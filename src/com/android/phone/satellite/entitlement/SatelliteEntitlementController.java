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

import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NETWORK_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SERVER_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.telephony.ExponentialBackoff;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConstants;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.satellite.metrics.EntitlementMetricsStats;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.libraries.entitlement.ServiceEntitlementException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class query the entitlement server to receive values for satellite services and passes the
 * response to the {@link com.android.internal.telephony.satellite.SatelliteController}.
 *
 * @hide
 */
public class SatelliteEntitlementController extends Handler {
    private static final String TAG = "SatelliteEntitlementController";
    @NonNull private static SatelliteEntitlementController sInstance;

    /** Message code used in handleMessage() */
    private static final int CMD_START_QUERY_ENTITLEMENT = 1;

    private static final int CMD_RETRY_QUERY_ENTITLEMENT = 2;
    private static final int CMD_SIM_REFRESH = 3;
    private static final int AIRPLANE_MODE_CHANGED = 4;

    private static final int CMD_START_QUERY_ENTITLEMENT_FOR_SUB_ID = 5;
    private static final int CMD_UPDATE_CONFIG_DATA = 6;

    private static final boolean IS_DEBUG_BUILD = !"user".equals(Build.TYPE);

    /** Retry on next trigger event. */
    private static final int HTTP_RESPONSE_500 = 500;

    /**
     * Retry after the time specified in the “Retry-After” header. After retry count doesn't exceed
     * MAX_RETRY_COUNT.
     */
    private static final int HTTP_RESPONSE_503 = 503;

    /** Default query refresh time is 1 month. */
    private static final int DEFAULT_QUERY_REFRESH_DAYS = 7;

    private static final long INITIAL_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(10); // 10 min
    private static final long MAX_DELAY_MILLIS = TimeUnit.DAYS.toMillis(5); // 5 days
    private static final int MULTIPLIER = 2;
    private static final int MAX_RETRY_COUNT = 5;

    @NonNull private final SubscriptionManagerService mSubscriptionManagerService;
    @NonNull private final CarrierConfigManager mCarrierConfigManager;

    @NonNull
    private final CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

    @NonNull private final ConnectivityManager mConnectivityManager;
    @NonNull private final ConnectivityManager.NetworkCallback mNetworkCallback;
    @NonNull private final BroadcastReceiver mReceiver;
    @NonNull private final Context mContext;

    /** Map key : subId, value : ExponentialBackoff. */
    private ConcurrentHashMap<Integer, ExponentialBackoff> mExponentialBackoffPerSub =
            new ConcurrentHashMap<>();

    /** Map key : subId, value : SatelliteEntitlementResult. */
    private ConcurrentHashMap<Integer, SatelliteEntitlementResult>
            mSatelliteEntitlementResultPerSub = new ConcurrentHashMap<>();

    /** Map key : subId, value : the last query time to millis. */
    private ConcurrentHashMap<Integer, Long> mLastQueryTimePerSub = new ConcurrentHashMap<>();

    /**
     * Map key : subId, value : Count the number of retries caused by the 'ExponentialBackoff' and
     * '503 error case with the Retry-After header'.
     */
    private ConcurrentHashMap<Integer, Integer> mRetryCountPerSub = new ConcurrentHashMap<>();

    /** Map key : subId, value : Whether query is in progress. */
    private ConcurrentHashMap<Integer, Boolean> mIsEntitlementInProgressPerSub =
            new ConcurrentHashMap<>();

    /** Map key : slotId, value : The last used subId. */
    private ConcurrentHashMap<Integer, Integer> mSubIdPerSlot = new ConcurrentHashMap<>();

    @NonNull private final EntitlementMetricsStats mEntitlementMetricsStats;

    /** Feature flags to control behavior and errors. */
    @NonNull private final FeatureFlags mFeatureFlags;

    private AtomicBoolean mShouldIgnoreInternetConnectionStateForCtsTest = new AtomicBoolean(false);
    private AtomicBoolean mShouldIgnoreRefreshConditionForCtsTest = new AtomicBoolean(false);
    private String mOverriddenEntilementStatusResponseForCtsTest = "";
    private AtomicBoolean mShouldThrowExceptionForCtsTest = new AtomicBoolean(false);

