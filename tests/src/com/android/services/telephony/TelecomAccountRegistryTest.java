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

package com.android.services.telephony;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SimultaneousCallingTracker;
import com.android.internal.telephony.flags.Flags;
import com.android.phone.PhoneInterfaceManager;
import com.android.phone.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TelecomAccountRegistryTest extends TelephonyTestBase {

    private static final String TAG = "TelecomAccountRegistryTest";
    private static final int TEST_SUB_ID = 1;
    private static final int TEST_SUB_ID_2 = 2;
    private static final long TIMEOUT = 10000;

    // We need more functions that what TelephonyTestBase.mContext supports.
    // Use a local mocked Context to make life easier.
    @Mock Context mMockedContext;
    @Mock TelecomManager mTelecomManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock ImsManager mImsManager;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock ContentProvider mContentProvider;
    @Mock Phone mPhone2;
    @Mock Resources mResources;
    @Mock Drawable mDrawable;
    @Mock PhoneInterfaceManager mPhoneInterfaceManager;
    @Mock PackageManager mPackageManager;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private TelecomAccountRegistry mTelecomAccountRegistry;

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;
    private TelephonyCallback mTelephonyCallback;
    private BroadcastReceiver mUserSwitchedAndConfigChangedReceiver;
    private BroadcastReceiver mLocaleChangedBroadcastReceiver;
    private ContentResolver mContentResolver;
    private TestableLooper mTestableLooper;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        replaceInstance(PhoneInterfaceManager.class, "sInstance", null, mPhoneInterfaceManager);
        when(mPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mPhone.getContext()).thenReturn(mMockedContext);
        when(mPhone.getSubId()).thenReturn(TEST_SUB_ID);
        when(mPhoneInterfaceManager.isRttEnabled(anyInt())).thenReturn(false);
        when(mPhone2.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mPhone2.getContext()).thenReturn(mMockedContext);
        when(mPhone2.getSubId()).thenReturn(TEST_SUB_ID_2);
        when(mPhoneInterfaceManager.isRttEnabled(anyInt())).thenReturn(false);

        when(mMockedContext.getResources()).thenReturn(mResources);
        // Enable PSTN PhoneAccount which can place emergency call by default
        when(mResources.getBoolean(R.bool.config_pstn_phone_accounts_enabled)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_pstnCanPlaceEmergencyCalls)).thenReturn(true);
        when(mResources.getDrawable(anyInt(), any())).thenReturn(mDrawable);
        when(mDrawable.getIntrinsicWidth()).thenReturn(5);
        when(mDrawable.getIntrinsicHeight()).thenReturn(5);

        when(mMockedContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS)).thenReturn(true);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SUPPORT_IMS_CONFERENCE_CALL_BOOL, false);
        bundle.putIntArray(CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY,
                new int[]{
                        SubscriptionManager.SERVICE_CAPABILITY_VOICE,
                        SubscriptionManager.SERVICE_CAPABILITY_SMS,
                        SubscriptionManager.SERVICE_CAPABILITY_DATA
                });
        when(mPhoneGlobals.getCarrierConfigForSubId(anyInt())).thenReturn(bundle);

        // Mock system services used by TelecomAccountRegistry
        when(mMockedContext.getSystemServiceName(TelecomManager.class))
                .thenReturn(Context.TELECOM_SERVICE);
        when(mMockedContext.getSystemService(TelecomManager.class))
                .thenReturn(mTelecomManager);
        when(mMockedContext.getSystemServiceName(TelephonyManager.class))
                .thenReturn(Context.TELEPHONY_SERVICE);
        when(mMockedContext.getSystemService(TelephonyManager.class))
                .thenReturn(mTelephonyManager);
        when(mMockedContext.getSystemServiceName(ImsManager.class))
                .thenReturn(Context.TELEPHONY_IMS_SERVICE);
        when(mMockedContext.getSystemService(ImsManager.class))
                .thenReturn(mImsManager);
        when(mMockedContext.getSystemServiceName(SubscriptionManager.class))
                .thenReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        when(mMockedContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mSubscriptionManager);

        // Use mocked ContentProvider since we can't really mock ContentResolver
        mContentResolver = ContentResolver.wrap(mContentProvider);
        when(mMockedContext.getContentResolver()).thenReturn(mContentResolver);

        //Simulate isTelecomReady is true so setupOnBootInternal is called during test setup.
        when(mTelecomManager.getSystemDialerPackage()).thenReturn("");

        // Mock SubscriptionInfo
        SubscriptionInfo subscriptionInfo = Mockito.mock(SubscriptionInfo.class);
        when(subscriptionInfo.getDisplayName()).thenReturn("Test Sub");
        when(subscriptionInfo.getSimSlotIndex()).thenReturn(0);
        when(subscriptionInfo.createIconBitmap(any())).thenReturn(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
        when(mSubscriptionManager.getActiveSubscriptionInfo(
                anyInt())).thenReturn(subscriptionInfo);

        mTestableLooper = TestableLooper.get(this);
        when(mMockedContext.getMainLooper()).thenReturn(mTestableLooper.getLooper());
        replaceInstance(Handler.class, "mLooper", mPhone, mTestableLooper.getLooper());
        mTelecomAccountRegistry = new TelecomAccountRegistry(mMockedContext);
        mTelecomAccountRegistry.setupOnBoot();
        // Benign; in the unflagged context this does nothing, but in the flagged context this will
        // process the handler-posted setup.
        mTestableLooper.processAllMessages();

        // Capture OnSubscriptionsChangedListener
        ArgumentCaptor<OnSubscriptionsChangedListener> subChangeListenerCaptor =
                ArgumentCaptor.forClass(OnSubscriptionsChangedListener.class);
        verify(mSubscriptionManager).addOnSubscriptionsChangedListener(
                subChangeListenerCaptor.capture());
        mOnSubscriptionsChangedListener = subChangeListenerCaptor.getValue();

        // Capture TelephonyCallback
        ArgumentCaptor<TelephonyCallback> telephonyCallbackArgumentCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);
        verify(mTelephonyManager).registerTelephonyCallback(anyInt(), any(),
                telephonyCallbackArgumentCaptor.capture());
        mTelephonyCallback = telephonyCallbackArgumentCaptor.getValue();

        // Capture BroadcastReceivers
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockedContext, times(2)).registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                any());
        mUserSwitchedAndConfigChangedReceiver =
                broadcastReceiverArgumentCaptor.getAllValues().get(0);
        mLocaleChangedBroadcastReceiver = broadcastReceiverArgumentCaptor.getAllValues().get(1);

        replaceInstance(SimultaneousCallingTracker.class, "sInstance", null,
                Mockito.mock(SimultaneousCallingTracker.class));

        mTestableLooper.processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void userSwitched_withPSTNAccount_shouldRegisterPSTNAccount() {
        onUserSwitched(UserHandle.CURRENT);

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)).isTrue();
        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_CALL_PROVIDER)).isTrue();
        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)).isTrue();
    }

    @Test
    public void onLocaleChanged_withPSTNAccountDisabled_shouldRegisterEmergencyOnlyAccount() {
        when(mResources.getBoolean(R.bool.config_pstn_phone_accounts_enabled)).thenReturn(false);
        when(mResources.getBoolean(
                R.bool.config_emergency_account_emergency_calls_only)).thenReturn(true);
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY)).isTrue();
    }

    @Test
    public void onLocaleChanged_withSubVoiceCapable_shouldNotRegisterEmergencyOnlyAccount() {
        overrideSubscriptionServiceCapabilities(
                new int[]{SubscriptionManager.SERVICE_CAPABILITY_VOICE});
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY)).isFalse();
    }

    @Test
    public void onLocaleChanged_withSubNotVoiceCapable_shouldRegisterEmergencyOnlyAccount() {
        overrideSubscriptionServiceCapabilities(
                new int[]{SubscriptionManager.SERVICE_CAPABILITY_DATA});
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY)).isTrue();
    }

    @Test
    public void lowBatteryAlert_validConfig_extrasSet() {
        // This test assumes Flags.supportLowBatteryAlert() is true.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_INTERVAL_INT, 30);
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_THRESHOLD_INT, 15);
        overrideSubscriptionServiceCapabilities(bundle);

        // Trigger account registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();
        assertThat(phoneAccount.getExtras()).isNotNull();
        assertThat(phoneAccount.getExtras().getInt(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_INTERVAL_SECONDS)).isEqualTo(30);
        assertThat(phoneAccount.getExtras().getInt(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_LEVEL_THRESHOLD)).isEqualTo(15);
    }

    @Test
    public void lowBatteryAlert_intervalDisabled_extrasNotSet() {
        // This test assumes Flags.supportLowBatteryAlert() is true.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_INTERVAL_INT,
                PhoneAccount.LOW_BATTERY_ALERT_DISABLED);
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_THRESHOLD_INT, 15);
        overrideSubscriptionServiceCapabilities(bundle);

        // Trigger account registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();
        assertThat(phoneAccount.getExtras()).isNotNull();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_INTERVAL_SECONDS)).isFalse();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_LEVEL_THRESHOLD)).isFalse();
    }

    @Test
    public void lowBatteryAlert_thresholdDisabled_extrasNotSet() {
        // This test assumes Flags.supportLowBatteryAlert() is true.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_INTERVAL_INT, 30);
        bundle.putInt(CarrierConfigManager.KEY_LOW_BATTERY_ALERT_THRESHOLD_INT,
                PhoneAccount.LOW_BATTERY_ALERT_DISABLED);
        overrideSubscriptionServiceCapabilities(bundle);

        // Trigger account registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();
        assertThat(phoneAccount.getExtras()).isNotNull();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_INTERVAL_SECONDS)).isFalse();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_LEVEL_THRESHOLD)).isFalse();
    }

    @Test
    public void lowBatteryAlert_configMissing_extrasNotSet() {
        // This test assumes Flags.supportLowBatteryAlert() is true.
        // When config is not set, getInt should return the default value, which is
        // LOW_BATTERY_ALERT_DISABLED.
        PersistableBundle bundle = new PersistableBundle();
        overrideSubscriptionServiceCapabilities(bundle);

        // Trigger account registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();
        assertThat(phoneAccount.getExtras()).isNotNull();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_INTERVAL_SECONDS)).isFalse();
        assertThat(phoneAccount.getExtras().containsKey(
                PhoneAccount.EXTRA_LOW_BATTERY_ALERT_LEVEL_THRESHOLD)).isFalse();
    }

    @Test
    public void testSkipPrivateNetworkSubscriptions() {
        SubscriptionInfo subInfo1 = Mockito.mock(SubscriptionInfo.class);
        when(subInfo1.isPrivateNetwork()).thenReturn(true);
        when(mSubscriptionManager.getActiveSubscriptionInfo(TEST_SUB_ID)).thenReturn(subInfo1);
        SubscriptionInfo subInfo2 = Mockito.mock(SubscriptionInfo.class);
        when(subInfo2.isPrivateNetwork()).thenReturn(false);
        when(mSubscriptionManager.getActiveSubscriptionInfo(TEST_SUB_ID_2)).thenReturn(subInfo2);

        // Trigger account update
        onLocaleChanged();

        ArgumentCaptor<PhoneAccount> phoneAccountArgumentCaptor =
                ArgumentCaptor.forClass(PhoneAccount.class);
        verify(mTelecomManager, timeout(TIMEOUT).atLeastOnce()).registerPhoneAccount(
                phoneAccountArgumentCaptor.capture());
        assertThat(phoneAccountArgumentCaptor.getAllValues().size()).isEqualTo(1);
    }

    @Test
    public void testAdhocConferenceCapability_withActiveConference_shouldDisableCapability() {
        // Setup Adhoc config enabled
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL, true);
        overrideSubscriptionServiceCapabilities(bundle);

        // Setup IMS registered
        when(mPhone.isImsRegistered()).thenReturn(true);

        // Mock TelephonyConnectionService saying active conference
        TelephonyConnectionService mockConnectionService = Mockito.mock(
                TelephonyConnectionService.class);
        when(mockConnectionService.isConferenceActive(
                nullable(PhoneAccountHandle.class))).thenReturn(true);
        mTelecomAccountRegistry.setTelephonyConnectionService(mockConnectionService);

        // Trigger registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING)).isFalse();
    }

    @Test
    public void testAdhocConferenceCapability_withoutActiveConference_shouldEnableCapability() {
        // Setup Adhoc config enabled
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL, true);
        overrideSubscriptionServiceCapabilities(bundle);

        // Setup IMS registered
        when(mPhone.isImsRegistered()).thenReturn(true);

        // Mock TelephonyConnectionService saying NO active conference
        TelephonyConnectionService mockConnectionService = Mockito.mock(
                TelephonyConnectionService.class);
        when(mockConnectionService.isConferenceActive(
                nullable(PhoneAccountHandle.class))).thenReturn(false);
        mTelecomAccountRegistry.setTelephonyConnectionService(mockConnectionService);

        // Trigger registration
        onLocaleChanged();

        PhoneAccount phoneAccount = verifyAndCaptureRegisteredPhoneAccount();

        assertThat(phoneAccount.hasCapabilities(
                PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING)).isTrue();
    }

    private PhoneAccount verifyAndCaptureRegisteredPhoneAccount() {
        ArgumentCaptor<PhoneAccount> phoneAccountArgumentCaptor =
                ArgumentCaptor.forClass(PhoneAccount.class);
        verify(mTelecomManager, timeout(TIMEOUT).atLeastOnce()).registerPhoneAccount(
                phoneAccountArgumentCaptor.capture());
        return phoneAccountArgumentCaptor.getValue();
    }

    private void onUserSwitched(UserHandle userHandle) {
        Intent intent = new Intent(Intent.ACTION_USER_SWITCHED);
        intent.putExtra(Intent.EXTRA_USER, userHandle);
        mUserSwitchedAndConfigChangedReceiver.onReceive(mMockedContext, intent);
        mTestableLooper.processAllMessages();
    }

    private void onCarrierConfigChanged(int subId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        mUserSwitchedAndConfigChangedReceiver.onReceive(mMockedContext, intent);
        mTestableLooper.processAllMessages();

    }

    private void onAddSubscriptionListenerFailed() {
        mOnSubscriptionsChangedListener.onAddListenerFailed();
    }

    private void onServiceStateChanged(ServiceState serviceState) {
        if (mTelephonyCallback instanceof TelephonyCallback.ServiceStateListener) {
            TelephonyCallback.ServiceStateListener listener =
                    (TelephonyCallback.ServiceStateListener) mTelephonyCallback;
            listener.onServiceStateChanged(serviceState);
        }
    }

    private void onActiveDataSubscriptionIdChanged(int subId) {
        if (mTelephonyCallback instanceof TelephonyCallback.ActiveDataSubscriptionIdListener) {
            TelephonyCallback.ActiveDataSubscriptionIdListener listener =
                    (TelephonyCallback.ActiveDataSubscriptionIdListener) mTelephonyCallback;
            listener.onActiveDataSubscriptionIdChanged(subId);
        }
    }

    private void onLocaleChanged() {
        Intent intent = new Intent(Intent.ACTION_LOCALE_CHANGED);
        mLocaleChangedBroadcastReceiver.onReceive(mMockedContext, intent);
        mTestableLooper.processAllMessages();
    }

    private void onNetworkCountryChanged() {
        Intent intent = new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED);
        mLocaleChangedBroadcastReceiver.onReceive(mMockedContext, intent);
    }

    private void overrideSubscriptionServiceCapabilities(int[] capabilities) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY,
                capabilities);

        when(mPhoneGlobals.getCarrierConfigForSubId(anyInt())).thenReturn(bundle);
        mTestableLooper.processAllMessages();
    }

    private void overrideSubscriptionServiceCapabilities(PersistableBundle bundle) {
        if (bundle.getIntArray(
                CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY) == null) {
            bundle.putIntArray(CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY,
                    new int[]{SubscriptionManager.SERVICE_CAPABILITY_VOICE});
        }
        when(mPhoneGlobals.getCarrierConfigForSubId(anyInt())).thenReturn(bundle);
        mTestableLooper.processAllMessages();
    }
}
