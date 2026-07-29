/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone.satellite.entitlement;

import static android.telephony.CarrierConfigManager.SATELLITE_DATA_SUPPORT_ALL;
import static android.telephony.CarrierConfigManager.SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_DATA;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_VOICE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NETWORK_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVER_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_DATA_PLAN_METERED;
import static com.android.internal.telephony.satellite.SatelliteController.SATELLITE_DATA_PLAN_UNMETERED;
import static com.android.libraries.entitlement.ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS;
import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_DISABLED;
import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.ExponentialBackoff;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.LocaleTracker;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.domainselection.DomainSelectionResolver;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConstants;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.libraries.entitlement.ServiceEntitlementException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteEntitlementControllerTest extends TelephonyTestBase {
    private static final String TAG = "SatelliteEntitlementControllerTest";
    private static final int SUB_ID = 0;
    private static final int SUB_ID_2 = 1;
    private static final int[] ACTIVE_SUB_ID = {SUB_ID};
    private static final int DEFAULT_QUERY_REFRESH_DAY = 7;
    private static final List<String> PLMN_ALLOWED_LIST = Arrays.asList("31026", "302820");
    private static final List<String> PLMN_BARRED_LIST = Arrays.asList("12345", "98765");
    private static final Map<String, Integer> PLMN_DATA_PLAN_LIST = Map.of(
            "31026", SATELLITE_DATA_PLAN_METERED,
            "302820", SATELLITE_DATA_PLAN_UNMETERED);
    private static final List<String> EMPTY_PLMN_LIST = new ArrayList<>();
    private static final Map<String, Integer> EMPTY_PLMN_DATA_PLAN_LIST = new HashMap<>();
    private static final Map<String, List<Integer>> PLMN_ALLOWED_SERVICES_LIST = Map.of(
            "31026", List.of(SERVICE_TYPE_DATA),
            "302820", List.of(SERVICE_TYPE_DATA, SERVICE_TYPE_VOICE)
    );
    private static final Map<String, Integer> PLMN_DATA_SERVICE_POLICY_LIST = Map.of(
            "31026", SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED,
            "302820", SATELLITE_DATA_SUPPORT_ALL);
    private static final Map<String, Integer> PLMN_VOICE_SERVICE_POLICY_LIST = Map.of(
            "31026", SATELLITE_DATA_SUPPORT_ALL,
            "302820", SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED
    );
    private static final Map<String, List<Integer>> EMPTY_PLMN_ALLOWED_SERVICES_LIST =
            new HashMap<>();
    private static final Map<String, Integer> EMPTY_PLMN_DATA_SERVICE_POLICY_LIST =
            new HashMap<>();
    private static final Map<String, Integer> EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST =
            new HashMap<>();
    private static final int CMD_START_QUERY_ENTITLEMENT = 1;
    private static final int CMD_RETRY_QUERY_ENTITLEMENT = 2;
    private static final int CMD_SIM_REFRESH = 3;
    private static final int MAX_RETRY_COUNT = 5;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock ConnectivityManager mConnectivityManager;
    @Mock Network mNetwork;
    @Mock TelephonyManager mTelephonyManager;
    @Mock SubscriptionManagerService mMockSubscriptionManagerService;
    @Mock SatelliteEntitlementApi mSatelliteEntitlementApi;
    @Mock SatelliteEntitlementResult mSatelliteEntitlementResult;
    @Mock SatelliteController mSatelliteController;
    @Mock private FeatureFlags mMockFeatureFlags;
    @Mock private SatelliteConfig mMockSatelliteConfig;
    @Mock private IIntegerConsumer mCallback;
    @Mock private DomainSelectionResolver mMockDomainSelectionResolver;
    @Mock private ServiceStateTracker mMockSST;
    @Mock private LocaleTracker mMockLocaleTracker;

    private PersistableBundle mCarrierConfigBundle;
    private TestSatelliteEntitlementController mSatelliteEntitlementController;
    private Handler mHandler;
    private TestableLooper mTestableLooper;
    private List<Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener>>
            mCarrierConfigChangedListenerList;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // 1. Explicitly reset mocks to ensure no interactions persist from previous tests
        // (Even if JUnit creates new instances, this is a safety net for spied objects)
        reset(mMockSubscriptionManagerService, mSatelliteController, mSatelliteEntitlementApi,
                mMockFeatureFlags, mCallback, mMockDomainSelectionResolver);

        // 2. Initialize the listener list fresh for every test
        mCarrierConfigChangedListenerList = new ArrayList<>();

        // 3. Setup global static mocks
        replaceInstance(SubscriptionManagerService.class, "sInstance", null,
                mMockSubscriptionManagerService);
        replaceInstance(SatelliteController.class, "sInstance", null, mSatelliteController);
        replaceInstance(DomainSelectionResolver.class, "sInstance",
                null, mMockDomainSelectionResolver);

        // 4. Common Mock behaviors
        mTestableLooper = TestableLooper.get(this);
        mHandler = new Handler(mTestableLooper.getLooper());

        doReturn(Context.TELEPHONY_SERVICE).when(mContext).getSystemServiceName(
                TelephonyManager.class);
        doReturn(mTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());

        doReturn(Context.CARRIER_CONFIG_SERVICE).when(mContext).getSystemServiceName(
                CarrierConfigManager.class);
        doReturn(mCarrierConfigManager).when(mContext).getSystemService(
                Context.CARRIER_CONFIG_SERVICE);

        // Capture listeners reliably
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(0);
            CarrierConfigManager.CarrierConfigChangeListener listener = invocation.getArgument(1);
            mCarrierConfigChangedListenerList.add(new Pair<>(executor, listener));
            return null;
        }).when(mCarrierConfigManager).registerCarrierConfigChangeListener(
                any(Executor.class),
                any(CarrierConfigManager.CarrierConfigChangeListener.class));

        // Carrier Config Bundle Setup
        mCarrierConfigBundle = new PersistableBundle();
        mCarrierConfigBundle.putInt(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                DEFAULT_QUERY_REFRESH_DAY);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        doReturn(mCarrierConfigBundle)
                .when(mCarrierConfigManager).getConfigForSubId(anyInt(), any());

        // Connectivity Setup
        doReturn(Context.CONNECTIVITY_SERVICE).when(mContext).getSystemServiceName(
                ConnectivityManager.class);
        doReturn(mConnectivityManager).when(mContext).getSystemService(
                Context.CONNECTIVITY_SERVICE);
        doReturn(mNetwork).when(mConnectivityManager).getActiveNetwork();

        // Subscription Setup
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        doReturn(new int[]{SUB_ID}).when(mMockSubscriptionManagerService).getActiveSubIdList(true);

        // Phone/SST Setup
        doReturn(SUB_ID).when(mPhone).getSubId();
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(mMockSST).when(mPhone).getServiceStateTracker();
        doReturn(mMockLocaleTracker).when(mMockSST).getLocaleTracker();
        doReturn(true).when(mMockDomainSelectionResolver).isDomainSelectionSupported();

        // 5. Initialize Controller
        mSatelliteEntitlementController = spy(new TestSatelliteEntitlementController(mContext,
                mTestableLooper.getLooper(), mSatelliteEntitlementApi, mMockFeatureFlags));

        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        doReturn(true).when(mMockFeatureFlags).satelliteImproveMultiThreadDesign();
    }

    @After
    public void tearDown() throws Exception {
        // CRITICAL: Restore static instances to null to prevent pollution between tests
        replaceInstance(SubscriptionManagerService.class, "sInstance", null, null);
        replaceInstance(SatelliteController.class, "sInstance", null, null);
        replaceInstance(DomainSelectionResolver.class, "sInstance", null, null);

        if (mTestableLooper != null) {
            mTestableLooper.processAllMessages();
        }
        super.tearDown();
    }

    @Test
    public void testShouldStartQueryEntitlement() throws Exception {
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);

        // 1. Verify don't start query when Not Supported
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // Reset Support to TRUE
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);

        // 2. Verify don't start query when No Internet
        setInternetConnected(false);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // Reset Internet to TRUE
        setInternetConnected(true);

        // 3. Verify don't start query when Throttled (Time not expired)
        setLastQueryTime(System.currentTimeMillis());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // 4. Verify don't start query when In Progress
        ConcurrentHashMap<Integer, Boolean> inProgressMap = new ConcurrentHashMap<>();
        inProgressMap.put(SUB_ID, true);
        replaceInstance(SatelliteEntitlementController.class, "mIsEntitlementInProgressPerSub",
                mSatelliteEntitlementController, inProgressMap);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // Clear In Progress Flag
        replaceInstance(SatelliteEntitlementController.class, "mIsEntitlementInProgressPerSub",
                mSatelliteEntitlementController, new ConcurrentHashMap<>());
        // Clear Throttling (Set Last Query Time to 0)
        setLastQueryTime(0L);
        // Clear Retry Counts
        replaceInstance(SatelliteEntitlementController.class, "mRetryCountPerSub",
                mSatelliteEntitlementController, new ConcurrentHashMap<>());

        // 5. Verify Success Case
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);

        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());
    }

    @Test
    public void testRequestEntitlementRefresh_NotSupported() throws Exception {
        // [Setup] Ensure the device thinks it has an active internet connection.
        setInternetConnected(true);

        // [Setup] Simulate Carrier Configuration: Set the boolean flag
        // 'KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL' to false.
        // This simulates a carrier that does not support satellite entitlement.
        setSatelliteEntitlementSupported(false);

        // [Execution] Call the public API to request a refresh.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);

        // [Execution] Flush the Looper. The controller uses a Handler for this operation,
        // so we must process messages to execute the logic.
        mTestableLooper.processAllMessages();

        // [Verification] The Controller checks support *before* making network calls.
        // Therefore, the API should NEVER be triggered.
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // [Verification] The callback should immediately receive the NOT_SUPPORTED error code.
        verify(mCallback).accept(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
    }

    @Test
    public void testRequestEntitlementRefresh_InProgress() throws Exception {
        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);

        // [Setup] Manually inject internal state to simulate a query already running.
        // We use a ConcurrentHashMap to represent 'mIsEntitlementInProgressPerSub'.
        ConcurrentHashMap<Integer, Boolean> inProgressMap = new ConcurrentHashMap<>();
        inProgressMap.put(SUB_ID, true); // Mark SUB_ID as currently busy

        // Use reflection helper to inject this map into the private field.
        replaceInstance(SatelliteEntitlementController.class, "mIsEntitlementInProgressPerSub",
                mSatelliteEntitlementController, inProgressMap);

        // [Execution] Call the API while the "In Progress" flag is true.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] The controller should detect the busy state and abort.
        // No new API call should be made.
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // [Verification] Callback should receive the IN_PROGRESS error code.
        verify(mCallback).accept(SATELLITE_RESULT_REQUEST_IN_PROGRESS);
    }

    @Test
    public void testRequestEntitlementRefresh_NoInternet() throws Exception {
        // [Setup] Simulate no internet connectivity.
        // This usually mocks the ConnectivityManager or NetworkInfo.
        setInternetConnected(false);
        setSatelliteEntitlementSupported(true);

        // [Execution] Call the API.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] The Controller checks network availability early.
        // It should fail fast without hitting the server API.
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();

        // [Verification] Callback receives NETWORK_ERROR.
        verify(mCallback).accept(SATELLITE_RESULT_NETWORK_ERROR);
    }

    @Test
    public void testRequestEntitlementRefresh_Server500() throws Exception {
        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);

        // [Setup] Mock the API to throw a ServiceEntitlementException.
        // We simulate a 500 Internal Server Error.
        doAnswer(invocation -> {
            throw new ServiceEntitlementException(
                    ERROR_HTTP_STATUS_NOT_SUCCESS, 500, "Server Error", "");
        }).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // [Execution] Trigger the refresh.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] Unlike previous tests, the API *IS* called here.
        // The failure happens at the server layer, not the validation layer.
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();

        // [Verification] The Exception matches the SERVER_ERROR code.
        verify(mCallback).accept(SATELLITE_RESULT_SERVER_ERROR);
    }

    @Test
    public void testRequestEntitlementRefresh_Success_ClearsRetry_And_BypassesThrottling()
            throws Exception {
        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);

        // [Setup] Set a long refresh interval (7 days).
        // This ensures that if the throttling logic were active, a second call would fail.
        setSatelliteEntitlementStatusRefreshDays(7);

        // [Setup] Simulate a "Bad State": The user has failed 5 times previously.
        // We inject this into the private retry counter map.
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");
        retryCountPerSub.put(SUB_ID, MAX_RETRY_COUNT);

        // [Setup] Mock a successful API response (HTTP 200 equivalent).
        SatelliteEntitlementResult result = new SatelliteEntitlementResult(
                SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                new ArrayList<>(), new ArrayList<>());
        doReturn(result).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // --- PART 1: First Query (Happy Path & State Cleanup) ---

        // [Execution] Call the API for the first time.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] Crucial Requirement: A successful call must reset the retry counter to 0.
        // This ensures the exponential backoff logic is reset.
        assertEquals("Retry count should be cleared", 0,
                retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // [Verification] Confirm API was called once and callback succeeded.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        verify(mCallback, times(1)).accept(SATELLITE_RESULT_SUCCESS);

        // --- PART 2: Second Query (Throttling Bypass) ---

        // Note: The first query set the "LastQueryTime" to NOW.
        // Under legacy rules, a query immediately after would be blocked for 7 days.
        // We are testing that the new requestEntitlementRefresh API ignores this timer.

        // [Execution] Call the API a second time immediately.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] The API should be called a SECOND time.
        // Total invocations = 2. This proves the 7-day timer was ignored.
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        verify(mCallback, times(2)).accept(SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testRequestEntitlementRefresh_LegacyPathThrottling_Enforced() throws Exception {
        // [Context] This test acts as a control group.
        // It proves that the throttling logic actually works for the OLD method,
        // confirming that the bypass in the previous test is working correctly.

        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);
        setSatelliteEntitlementStatusRefreshDays(7);

        // [Setup] Mock success.
        SatelliteEntitlementResult result = new SatelliteEntitlementResult(
                SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                new ArrayList<>(), new ArrayList<>());
        doReturn(result).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // --- Step 1: Execute Legacy Periodic Query ---

        // [Execution] Send the internal CMD_START_QUERY_ENTITLEMENT message directly.
        // This represents the system doing a background check.
        mSatelliteEntitlementController
                .handleMessage(mHandler.obtainMessage(CMD_START_QUERY_ENTITLEMENT));
        mTestableLooper.processAllMessages();

        // [Verification] It ran once. LastQueryTime is now set to NOW.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();

        // --- Step 2: Attempt Immediate Legacy Retry ---

        // [Execution] Send the internal message again immediately.
        mSatelliteEntitlementController
                .handleMessage(mHandler.obtainMessage(CMD_START_QUERY_ENTITLEMENT));
        mTestableLooper.processAllMessages();

        // [Verification] Verify count REMAINS 1.
        // The second call was throttled/skipped because 7 days haven't passed.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
    }

    @Test
    public void testRequestEntitlementRefresh_Throttling_StateIsolation() throws Exception {
        // [Context] This test ensures that if a user manually forces a refresh (New API),
        // it correctly updates the timestamp used by the background system (Legacy API).

        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);
        setSatelliteEntitlementStatusRefreshDays(7);

        SatelliteEntitlementResult result = new SatelliteEntitlementResult(
                SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                new ArrayList<>(), new ArrayList<>());
        doReturn(result).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // --- Step 1: Force Refresh (New API) ---

        // [Execution] User manually requests refresh.
        // This succeeds and updates "LastQueryTime" to NOW.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] API called once.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();

        // --- Step 2: Attempt Legacy Query ---

        // [Execution] The system attempts a background check immediately after.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_FCM_TICKLE);
        mTestableLooper.processAllMessages();

        // [Verification] The background check should respect the manual refresh timestamp.
        // It sees that a refresh happened recently, so it does nothing.
        // API count remains 1.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
    }

    @Test
    public void testRequestEntitlementRefresh_RetryAfter_InterruptedByExplicitRefresh()
            throws Exception {
        setInternetConnected(true);
        setSatelliteEntitlementSupported(true);
        setSatelliteEntitlementStatusRefreshDays(1);

        // --- Step 1: Simulate a 503 Service Unavailable ---

        // [Setup] Mock the API to fail with a 503 error and a "Retry-After: 3600" header.
        doAnswer(invocation -> {
            throw new ServiceEntitlementException(
                    ERROR_HTTP_STATUS_NOT_SUCCESS, 503, "Retry-After", "3600");
        }).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // [Execution] Trigger initial background query.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        // [Verification] API was called and failed.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        // NOTE: Internally, the Controller has now scheduled a delayed message for 3600s later.

        // --- Step 2: User Forces Refresh (Interruption) ---

        // [Setup] The server comes back online. Reset mock to return Success.
        SatelliteEntitlementResult result = new SatelliteEntitlementResult(
                SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                new ArrayList<>(), new ArrayList<>());
        doReturn(result).when(mSatelliteEntitlementApi).checkEntitlementStatus();

        // [Execution] User manually refreshes *before* the 3600s timer expires.
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] The manual refresh succeeds.
        // Total API calls = 1.
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        verify(mCallback).accept(SATELLITE_RESULT_REQUEST_IN_PROGRESS);

        // --- Step 3: The Original Retry Timer Fires ---

        // [Execution] We simulate time passing. The 3600s timer (from Step 1) now fires.
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(3601));
        mTestableLooper.processAllMessages();

        // [Verification] The retry logic checks "shouldRefreshEntitlementStatus".
        // Because Step 2 just succeeded, the LastQueryTime is very fresh.
        // The retry determines it is no longer needed and aborts.
        // Total API calls remains 2.
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
    }

    @Test
    public void testRequestEntitlementRefresh_InvalidSubId() throws Exception {
        setInternetConnected(true);
        final int invalidSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        // [Setup] Create a specific CarrierConfig bundle for the INVALID_SUBSCRIPTION_ID.
        // We explicitly set the support flag to false for this specific bundle.
        // If we don't do this, the default mock might return 'true', leading to a false positive.
        PersistableBundle notSupportedBundle = new PersistableBundle();
        notSupportedBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);

        // [Setup] Configure the Mock to return this specific bundle only when queried
        // with the invalidSubId.
        doReturn(notSupportedBundle).when(mCarrierConfigManager)
                .getConfigForSubId(eq(invalidSubId), any());

        // [Execution] Attempt to refresh with an invalid subscription ID.
        mSatelliteEntitlementController.requestEntitlementRefresh(invalidSubId, mCallback);
        mTestableLooper.processAllMessages();

        // [Verification] An invalid SubID cannot support the feature.
        // Expect NOT_SUPPORTED error.
        verify(mCallback).accept(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);

        // [Verification] Ensure no network traffic occurred.
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
    }

    @Test
    public void testRequestEntitlementRefresh_ResetsRetryCount_After503Failure() throws Exception {
        setIsQueryAvailableTrue();

        // 1. Mock 503 Failure (Persistent)
        when(mSatelliteEntitlementApi.checkEntitlementStatus()).thenAnswer(
                invocation -> {
                    throw new ServiceEntitlementException(
                            ERROR_HTTP_STATUS_NOT_SUCCESS, 503, "Service Unavailable", "");
                }
        );

        // 2. Trigger Initial Query
        mSatelliteEntitlementController.handleMessage(
                mHandler.obtainMessage(CMD_START_QUERY_ENTITLEMENT));
        mTestableLooper.processAllMessages();

        // The implementation of handleCmdStartQueryEntitlementForSubId returns early on error
        // without clearing the 'In Progress' flag. We must clear it manually here to allow
        // the retry logic to proceed for this test.
        ConcurrentHashMap<Integer, Boolean> inProgressMap =
                (ConcurrentHashMap<Integer, Boolean>) getValue("mIsEntitlementInProgressPerSub");
        inProgressMap.remove(SUB_ID);

        // 3. Force Retry Execution
        // Advance time to trigger the exponential backoff retry
        mTestableLooper.moveTimeForward(TimeUnit.MINUTES.toMillis(10));
        mTestableLooper.processAllMessages();

        // 4. Verify Retry Count INCREASED
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");
        int countAfterRetry = retryCountPerSub.getOrDefault(SUB_ID, 0);

        // Assert that the system actually retried (count > 0)
        assertTrue("Retry count should be > 0 after retry executed. Actual: " + countAfterRetry,
                countAfterRetry > 0);

        // 5. Setup: Prepare for Success on explicit request
        doReturn(mSatelliteEntitlementResult).when(mSatelliteEntitlementApi)
                .checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);

        // 6. Trigger the New API
        IIntegerConsumer callback = mock(IIntegerConsumer.class);
        mSatelliteEntitlementController.requestEntitlementRefresh(SUB_ID, callback);
        mTestableLooper.processAllMessages();

        // 7. Verify Retry Count RESET
        assertEquals("Retry count should be reset to 0 after explicit refresh",
                0, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        verify(callback).accept(SATELLITE_RESULT_SUCCESS);
    }

    @Test
    public void testCheckSatelliteEntitlementStatus() throws Exception {
        setIsQueryAvailableTrue();
        // Verify don't call the checkSatelliteEntitlementStatus when getActiveSubIdList is empty.
        doReturn(new int[]{}).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        // Verify don't call the updateSatelliteEntitlementStatus.
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify call the checkSatelliteEntitlementStatus with invalid response.
        setIsQueryAvailableTrue();
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        replaceInstance(SatelliteEntitlementController.class,
                "mSatelliteEntitlementResultPerSub", mSatelliteEntitlementController,
                new ConcurrentHashMap<>());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is disabled
        // , empty PLMNAllowed and empty PLMNBarred.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID),
                eq(false), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST), eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST),
                any());

        // Verify call the checkSatelliteEntitlementStatus with the subscribed result.
        clearInvocationsForMock();
        setIsQueryAvailableTrue();
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is enable,
        // availablePLMNAllowedList and availablePLMNBarredList.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());

        // Change subId and verify call the updateSatelliteEntitlementStatus with satellite
        // service is enable, availablePLMNAllowedList and availablePLMNBarredList
        clearInvocationsForMock();
        doReturn(new int[]{SUB_ID_2}).when(mMockSubscriptionManagerService).getActiveSubIdList(
                true);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID_2), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());

        // Verify call the updateSatelliteEntitlementStatus with satellite service is enable,
        // availablePLMNAllowedList and empty plmn barred list.
        clearInvocationsForMock();
        setIsQueryAvailableTrue();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_DATA_PLAN_LIST),
                eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST), eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST),
                eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST), any());

        // Verify call the updateSatelliteEntitlementStatus with satellite service is enable,
        // empty PLMNAllowedList and PLMNBarredList.
        clearInvocationsForMock();
        setIsQueryAvailableTrue();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_DATA_PLAN_LIST),
                eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST), eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST),
                eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST), any());

        // Verify call the updateSatelliteEntitlementStatus with satellite service is enable,
        // empty PLMNAllowedList and availablePLMNBarredList.
        clearInvocationsForMock();
        setIsQueryAvailableTrue();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, new ArrayList<>(),
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(EMPTY_PLMN_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testCheckSatelliteEntitlementStatusWhenInternetConnected() throws Exception {
        ConnectivityManager.NetworkCallback networkCallback =
                (ConnectivityManager.NetworkCallback) getValue("mNetworkCallback");
        Network mockNetwork = mock(Network.class);

        // Verify the called the checkSatelliteEntitlementStatus when Internet is connected.
        setInternetConnected(true);
        setLastQueryTime(0L);
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);

        networkCallback.onAvailable(mockNetwork);
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is available.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testCheckSatelliteEntitlementStatusWhenCarrierConfigChanged() throws Exception {
        // Verify the called the checkSatelliteEntitlementStatus when CarrierConfigChanged
        // occurred and Internet is connected.
        setInternetConnected(true);
        setLastQueryTime(0L);
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        triggerCarrierConfigChanged();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is available.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testCheckWhenStartCmdIsReceivedDuringRetry() throws Exception {
        // Verify that start cmd RESETS the retry count,
        // even if the query is deferred due to "In Progress" state.
        setIsQueryAvailableTrue();
        set503RetryAfterResponse();
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");

        // 1. First Query
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        assertEquals(0, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // 2. Second Query (Retry #1 from First Query)
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.get(SUB_ID).longValue());

        // 3. Third Query (Retry #2 from First Query)
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(3)).checkEntitlementStatus();
        assertEquals(2, retryCountPerSub.get(SUB_ID).longValue());

        // 4. Start CMD Received -> Should RESET retry count but abort due to "In Progress"
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        // VERIFICATION: No new query (still 3), but retry count cleared to 0.
        verify(mSatelliteEntitlementApi, times(3)).checkEntitlementStatus();
        assertEquals(0, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // 5. Verify the NEW retry loop continues from here (Retry #1 of New Loop)
        // The previous failure (step 3) scheduled a retry in 1 second.
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();

        // Now it runs (Time 4)
        verify(mSatelliteEntitlementApi, times(4)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.get(SUB_ID).longValue());
    }

    @Test
    public void testCheckAfterInternetConnectionChangedDuringRetry() throws Exception {
        // Verify that the retry count is maintained even when internet connection is lost and
        // connected during retries, and that up to 5 retries are performed.
        setIsQueryAvailableTrue();
        set503RetryAfterResponse();
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");

        // Verify that the first query.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        // Verify that the retry count is 0 after receiving a 503 with retry-after header in
        // response.
        assertEquals(0, retryCountPerSub.getOrDefault(SUB_ID, 0).longValue());

        // Verify that the retry count is 1 for the second query when receiving a 503 with
        // retry-after header in response.
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // Verify that no query is executed and the retry count does not increase when internet
        // connection is lost during the second retry.
        setInternetConnected(false);
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(2));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // Verify that the query is started when internet connection is restored and that the
        // retry count does not increase.
        setInternetConnected(true);
        Log.d(TAG, "internet connected again");
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_INTERNET_CONNECTED);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(3)).checkEntitlementStatus();
        assertEquals(0, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        // Verify that the retry count is increases after received a 503 with retry-after header
        // in response.
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(4)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(5)).checkEntitlementStatus();
        assertEquals(2, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(6)).checkEntitlementStatus();
        assertEquals(3, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(7)).checkEntitlementStatus();
        assertEquals(4, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());

        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(8)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));

        // Verify that the query is not restarted after reaching the maximum retry count even if
        // a start cmd is received.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(8)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));

        // Verify that the query is not restarted after reaching the maximum retry count even if
        // a retry cmd is received.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(8)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));

        // Verify only called onSatelliteEntitlementStatusUpdated once.
        verify(mSatelliteController, times(1)).onSatelliteEntitlementStatusUpdated(eq(SUB_ID),
                eq(false), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST), eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST),
                any());
    }

    @Test
    public void testStartQueryEntitlementStatus_error500() throws Exception {
        setIsQueryAvailableTrue();
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");
        setErrorResponse(500);

        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));
        verify(mSatelliteController, times(1)).onSatelliteEntitlementStatusUpdated(eq(SUB_ID),
                eq(false), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST),
                eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_error503_retrySuccess() throws Exception {
        setIsQueryAvailableTrue();
        set503RetryAfterResponse();
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");

        // Verify that the first query.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));

        // Verify whether the query has been retried and verify called
        // onSatelliteEntitlementStatusUpdated after receive a success case.
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_otherError_retrySuccess() throws Exception {
        setIsQueryAvailableTrue();
        ConcurrentHashMap<Integer, Integer> retryCountPerSub =
                (ConcurrentHashMap<Integer, Integer>) getValue("mRetryCountPerSub");
        ConcurrentHashMap<Integer, Boolean> isEntitlementInProgressPerSub =
                (ConcurrentHashMap<Integer, Boolean>) getValue("mIsEntitlementInProgressPerSub");
        ConcurrentHashMap<Integer, ExponentialBackoff> exponentialBackoffPerSub =
                (ConcurrentHashMap<Integer, ExponentialBackoff>) getValue(
                        "mExponentialBackoffPerSub");
        setErrorResponse(400);

        // Verify start the exponentialBackoff.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(1)).checkEntitlementStatus();
        assertNull(retryCountPerSub.get(SUB_ID));
        assertTrue(isEntitlementInProgressPerSub.getOrDefault(SUB_ID, false));
        assertNotNull(exponentialBackoffPerSub.get(SUB_ID));
        // Verify don't call the onSatelliteEntitlementStatusUpdated.
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify the retry in progress.
        sendMessage(CMD_RETRY_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());
        // Verify don't call the onSatelliteEntitlementStatusUpdated.
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Received the 200 response, Verify call the onSatelliteEntitlementStatusUpdated.
        setIsQueryAvailableTrue();
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);

        sendMessage(CMD_RETRY_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi, times(3)).checkEntitlementStatus();
        assertEquals(1, retryCountPerSub.getOrDefault(SUB_ID, 0).intValue());
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testSatelliteEntitlementSupportedChangedFromSupportToNotSupport() throws Exception {
        setIsQueryAvailableTrue();

        // KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL changed from Support(entitlement status
        // disabled) to not support.
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_DISABLED, EMPTY_PLMN_LIST,
                EMPTY_PLMN_LIST, EMPTY_PLMN_DATA_PLAN_LIST, EMPTY_PLMN_ALLOWED_SERVICES_LIST,
                EMPTY_PLMN_DATA_SERVICE_POLICY_LIST, EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        // Verify call the onSatelliteEntitlementStatusUpdated - entitlement status false
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                eq(false), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST), eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST),
                any());

        // Verify call the onSatelliteEntitlementStatusUpdated - entitlement status true
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                eq(true), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST), eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST),
                any());

        // KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL changed from Support(entitlement status
        // enabled) to not support.
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_CARRIER_CONFIG_CHANGED);
        mTestableLooper.processAllMessages();

        // Verify call the onSatelliteEntitlementStatusUpdated - entitlement status true.
        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                eq(true), eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST),
                eq(PLMN_DATA_PLAN_LIST), eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());

        // Verify not call the onSatelliteEntitlementStatusUpdated.
        clearInvocationsForMock();
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                eq(true), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(PLMN_DATA_PLAN_LIST), eq(PLMN_ALLOWED_SERVICES_LIST),
                eq(PLMN_DATA_SERVICE_POLICY_LIST), eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_refreshStatus() throws Exception {
        setIsQueryAvailableTrue();
        mCarrierConfigBundle.putInt(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT, 1);

        // Verify start query and success.
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // After move to the refresh time, verify the query started and success.
        setLastQueryTime(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1) - 1000);
        mTestableLooper.moveTimeForward(TimeUnit.DAYS.toMillis(1));
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        verify(mSatelliteController, times(2)).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_internetDisconnectedAndConnectedAgain()
            throws Exception {
        setIsQueryAvailableTrue();

        // Verify the query does not start if there is no internet connection.
        setInternetConnected(false);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify the query start and success after internet connected.
        setInternetConnected(true);
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_INTERNET_CONNECTED);
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_error503_error500() throws Exception {
        setIsQueryAvailableTrue();
        set503RetryAfterResponse();

        // Verify that the first query was triggered and that onSatelliteEntitlementStatusUpdated
        // was not called after received a 503 error.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify whether the second query has been triggered and whether
        // onSatelliteEntitlementStatusUpdated has been called after received the 500 error.
        reset(mSatelliteEntitlementApi);
        setErrorResponse(500);
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID),
                eq(false), eq(EMPTY_PLMN_LIST), eq(EMPTY_PLMN_LIST),
                eq(EMPTY_PLMN_DATA_PLAN_LIST), eq(EMPTY_PLMN_ALLOWED_SERVICES_LIST),
                eq(EMPTY_PLMN_DATA_SERVICE_POLICY_LIST), eq(EMPTY_PLMN_VOICE_SERVICE_POLICY_LIST),
                any());
    }

    @Test
    public void testStartQueryEntitlementStatus_error503_otherError() throws Exception {
        setIsQueryAvailableTrue();
        set503RetryAfterResponse();

        // Verify that the first query was triggered and that onSatelliteEntitlementStatusUpdated
        // was not called after received a 503 error.
        sendMessage(CMD_START_QUERY_ENTITLEMENT, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify whether the second query was triggered and onSatelliteEntitlementStatusUpdated
        // was not called after received a 503 error without valid retry-after header.
        reset(mSatelliteEntitlementApi);
        setErrorResponse(503);
        mTestableLooper.moveTimeForward(TimeUnit.SECONDS.toMillis(1));
        mTestableLooper.processAllMessages();
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // Verify whether the third query was triggered and onSatelliteEntitlementStatusUpdated
        // was called after received a success case.
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        mTestableLooper.moveTimeForward(TimeUnit.MINUTES.toMillis(10));
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), eq(PLMN_BARRED_LIST), eq(PLMN_DATA_PLAN_LIST),
                eq(PLMN_ALLOWED_SERVICES_LIST), eq(PLMN_DATA_SERVICE_POLICY_LIST),
                eq(PLMN_VOICE_SERVICE_POLICY_LIST), any());
    }

    @Test
    public void testStartQueryEntitlementStatus_AfterSimRefresh() throws Exception {
        setIsQueryAvailableTrue();

        // Verify the first query complete.
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST,
                PLMN_BARRED_LIST, PLMN_DATA_PLAN_LIST, PLMN_ALLOWED_SERVICES_LIST,
                PLMN_DATA_SERVICE_POLICY_LIST, PLMN_VOICE_SERVICE_POLICY_LIST);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), anyList(), anyMap(), anyMap(), anyMap(), anyMap(), any());

        // SIM_REFRESH event occurred before expired the query refresh timer, verify the start
        // the query.
        sendMessage(CMD_SIM_REFRESH, SUB_ID,
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_SIM_REFRESH);
        mTestableLooper.moveTimeForward(TimeUnit.MINUTES.toMillis(10));
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi, times(2)).checkEntitlementStatus();
        verify(mSatelliteController, times(2))
                .onSatelliteEntitlementStatusUpdated(anyInt(), anyBoolean(), anyList(), anyList(),
                        anyMap(), anyMap(), anyMap(), anyMap(), any());
    }

    private void triggerCarrierConfigChanged() {
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        mTestableLooper.processAllMessages();
    }

    private void triggerCarrierConfigChanged(int subId) {
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ subId, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        mTestableLooper.processAllMessages();
    }

    private void clearInvocationsForMock() {
        clearInvocations(mSatelliteEntitlementApi);
        clearInvocations(mSatelliteController);
    }

    private void setIsQueryAvailableTrue() throws Exception {
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        replaceInstance(SatelliteEntitlementController.class, "mRetryCountPerSub",
                mSatelliteEntitlementController, new ConcurrentHashMap<>());
        replaceInstance(SatelliteEntitlementController.class, "mIsEntitlementInProgressPerSub",
                mSatelliteEntitlementController, new ConcurrentHashMap<>());
        setInternetConnected(true);
        setLastQueryTime(0L);
        replaceInstance(SatelliteEntitlementController.class,
                "mSatelliteEntitlementResultPerSub", mSatelliteEntitlementController,
                new ConcurrentHashMap<>());
        replaceInstance(SatelliteEntitlementController.class,
                "mSubIdPerSlot", mSatelliteEntitlementController, new ConcurrentHashMap<>());
    }

    @Test
    public void testShouldStartQueryEntitlement_withSatelliteConfig() throws Exception {
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        doReturn(true).when(mMockFeatureFlags).configForEnablingCarrier();

        SatelliteConfig mockSatelliteConfig = mock(SatelliteConfig.class);
        doReturn(mockSatelliteConfig).when(mSatelliteController).getSatelliteConfig();
        doReturn(1).when(mTelephonyManager).getSimCarrierId();

        // 1. Verify entitlement supported from SatelliteConfig
        doReturn(true).when(mockSatelliteConfig).isSatelliteEntitlementSupportedBySubId(anyInt());
        // Set CarrierConfig to false to ensure we are using SatelliteConfig
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);

        setInternetConnected(true);
        setLastQueryTime(0L);

        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi).checkEntitlementStatus();

        // 2. Verify entitlement NOT supported from SatelliteConfig
        clearInvocations(mSatelliteEntitlementApi);
        doReturn(false).when(mockSatelliteConfig).isSatelliteEntitlementSupportedBySubId(anyInt());

        mSatelliteEntitlementController.handleCmdStartQueryEntitlement(
                SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_UNKNOWN);
        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
    }

    @Test
    public void testRegisterForConfigUpdateChanged() {
        SatelliteConfig mockSatelliteConfig = mock(SatelliteConfig.class);
        doReturn(true).when(mockSatelliteConfig).isSatelliteEntitlementSupportedBySubId(anyInt());
        // Reset the singleton mock to capture the registration call in constructor
        reset(mSatelliteController);

        SatelliteEntitlementController testController = new SatelliteEntitlementController(
                mContext, mTestableLooper.getLooper(), mMockFeatureFlags);

        verify(mSatelliteController).registerForConfigUpdateChanged(
                eq(testController), eq(6 /* CMD_UPDATE_CONFIG_DATA */), any());
    }

    @Test
    public void testReceiveConfigUpdateMessage() throws Exception {
        // Prepare mock environment
        doReturn(true).when(mMockFeatureFlags).configForEnablingCarrier();
        replaceInstance(SatelliteEntitlementController.class, "mSubscriptionManagerService",
                mSatelliteEntitlementController, mMockSubscriptionManagerService);
        doReturn(new int[]{SUB_ID}).when(mMockSubscriptionManagerService).getActiveSubIdList(true);

        // Satisfy isEntitlementItemExistOnSatelliteConfig() condition
        SatelliteConfig mockSatelliteConfig = mock(SatelliteConfig.class);
        doReturn(mockSatelliteConfig).when(mSatelliteController).getSatelliteConfig();
        doReturn(true).when(mockSatelliteConfig).isSatelliteEntitlementSupportedBySubId(anyInt());

        // 1. Send the message CMD_UPDATE_CONFIG_DATA (6)
        Message msgUpdate = mSatelliteEntitlementController.obtainMessage(6);
        mSatelliteEntitlementController.handleMessage(msgUpdate);

        // 2. Manually trigger the resulting entitlement query to avoid async looper issues
        // In handleCmdUpdateConfigData, it sends CMD_START_QUERY_ENTITLEMENT (1)
        Message msgQuery = mSatelliteEntitlementController.obtainMessage(1);
        msgQuery.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_CONFIG_UPDATED;
        mSatelliteEntitlementController.handleMessage(msgQuery);

        // Verify that the controller eventually accessed the sub list during entitlement query
        verify(mMockSubscriptionManagerService, atLeastOnce()).getActiveSubIdList(true);
    }

    private void setInternetConnected(boolean connected) {
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder().build();

        if (connected) {
            networkCapabilities = new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setTransportInfo(mock(WifiInfo.class))
                    .build();
        }
        doReturn(networkCapabilities).when(mConnectivityManager).getNetworkCapabilities(mNetwork);
    }

    private void setSatelliteEntitlementSupported(boolean supported) {
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, supported);
    }

    private void setSatelliteEntitlementStatusRefreshDays(int days) {
        mCarrierConfigBundle.putInt(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT, days);
    }

    private void setSatelliteEntitlementResult(int entitlementStatus,
            List<String> plmnAllowedList, List<String> plmnBarredList,
            Map<String,Integer> plmnDataPlanMap,
            Map<String,List<Integer>> plmnAllowedServicesMap,
            Map<String,Integer> plmnDataServicePolicyMap,
            Map<String,Integer> plmnVoiceServicePolicyMap) {
        doReturn(entitlementStatus).when(mSatelliteEntitlementResult).getEntitlementStatus();
        doReturn(plmnAllowedList).when(mSatelliteEntitlementResult).getAllowedPLMNList();
        doReturn(plmnBarredList).when(mSatelliteEntitlementResult).getBarredPLMNList();
        doReturn(plmnDataPlanMap).when(mSatelliteEntitlementResult).getDataPlanInfoForPlmnList();
        doReturn(plmnAllowedServicesMap).when(mSatelliteEntitlementResult)
                .getAvailableServiceTypeInfoForPlmnList();
        doReturn(plmnDataServicePolicyMap).when(mSatelliteEntitlementResult)
                .getDataServicePolicyInfoForPlmnList();
        doReturn(plmnVoiceServicePolicyMap).when(mSatelliteEntitlementResult)
                .getVoiceServicePolicyInfoForPlmnList();
    }

    private void setLastQueryTime(Long lastQueryTime) throws Exception {
        ConcurrentHashMap<Integer, Long> lastQueryTimePerSub = new ConcurrentHashMap<>();
        replaceInstance(SatelliteEntitlementController.class, "mLastQueryTimePerSub",
                mSatelliteEntitlementController, lastQueryTimePerSub);
        lastQueryTimePerSub.put(SUB_ID, lastQueryTime);
    }

    private void set503RetryAfterResponse() throws Exception {
        when(mSatelliteEntitlementApi.checkEntitlementStatus()).thenAnswer(
                new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        throw new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS, 503, "1", "503 occurred");
                    }
                }
        );
    }

    private void setErrorResponse(int errorCode) throws Exception {
        when(mSatelliteEntitlementApi.checkEntitlementStatus()).thenAnswer(
                new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        throw new ServiceEntitlementException(
                                ERROR_HTTP_STATUS_NOT_SUCCESS, errorCode, "",
                                errorCode + " occurred");
                    }
                }
        );
    }

    private void sendMessage(int what, int subId, int triggerEvent) {
        mSatelliteEntitlementController.handleMessage(
                mHandler.obtainMessage(what, subId, triggerEvent));
    }

    private Object getValue(String originalObjectName) throws Exception {
        Field field = SatelliteEntitlementController.class.getDeclaredField(originalObjectName);
        field.setAccessible(true);
        return field.get(mSatelliteEntitlementController);
    }

    public static class TestSatelliteEntitlementController extends SatelliteEntitlementController {
        private SatelliteEntitlementApi mInjectSatelliteEntitlementApi;

        TestSatelliteEntitlementController(@NonNull Context context, @NonNull Looper looper,
                SatelliteEntitlementApi api, FeatureFlags featureFlags) {
            super(context, looper, featureFlags);
            mInjectSatelliteEntitlementApi = api;
        }

        @Override
        public SatelliteEntitlementApi getSatelliteEntitlementApi(int subId) {
            Log.d(TAG, "getSatelliteEntitlementApi");
            return mInjectSatelliteEntitlementApi;
        }

        @Override
        protected void handleCmdStartQueryEntitlement(int triggerEvent) {
            super.handleCmdStartQueryEntitlement(triggerEvent);
        }

        @Override
        public void handleCmdStartQueryEntitlementForSubId(int subId,
                boolean shouldEnforceTimeout, @Nullable IIntegerConsumer callback,
                int triggerEvent) {
            super.handleCmdStartQueryEntitlementForSubId(subId, shouldEnforceTimeout,
                    callback, triggerEvent);
        }

        @Override
        public boolean isSatelliteEntitlementSupported(int subId) {
            return super.isSatelliteEntitlementSupported(subId);
        }

        @Override
        public void resetSatelliteEntitlementRestrictedReason(int subId) {
            super.resetSatelliteEntitlementRestrictedReason(subId);
        }
    }
}