    /**
     * Create the SatelliteEntitlementController singleton instance.
     *
     * @param context The Context to use to create the SatelliteEntitlementController.
     * @param featureFlags The feature flag.
     * @return The SatelliteEntitlementController singleton instance.
     */
    public static SatelliteEntitlementController make(
            @NonNull Context context, @NonNull FeatureFlags featureFlags) {
        if (sInstance == null) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            sInstance =
                    new SatelliteEntitlementController(
                            context, handlerThread.getLooper(), featureFlags);
        }
        return sInstance;
    }

    /**
     * Create a SatelliteEntitlementController to request query to the entitlement server for
     * satellite services and receive responses.
     *
     * @param context The Context for the SatelliteEntitlementController.
     * @param looper The looper for the handler. It does not run on main thread.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public SatelliteEntitlementController(
            @NonNull Context context, @NonNull Looper looper, @NonNull FeatureFlags featureFlags) {
        super(looper);
        mContext = context;
        mFeatureFlags = featureFlags;
        mSubscriptionManagerService = SubscriptionManagerService.getInstance();
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mCarrierConfigChangeListener =
                (slotIndex, subId, carrierId, specificCarrierId) ->
                        handleCarrierConfigChanged(slotIndex, subId, carrierId, specificCarrierId);
        if (mCarrierConfigManager != null) {
            mCarrierConfigManager.registerCarrierConfigChangeListener(
                    this::post, mCarrierConfigChangeListener);
        }
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mNetworkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        handleInternetConnected();
                    }
                };
        NetworkRequest networkrequest =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
        mConnectivityManager.registerNetworkCallback(networkrequest, mNetworkCallback, this);
        mReceiver = new SatelliteEntitlementControllerReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
        mEntitlementMetricsStats = EntitlementMetricsStats.getOrCreateInstance();
        SatelliteController.getInstance().registerIccRefresh(this, CMD_SIM_REFRESH);
        SatelliteController.getInstance()
                .registerForConfigUpdateChanged(this, CMD_UPDATE_CONFIG_DATA, null);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CMD_START_QUERY_ENTITLEMENT:
                handleCmdStartQueryEntitlement(msg.arg2);
                break;
            case CMD_RETRY_QUERY_ENTITLEMENT:
                handleCmdRetryQueryEntitlement(msg.arg1, msg.arg2);
                break;
            case CMD_SIM_REFRESH:
                handleSimRefresh();
                break;
            case AIRPLANE_MODE_CHANGED:
                logd("AIRPLANE_MODE_CHANGED");
                boolean airplaneMode = (boolean) msg.obj;
                if (!airplaneMode) {
                    resetEntitlementQueryCounts(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                }
                break;
            case CMD_START_QUERY_ENTITLEMENT_FOR_SUB_ID:
                logd("CMD_START_QUERY_ENTITLEMENT_FOR_SUB_ID");
                final SomeArgs args = (SomeArgs) msg.obj;
                try {
                    final int subId = (int) args.arg1;
                    final boolean ignoreApiThrottle = (boolean) args.arg2;
                    final IIntegerConsumer callback = (IIntegerConsumer) args.arg3;
                    final int triggerEvent = (int) args.arg4;
                    logd("handleMessage: subId = " + subId);
                    handleCmdStartQueryEntitlementForSubId(subId, ignoreApiThrottle,
                            callback, triggerEvent);
                } finally {
                    args.recycle();
                }
                break;
            case CMD_UPDATE_CONFIG_DATA:
                logd("CMD_UPDATE_CONFIG_DATA");
                handleCmdUpdateConfigData();
                break;
            default:
                logd("do not used this message");
        }
    }

    /**
     * Handles the configuration update event.
     * When the satellite configuration is updated, this method checks if the entitlement
     * related settings have changed and triggers a new query if necessary.
     */
    private void handleCmdUpdateConfigData() {
        logd("handleCmdUpdateConfigData: sending CMD_START_QUERY_ENTITLEMENT");
        if (isEntitlementItemExistOnSatelliteConfig()) {
            Message message = obtainMessage();
            message.what = CMD_START_QUERY_ENTITLEMENT;
            message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_CONFIG_UPDATED;
            sendMessage(message);
        }
    }

    /**
     * This API can be used by only CTS to set the ignore internet connection check.
     *
     * @param overriddenEntilementStatusResponse the overridden entitlement status response.
     * @param throwException whether to throw exception when receiving a request for entitlement
     *     status.
     * @return {@code true} if the setting is successful, {@code false} otherwise.
     */
    public boolean overrideEntilementStatusResponseForCtsTest(
            String overriddenEntilementStatusResponse, boolean throwException) {
        if (!isDebugBuild()) {
            logd("overrideEntilementStatusResponseForCtsTest: "
                    + "not allowed for non-debug build.");
            return false;
        }

        logd(
                "overrideEntilementStatusResponseForCtsTest: overriddenEntilementStatusResponse="
                        + overriddenEntilementStatusResponse);
        mOverriddenEntilementStatusResponseForCtsTest = overriddenEntilementStatusResponse;
        mShouldThrowExceptionForCtsTest.set(throwException);
        return true;
    }

    /**
     * This API can be used by only CTS to override the entitlement query conditions.
     *
     * @param ignoreInternetConnection whether to ignore the internet connection check.
     * @param ignoreRefreshCondition whether to ignore the refresh condition.
     * @return {@code true} if the setting is successful, {@code false} otherwise.
     */
    public boolean overrideEntilementQueryConditions(
            boolean ignoreInternetConnection, boolean ignoreRefreshCondition) {
        if (!isDebugBuild()) {
            logd("overrideEntilementQueryConditions: " + "not allowed for non-debug build.");
            return false;
        }

        logd(
                "overrideEntilementQueryConditions: ignoreInternetConnection="
                        + ignoreInternetConnection
                        + ", ignoreRefreshCondition="
                        + ignoreRefreshCondition);
        mShouldIgnoreInternetConnectionStateForCtsTest.set(ignoreInternetConnection);
        mShouldIgnoreRefreshConditionForCtsTest.set(ignoreRefreshCondition);
        return true;
    }

    private void handleCarrierConfigChanged(
            int slotIndex, int subId, int carrierId, int specificCarrierId) {
        logd(
                "handleCarrierConfigChanged(): slotIndex("
                        + slotIndex
                        + "), subId("
                        + subId
                        + "), carrierId("
                        + carrierId
                        + "), specificCarrierId("
                        + specificCarrierId
                        + ")");
        processSimChanged(slotIndex, subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }


        Message message = obtainMessage();
        message.what = CMD_START_QUERY_ENTITLEMENT;
        message.arg1 = subId;
        message.arg2 = SatelliteConstants
                .SATELLITE_ENTITLEMENT_QUERY_TRIGGER_CARRIER_CONFIG_CHANGED;

        sendMessage(message);
        mSubIdPerSlot.put(slotIndex, subId);
    }

    // When SIM is removed or changed, then reset the previous subId's retry related objects.
    private void processSimChanged(int slotIndex, int subId) {
        int previousSubId =
                mSubIdPerSlot.getOrDefault(slotIndex, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        logd("processSimChanged prev subId:" + previousSubId);
        if (previousSubId != subId) {
            mSubIdPerSlot.remove(slotIndex);
            logd("processSimChanged resetEntitlementQueryPerSubId");
            resetEntitlementQueryPerSubId(previousSubId);
        }
    }

    private class SatelliteEntitlementControllerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                handleAirplaneModeChange(airplaneMode);
            }
        }
    }

    private void handleAirplaneModeChange(boolean airplaneMode) {
        logd("handleAirplaneModeChange: airplaneMode=" + airplaneMode);
        if (mFeatureFlags.satelliteImproveMultiThreadDesign()) {
            sendMessage(obtainMessage(AIRPLANE_MODE_CHANGED, airplaneMode));
            return;
        }

        if (!airplaneMode) {
            resetEntitlementQueryCounts(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        }
    }

    private void handleSimRefresh() {
        resetEntitlementQueryCounts(cmdToString(CMD_SIM_REFRESH));

        Message message = obtainMessage();
        message.what = CMD_START_QUERY_ENTITLEMENT;
        message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_SIM_REFRESH;
        sendMessageDelayed(message, TimeUnit.SECONDS.toMillis(10));
    }

    private boolean isInternetConnected() {
        if (mShouldIgnoreInternetConnectionStateForCtsTest.get()) {
            logd("isInternetConnected: ignore internet connection state for CTS test");
            return true;
        }
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities =
                mConnectivityManager.getNetworkCapabilities(activeNetwork);
        // TODO b/319780796 Add checking if it is not a satellite.
        return networkCapabilities != null
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void handleInternetConnected() {
        Message message = obtainMessage();
        message.what = CMD_START_QUERY_ENTITLEMENT;
        message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_INTERNET_CONNECTED;
        sendMessage(message);
    }

    /**
     * Handles a request to refresh the satellite entitlement status.
     *
     * <p>This method posts a message to the handler to start the entitlement query
     * for the specified subscription ID.
     *
     * @param subId The subscription ID to refresh entitlement for.
     * @param callback The callback to report the result.
     */
    public void requestEntitlementRefresh(int subId, @NonNull IIntegerConsumer callback) {
        logd("requestEntitlementRefresh: subId = " + subId);
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = subId;
        args.arg2 = true;
        args.arg3 = callback;
        args.arg4 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_FCM_TICKLE;
        Message msg = obtainMessage(CMD_START_QUERY_ENTITLEMENT_FOR_SUB_ID, args);
        sendMessage(msg);
    }

    private int[] getServiceTypeForEntitlementMetrics(Map<String, List<Integer>> map) {
        if (map == null || map.isEmpty()) {
            return new int[] {};
        }

        return map.entrySet().stream()
                .findFirst()
                .map(
                        entry -> {
                            List<Integer> list = entry.getValue();
                            if (list == null) {
                                return new int[] {}; // Return empty array if the list is null
                            }
                            return list.stream().mapToInt(Integer::intValue).toArray();
                        })
                .orElse(new int[] {}); // Return empty array if no entry is found
    }

    private int getDataPolicyForEntitlementMetrics(Map<String, Integer> dataPolicyMap) {
        if (dataPolicyMap != null && !dataPolicyMap.isEmpty()) {
            return dataPolicyMap.values().stream().findFirst().orElse(-1);
        }
        return -1;
    }

    private void reportSuccessForEntitlement(
            int subId, SatelliteEntitlementResult entitlementResult, int triggerEvent) {
        // allowed service info entitlement status
        boolean isAllowedServiceInfo =
                !entitlementResult.getAvailableServiceTypeInfoForPlmnList().isEmpty();

        int[] serviceType = new int[0];
        int dataPolicy = 0;
        if (isAllowedServiceInfo) {
            serviceType =
                    getServiceTypeForEntitlementMetrics(
                            entitlementResult.getAvailableServiceTypeInfoForPlmnList());
            dataPolicy =
                    SatelliteController.getInstance()
                            .mapDataPolicyForMetrics(
                                    getDataPolicyForEntitlementMetrics(
                                            entitlementResult
                                                    .getDataServicePolicyInfoForPlmnList()));
        }
        mEntitlementMetricsStats.reportSuccess(
                subId,
                getEntitlementStatus(entitlementResult),
                true,
                isAllowedServiceInfo,
                serviceType,
                dataPolicy, triggerEvent);
    }

    /**
     * Check if the device can request to entitlement server (if there is an internet connection and
     * if the throttle time has passed since the last request), and then pass the response to
     * SatelliteController if the response is received.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected void handleCmdStartQueryEntitlement(int triggerEvent) {
        for (int subId : mSubscriptionManagerService.getActiveSubIdList(true)) {
            handleCmdStartQueryEntitlementForSubId(subId, false, null, triggerEvent);
        }
    }

    /**
     * Orchestrates the execution of the satellite entitlement query for a given subscription.
     *
     * <p>This method executes a blocking network call and manages the complete query lifecycle:
     * <ul>
     * <li><b>Validation:</b> Verifies preconditions (carrier support, connectivity, throttling)
     * via {@link #shouldQueryEntitlementForSubId}.</li>
     * <li><b>State Management:</b> Marks the query as "In Progress" to prevent concurrent
     * requests for the same ID.</li>
     * <li><b>Execution:</b> Performs the synchronous network request to the entitlement server.
     * </li>
     * <li><b>Error Handling:</b> Dispatches specific actions based on the exception type:
     * <ul>
     * <li><i>Permanent Errors:</i> Halts retries immediately.</li>
     * <li><i>Retry-After:</i> Schedules a precise delayed retry based on server headers and
     * stops generic exponential backoff.</li>
     * <li><i>Network/Transient:</i> Reports failure (allowing the caller or default logic to
     * handle standard backoff).</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <p><b>Note:</b> This method performs blocking I/O operations and must be executed on a
     * background handler thread.
     *
     * @param subId The integer ID of the subscription to query.
     * @param ignoreApiThrottle If {@code false}, validates local throttling/timeout logic before
     * proceeding. If {@code false}, bypasses throttling checks.
     * @param callback An optional consumer to receive the final {@code SATELLITE_RESULT_*} code.
     * Used by the caller to determine if further scheduling is required.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected void handleCmdStartQueryEntitlementForSubId(
            int subId,
            boolean ignoreApiThrottle,
            @Nullable IIntegerConsumer callback,
            int triggerEvent
    ) {
        // Clear retry count for the sub id
        clearRetryCountForSubId(subId);

        // Validation Phase
        if (!shouldQueryEntitlementForSubId(subId, ignoreApiThrottle, false, callback)) {
            return;
        }

        // Execution Phase
        try {
            // Mark the query as active to prevent concurrent requests (checked in validation step)
            mIsEntitlementInProgressPerSub.put(subId, true);
            logd("handleCmdStartQueryEntitlement: checkEntitlementStatus");

            // Perform the actual network request (Blocking Call)
            SatelliteEntitlementResult entitlementResult = checkEntitlementStatus(subId);

            // Success: Update cache and notify
            mSatelliteEntitlementResultPerSub.put(subId, entitlementResult);
            reportSuccessForEntitlement(subId, entitlementResult, triggerEvent);
            sendResult(subId, callback, SATELLITE_RESULT_SUCCESS);
        } catch (ServiceEntitlementException e) {
            // Error Handling Phase
            loge(e.toString());

            // Report the raw HTTP/API error to metrics
            mEntitlementMetricsStats.reportError(subId, e.getErrorCode(), false,
                    e.getHttpStatus(), triggerEvent);

            if (!isInternetConnected()) {
                // Scenario A: Connection lost during the API call
                logd("StartQuery: disconnected during execution. " + e);

                // Cleanup progress immediately so we aren't stuck in "In Progress" state
                // until the finally block cleans up.
                mIsEntitlementInProgressPerSub.remove(subId);
                sendResult(subId, callback, SATELLITE_RESULT_NETWORK_ERROR);
            } else if (isPermanentError(e)) {
                // Scenario B: Permanent Error
                // Stop retrying immediately.
                queryCompleted(subId);
                sendResult(subId, callback, SATELLITE_RESULT_SERVER_ERROR);
            } else if (isRetryAfterError(e)) {
                // Scenario C: Server requested a specific "Retry-After" delay
                long retryAfterSeconds = parseSecondsFromRetryAfter(e.getRetryAfter());
                logd("StartQuery: next retry will be in "
                        + TimeUnit.SECONDS.toMillis(retryAfterSeconds)
                        + " sec");

                // Schedule a specific retry message based on the server's instruction
                Message message = obtainMessage();
                message.what = CMD_RETRY_QUERY_ENTITLEMENT;
                message.arg1 = subId;
                message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_RETRY;
                sendMessageDelayed(message, TimeUnit.SECONDS.toMillis(retryAfterSeconds));

                // Important: Stop the generic exponential backoff because the server gave us
                // explicit instructions on when to come back.
                stopExponentialBackoff(subId);
                // Fail the current specific callback request, even though a retry is scheduled.
                sendResult(subId, callback, SATELLITE_RESULT_ERROR);
            } else {
                // Scenario D: Generic/Transient Error
                startExponentialBackoff(subId);
                sendResult(subId, callback, SATELLITE_RESULT_ERROR);
            }
            return;
        }

        // Cleanup Phase
        // Ensures internal state flags are reset and wake locks (if any) are released.
        queryCompleted(subId);
    }

    /**
     * Send the result of the entitlement query process to the callback.
     *
     * @param subId The subscription ID to query entitlement for.
     * @param callback The callback to report the result.
     * @param result The result of the entitlement query process.
     */
    private void sendResult(int subId, @Nullable IIntegerConsumer callback, int result) {
        if (callback == null) return;
        try {
            callback.accept(result);
        } catch (RemoteException e) {
            loge("sendResult:\nsubId = " + subId + "\nresult = " + result + "\ne = " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * When airplane mode changes from on to off, reset the values required to start the first
     * query.
     */
    private void resetEntitlementQueryCounts(String event) {
        logd("resetEntitlementQueryCounts: " + event);
        mLastQueryTimePerSub = new ConcurrentHashMap<>();
        mExponentialBackoffPerSub = new ConcurrentHashMap<>();
        mRetryCountPerSub = new ConcurrentHashMap<>();
        mIsEntitlementInProgressPerSub = new ConcurrentHashMap<>();
    }

    /**
     * If the HTTP response does not receive a body containing the 200 ok with sat mode
     * configuration,
     *
     * <p>1. If the 500 response received, then no more retry until next event occurred. 2. If the
     * 503 response with Retry-After header received, then the query is retried until
     * MAX_RETRY_COUNT. 3. If other response or exception is occurred, then the query is retried
     * until MAX_RETRY_COUNT is reached using the ExponentialBackoff.
     */
    private void handleCmdRetryQueryEntitlement(int subId, int triggerEvent) {
        if (!shouldQueryEntitlementForSubId(subId, false, true, null)) {
            return;
        }
        try {
            int currentRetryCount = getRetryCount(subId);
            mRetryCountPerSub.put(subId, currentRetryCount + 1);
            logd("[" + subId + "] retry cnt:" + getRetryCount(subId));
            logd("handleCmdRetryQueryEntitlement: checkEntitlementStatus");
            SatelliteEntitlementResult entitlementResult = checkEntitlementStatus(subId);
            mSatelliteEntitlementResultPerSub.put(subId, entitlementResult);
            reportSuccessForEntitlement(subId, entitlementResult, triggerEvent);
        } catch (ServiceEntitlementException e) {
            loge(e.toString());
            mEntitlementMetricsStats.reportError(subId, e.getErrorCode(), true,
                    e.getHttpStatus(), triggerEvent);
            if (!isRetryAvailable(subId)) {
                logd("retryQuery: unavailable.");
                queryCompleted(subId);
                return;
            }
            if (!isInternetConnected()) {
                logd("retryQuery: Internet disconnected.");
                stopExponentialBackoff(subId);
                mIsEntitlementInProgressPerSub.remove(subId);
                return;
            }
            if (isPermanentError(e)) {
                logd("retryQuery: shouldPermanentError.");
                queryCompleted(subId);
                return;
            } else if (isRetryAfterError(e)) {
                long retryAfterSeconds = parseSecondsFromRetryAfter(e.getRetryAfter());
                logd(
                        "retryQuery: next retry will be in "
                                + TimeUnit.SECONDS.toMillis(retryAfterSeconds)
                                + " sec");

                Message message = obtainMessage();
                message.what = CMD_RETRY_QUERY_ENTITLEMENT;
                message.arg1 = subId;
                message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_RETRY;
                sendMessageDelayed(message, TimeUnit.SECONDS.toMillis(retryAfterSeconds));
                stopExponentialBackoff(subId);
                return;
            } else {
                ExponentialBackoff exponentialBackoff = mExponentialBackoffPerSub.get(subId);
                if (exponentialBackoff == null) {
                    startExponentialBackoff(subId);
                } else {
                    exponentialBackoff.notifyFailed();
                    logd(
                            "retryQuery: The next retry will be in "
                                    + exponentialBackoff.getCurrentDelay()
                                    + " ms.");
                }
                return;
            }
        }
        queryCompleted(subId);
    }

    // If the 500 response is received, no retry until the next trigger event occurs.
    private boolean isPermanentError(ServiceEntitlementException e) {
        return e.getHttpStatus() == HTTP_RESPONSE_500;
    }

    /**
     * If the 503 response with Retry-After header, retry is attempted according to the value in the
     * Retry-After header up to MAX_RETRY_COUNT.
     */
    private boolean isRetryAfterError(ServiceEntitlementException e) {
        int responseCode = e.getHttpStatus();
        logd("shouldRetryAfterError: received the " + responseCode);
        if (responseCode == HTTP_RESPONSE_503
                && e.getRetryAfter() != null
                && !e.getRetryAfter().isEmpty()) {
            long retryAfterSeconds = parseSecondsFromRetryAfter(e.getRetryAfter());
            if (retryAfterSeconds == -1) {
                logd("Unable parsing the retry-after. try to exponential backoff.");
                return false;
            }
            return true;
        }
        return false;
    }

    /** Parse the HTTP-date or a number of seconds in the retry-after value. */
    private long parseSecondsFromRetryAfter(String retryAfter) {
        try {
            return Long.parseLong(retryAfter);
        } catch (NumberFormatException numberFormatException) {
        }

        try {
            return SECONDS.between(
                    Instant.now(), RFC_1123_DATE_TIME.parse(retryAfter, Instant::from));
        } catch (DateTimeParseException dateTimeParseException) {
        }

        return -1;
    }

    private void startExponentialBackoff(int subId) {
        stopExponentialBackoff(subId);
        mExponentialBackoffPerSub.put(
                subId,
                new ExponentialBackoff(
                        INITIAL_DELAY_MILLIS,
                        MAX_DELAY_MILLIS,
                        MULTIPLIER,
                        this.getLooper(),
                        () -> {
                            Message message = obtainMessage();
                            message.what = CMD_RETRY_QUERY_ENTITLEMENT;
                            message.arg1 = subId;
                            message.arg2 = SatelliteConstants
                                    .SATELLITE_ENTITLEMENT_QUERY_TRIGGER_RETRY;
                            sendMessage(message);
                        }));

        ExponentialBackoff exponentialBackoff = mExponentialBackoffPerSub.get(subId);
        if (exponentialBackoff != null) {
            exponentialBackoff.start();
            logd(
                    "start ExponentialBackoff, cnt: "
                            + getRetryCount(subId)
                            + ". Retrying in "
                            + exponentialBackoff.getCurrentDelay()
                            + " ms.");
        }
    }

    /**
     * If the Internet connection is lost during the ExponentialBackoff, stop the ExponentialBackoff
     * and reset it.
     */
    private void stopExponentialBackoff(int subId) {
        if (mExponentialBackoffPerSub.get(subId) != null) {
            logd("stopExponentialBackoff: reset ExponentialBackoff");
            mExponentialBackoffPerSub.get(subId).stop();
            mExponentialBackoffPerSub.remove(subId);
        }
    }

    /**
     * No more query retry, update the result. If there is no response from the server, then used
     * the default value - 'satellite disabled' and empty 'PLMN allowed list'. And then it send a
     * delayed message to trigger the query again after A refresh day has passed.
     */
    private void queryCompleted(int subId) {
        // If no entitlement result was ever stored for this subId (e.g., server was unreachable),
        // create and store a default result.
        if (!mSatelliteEntitlementResultPerSub.containsKey(subId)) {
            logd("queryCompleted: create default SatelliteEntitlementResult");
            mSatelliteEntitlementResultPerSub.put(
                    subId, SatelliteEntitlementResult.getDefaultResult());
        }

        // Retrieve the entitlement result to be used for the update.
        SatelliteEntitlementResult entitlementResult = mSatelliteEntitlementResultPerSub.get(subId);

        // Stop any ongoing exponential backoff retry mechanism for this subId.
        stopExponentialBackoff(subId);

        // Remove the "in progress" flag for this subId's entitlement query.
        mIsEntitlementInProgressPerSub.remove(subId);

        // Reset the retry count for this subId, as the current query cycle is complete.
        clearRetryCountForSubId(subId);

        // Record the time of this query completion to manage the refresh schedule.
        saveLastQueryTime(subId);

        // Prepare a message to trigger the next entitlement query.
        Message message = obtainMessage();
        message.what = CMD_START_QUERY_ENTITLEMENT;
        message.arg1 = subId;
        message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_REFRESH_TIMER;

        // Schedule the next query after the configured refresh period (in days).
        sendMessageDelayed(
                message, TimeUnit.DAYS.toMillis(getSatelliteEntitlementStatusRefreshDays(subId)));
        logd("queryCompleted: updateSatelliteEntitlementStatus");

        // Update the SatelliteController with the final entitlement status.
        updateSatelliteEntitlementStatus(
                subId,
                entitlementResult.getEntitlementStatus()
                        == SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                entitlementResult.getAllowedPLMNList(),
                entitlementResult.getBarredPLMNList(),
                entitlementResult.getDataPlanInfoForPlmnList(),
                entitlementResult.getAvailableServiceTypeInfoForPlmnList(),
                entitlementResult.getDataServicePolicyInfoForPlmnList(),
                entitlementResult.getVoiceServicePolicyInfoForPlmnList());
    }

    private void clearRetryCountForSubId(int subId) {
        logd("reset retry count for refresh query for subId = " + subId);
        mRetryCountPerSub.remove(subId);
    }

    /**
     * Validates whether a satellite entitlement query can and should proceed for the given
     * subscription.
     *
     * <p>This method evaluates a series of preconditions in a strict order. If any condition fails,
     * it immediately triggers the {@code callback} with the specific failure reason (e.g.,
     * {@code SATELLITE_RESULT_NETWORK_ERROR}) and returns {@code false}.
     *
     * <p><b>Validation Steps:</b>
     * <ul>
     * <li><b>Carrier Support:</b> Checks if the carrier configuration enables satellite
     * entitlement.</li>
     * <li><b>Concurrency:</b> Ensures a query for this ID is not already "In Progress".</li>
     * <li><b>Connectivity:</b> Verifies active internet connection.
     * <br><i>Side Effect:</i> If connectivity fails, this method stops any pending exponential
     * backoff and clears the internal in-progress state to reset the flow.</li>
     * <li><b>Throttling:</b> If {@code ignoreApiThrottle} is set to {@code false}, checks if the
     * cached status is still fresh to prevent spamming the server.</li>
     * <li><b>Retry Limits:</b> Verifies that the maximum retry count has not been exceeded.</li>
     * </ul>
     *
     * @param subId The subscription ID to validate.
     * @param ignoreApiThrottle If {@code false}, respects the standard refresh interval
     *                             (throttling).
     * If {@code false}, bypasses the freshness check to force a re-query.
     * @param isRetry If {@code true}, bypasses the "In Progress" check because this is a
     *                scheduled retry for an existing active query.
     * @param callback The consumer to notify if a validation step fails.
     * <b>Note:</b> If validation succeeds (returns {@code true}), this callback is <i>not</i>
     * invoked by this method; it is the caller's responsibility to proceed.
     * @return {@code true} if all preconditions are met and the query should proceed;
     * {@code false} otherwise.
     */
    private boolean shouldQueryEntitlementForSubId(int subId,
            boolean ignoreApiThrottle,
            boolean isRetry,
            @Nullable IIntegerConsumer callback) {
        logd("Checking preconditions for subId = " + subId);

        // 1. Check Carrier Support (Config)
        // Verify if the carrier configuration allows satellite entitlement for this subId.
        if (!isSatelliteEntitlementSupported(subId)) {
            logd("Entitlement not supported by carrier config.");
            resetSatelliteEntitlementRestrictedReason(subId);
            sendResult(subId, callback, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
            return false;
        }

        // 2. Check In-Progress State
        // Prevent concurrent queries for the same subscription to avoid race conditions.
        if (!isRetry && mIsEntitlementInProgressPerSub.getOrDefault(subId, false)) {
            logd("Entitlement query already in progress.");
            sendResult(subId, callback, SATELLITE_RESULT_REQUEST_IN_PROGRESS);
            return false;
        }

        // 3. Check Internet Connectivity
        // An active data connection is required to reach the entitlement server.
        if (!isInternetConnected()) {
            logd("No internet connection available.");

            // CLEANUP: Since we cannot proceed due to network, we must stop the backoff
            // timer and clear the progress flag so the system doesn't get stuck in a "busy" state.
            stopExponentialBackoff(subId);
            mIsEntitlementInProgressPerSub.remove(subId);
            sendResult(subId, callback, SATELLITE_RESULT_NETWORK_ERROR);
            return false;
        }

        // 4. Check Refresh Timeout (Throttling)
        // If timeout enforcement is requested, ensure enough time has passed since the
        // last check to avoid spamming the server.
        if (!ignoreApiThrottle && !shouldRefreshEntitlementStatus(subId)) {
            logd("Entitlement status is fresh; skipping query due to timeout enforcement.");
            sendResult(subId, callback, SATELLITE_RESULT_ERROR);
            return false;
        }

        // 5. Check Retry Availability
        // Ensure we have not exceeded the maximum number of allowed retry attempts.
        if (!isRetryAvailable(subId)) {
            logd("Retry limit reached for entitlement query.");
            sendResult(subId, callback, SATELLITE_RESULT_ERROR);
            return false;
        }

        // All preconditions met.
        return true;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    // update for removing the satellite entitlement restricted reason
    protected void resetSatelliteEntitlementRestrictedReason(int subId) {
        logd("resetSatelliteEntitlementRestrictedReason, subId=" + subId);
        SatelliteEntitlementResult enabledResult =
                new SatelliteEntitlementResult(
                        SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED,
                        new ArrayList<>(),
                        new ArrayList<>());
        SatelliteEntitlementResult previousResult = mSatelliteEntitlementResultPerSub.get(subId);

        if (previousResult != null
                && previousResult.getEntitlementStatus()
                        != SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED) {
            logd("set enabled status for removing satellite entitlement restricted reason");
            mSatelliteEntitlementResultPerSub.put(subId, enabledResult);
            updateSatelliteEntitlementStatus(
                    subId,
                    true,
                    enabledResult.getAllowedPLMNList(),
                    enabledResult.getBarredPLMNList(),
                    enabledResult.getDataPlanInfoForPlmnList(),
                    enabledResult.getAvailableServiceTypeInfoForPlmnList(),
                    enabledResult.getDataServicePolicyInfoForPlmnList(),
                    enabledResult.getVoiceServicePolicyInfoForPlmnList());
        }
        resetEntitlementQueryPerSubId(subId);
    }

    private void resetEntitlementQueryPerSubId(int subId) {
        logd("resetEntitlementQueryPerSubId: " + subId);
        stopExponentialBackoff(subId);
        mLastQueryTimePerSub.remove(subId);
        mRetryCountPerSub.remove(subId);
        mIsEntitlementInProgressPerSub.remove(subId);

        Message message = obtainMessage();
        message.what = CMD_RETRY_QUERY_ENTITLEMENT;
        message.arg1 = subId;
        message.arg2 = SatelliteConstants.SATELLITE_ENTITLEMENT_QUERY_TRIGGER_RETRY;
        removeMessages(CMD_RETRY_QUERY_ENTITLEMENT, message);
    }

    /**
     * Compare the last query time to the refresh time from the CarrierConfig to see if the device
     * can query the entitlement server.
     */
    private boolean shouldRefreshEntitlementStatus(int subId) {
        if (mShouldIgnoreRefreshConditionForCtsTest.get()) {
            logd("shouldRefreshEntitlementStatus: allow refreshing entitlement for CTS test");
            return true;
        }

        long lastQueryTimeMillis = getLastQueryTime(subId);
        long refreshTimeMillis =
                TimeUnit.DAYS.toMillis(getSatelliteEntitlementStatusRefreshDays(subId));
        boolean isAvailable =
                (System.currentTimeMillis() - lastQueryTimeMillis) > refreshTimeMillis;
        if (!isAvailable) {
            logd(
                    "query is already done. can query after "
                            + Instant.ofEpochMilli(refreshTimeMillis + lastQueryTimeMillis));
        }
        return isAvailable;
    }

    /**
     * Get the SatelliteEntitlementApi.
     *
     * @param subId The subId of the subscription for creating SatelliteEntitlementApi
     * @return A new SatelliteEntitlementApi object.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected SatelliteEntitlementApi getSatelliteEntitlementApi(int subId) {
        return new SatelliteEntitlementApi(mContext, getConfigForSubId(subId), subId);
    }

    /**
     * If there is a value stored in the cache, it is used. If there is no value stored in the
     * cache, it is considered the first query.
     */
    private long getLastQueryTime(int subId) {
        return mLastQueryTimePerSub.getOrDefault(subId, 0L);
    }

    /** Return the satellite entitlement status refresh days from carrier config. */
    private int getSatelliteEntitlementStatusRefreshDays(int subId) {
        return getConfigForSubId(subId)
                .getInt(
                        CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                        DEFAULT_QUERY_REFRESH_DAYS);
    }

    private boolean isRetryAvailable(int subId) {
        if (getRetryCount(subId) >= MAX_RETRY_COUNT) {
            logd("The retry will not be attempted until the next trigger event.");
            return false;
        }
        return true;
    }

    private boolean isEntitlementItemExistOnSatelliteConfig() {
        SatelliteConfig config = SatelliteController.getInstance().getSatelliteConfig();
        if (config == null) {
            logd("isEntitlementItemExistOnSatelliteConfig: "
                    + "return false (SatelliteConfig is null)");
            return false;
        }

        for (Integer slotIndex : mSubIdPerSlot.keySet()) {
            int subId = mSubIdPerSlot.get(slotIndex);
            Boolean entitlementSupported = config.isSatelliteEntitlementSupportedBySubId(subId);
            if (entitlementSupported != null) {
                logd("isEntitlementItemExistOnSatelliteConfig:"
                        + " entitlement support exist, return true");
                return true;
            }

            String url = config.getSatelliteEntitlementServerUrlBySubId(subId);
            if (!TextUtils.isEmpty(url)) {
                logd("isEntitlementItemExistOnSatelliteConfig: entitlement url exist, return true");
                return true;
            }
        }

        logd("isEntitlementItemExistOnSatelliteConfig: entitlement related item is not exist ");
        return false;
    }

    @Nullable
    private Boolean getEntitlementSupportedFromSatelliteConfig(int subId) {
        SatelliteConfig config = SatelliteController.getInstance().getSatelliteConfig();
        if (config == null) {
            logd("getEntitlementSupportedFromSatelliteConfig: "
                    + "return null (SatelliteConfig is null)");
            return null;
        }

        return config.isSatelliteEntitlementSupportedBySubId(subId);
    }

    /** Return the satellite entitlement supported bool from carrier config. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected boolean isSatelliteEntitlementSupported(int subId) {
        // 1. get from SatelliteConfig
        Boolean supported = getEntitlementSupportedFromSatelliteConfig(subId);
        if (supported != null) {
            logd("isSatelliteEntitlementSupported: using SatelliteConfig for subId=" + subId
                    + ", entitlementSupported=" + supported);
            return supported;
        }
        // 2. get from CarrierConfig
        return getConfigForSubId(subId)
                .getBoolean(CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL);
    }

    @NonNull
    private PersistableBundle getConfigForSubId(int subId) {
        PersistableBundle config = null;
        if (mCarrierConfigManager != null) {
            config =
                    mCarrierConfigManager.getConfigForSubId(
                            subId,
                            CarrierConfigManager.ImsServiceEntitlement
                                    .KEY_ENTITLEMENT_SERVER_URL_STRING,
                            CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                            CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL,
                            CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_APP_NAME_STRING);
        }
        if (config == null || config.isEmpty()) {
            config = CarrierConfigManager.getDefaultConfig();
        }
        return config;
    }

    private void saveLastQueryTime(int subId) {
        long lastQueryTimeMillis = System.currentTimeMillis();
        mLastQueryTimePerSub.put(subId, lastQueryTimeMillis);
    }

    private int getRetryCount(int subId) {
        return mRetryCountPerSub.getOrDefault(subId, 0);
    }

    /**
     * Send to satelliteController for update the satellite service enabled or not and plmn Allowed
     * list.
     */
    private void updateSatelliteEntitlementStatus(
            int subId,
            boolean enabled,
            List<String> plmnAllowedList,
            List<String> plmnBarredList,
            Map<String, Integer> plmnDataPlanMap,
            Map<String, List<Integer>> plmnAllowedServicesMap,
            Map<String, Integer> plmnDataServicePolicyMap,
            Map<String, Integer> plmnVoiceServicePolicyMap) {
        SatelliteController.getInstance()
                .onSatelliteEntitlementStatusUpdated(
                        subId,
                        enabled,
                        plmnAllowedList,
                        plmnBarredList,
                        plmnDataPlanMap,
                        plmnAllowedServicesMap,
                        plmnDataServicePolicyMap,
                        plmnVoiceServicePolicyMap,
                        null);
    }

    private @SatelliteConstants.SatelliteEntitlementStatus int getEntitlementStatus(
            SatelliteEntitlementResult entitlementResult) {
        switch (entitlementResult.getEntitlementStatus()) {
            case SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_DISABLED:
                return SatelliteConstants.SATELLITE_ENTITLEMENT_STATUS_DISABLED;
            case SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED:
                return SatelliteConstants.SATELLITE_ENTITLEMENT_STATUS_ENABLED;
            case SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE:
                return SatelliteConstants.SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE;
            case SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_PROVISIONING:
                return SatelliteConstants.SATELLITE_ENTITLEMENT_STATUS_PROVISIONING;
            default:
                return SatelliteConstants.SATELLITE_ENTITLEMENT_STATUS_UNKNOWN;
        }
    }

    /**
     * Check the entitlement status from the entitlement server.
     *
     * @param subId The subId of the subscription for creating SatelliteEntitlementApi
     * @return The SatelliteEntitlementResult
     */
    @NonNull
    private SatelliteEntitlementResult checkEntitlementStatus(int subId)
            throws ServiceEntitlementException {
        logd("checkEntitlementStatus: subId=" + subId);
        SatelliteEntitlementApi entitlementApi = getSatelliteEntitlementApi(subId);
        entitlementApi.overrideEntilementStatusResponseForCtsTest(
                mOverriddenEntilementStatusResponseForCtsTest,
                mShouldThrowExceptionForCtsTest.get());
        return entitlementApi.checkEntitlementStatus();
    }

    private static String cmdToString(int cmd) {
        switch (cmd) {
            case CMD_SIM_REFRESH:
                return "SIM_REFRESH";
            default:
                return "UNKNOWN(" + cmd + ")";
        }
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(String log) {
        Rlog.e(TAG, log);
    }

    private static boolean isDebugBuild() {
        return IS_DEBUG_BUILD;
    }
}
