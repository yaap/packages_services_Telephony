/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.phone;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class PhoneGlobalsTest extends TelephonyTestBase {

    private static final int TEST_SUB_ID = 1;
    private static final String TEST_OPERATOR_NUMERIC = "123456";

    private PhoneGlobals mPhoneGlobalsInstance;
    @Mock private ServiceState mServiceState;
    @Mock private NotificationMgr mNotificationMgr;
    @Mock private Handler mHandler;
    @Mock private CarrierConfigLoader mConfigLoader;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mPhoneGlobalsInstance = new PhoneGlobals(mContext);
        mPhoneGlobalsInstance.notificationMgr = mNotificationMgr;
        mPhoneGlobalsInstance.mHandler = mHandler;
        mPhoneGlobalsInstance.configLoader = mConfigLoader;

        mPhoneGlobalsInstance.mDefaultDataSubId = TEST_SUB_ID;
        when(mConfigLoader.getConfigForSubIdWithFeature(anyInt(), any(), any()))
                .thenReturn(new PersistableBundle());
        when(mServiceState.getOperatorNumeric()).thenReturn(TEST_OPERATOR_NUMERIC);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testUpdateDataRoamingStatus_RoamingConnected() throws Exception {
        // Roaming is on, data is allowed
        when(mServiceState.getDataRoaming()).thenReturn(true);
        List<DataDisallowedReason> disallowReasons = new ArrayList<>();

        // Enable connected roaming notification in carrier config
        PersistableBundle config = new PersistableBundle();
        config.putBoolean(
                CarrierConfigManager.KEY_SHOW_DATA_CONNECTED_ROAMING_NOTIFICATION_BOOL, true);
        when(mConfigLoader.getConfigForSubIdWithFeature(anyInt(), any(), any())).thenReturn(config);

        mPhoneGlobalsInstance.updateDataRoamingStatus(
                PhoneGlobals.ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED,
                disallowReasons,
                mServiceState);

        // Verify that ROAMING_NOTIFICATION_CONNECTED is set
        assertEquals(
                PhoneGlobals.ROAMING_NOTIFICATION_CONNECTED,
                mPhoneGlobalsInstance.getCurrentRoamingNotification());
    }

    @Test
    public void testUpdateDataRoamingStatus_RoamingConnected_ExcludedMcc() throws Exception {
        // Roaming is on, data is allowed
        when(mServiceState.getDataRoaming()).thenReturn(true);
        // MCC 311 is excluded
        when(mServiceState.getOperatorNumeric()).thenReturn("311580");
        List<DataDisallowedReason> disallowReasons = new ArrayList<>();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(
                CarrierConfigManager.KEY_SHOW_DATA_CONNECTED_ROAMING_NOTIFICATION_BOOL, true);
        config.putStringArray(
                CarrierConfigManager
                        .KEY_DATA_CONNECTED_ROAMING_NOTIFICATION_EXCLUDED_MCCS_STRING_ARRAY,
                new String[] {"311"});
        when(mConfigLoader.getConfigForSubIdWithFeature(anyInt(), any(), any())).thenReturn(config);

        mPhoneGlobalsInstance.updateDataRoamingStatus(
                PhoneGlobals.ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED,
                disallowReasons,
                mServiceState);

        // Verify that no notification is set
        assertEquals(
                PhoneGlobals.ROAMING_NOTIFICATION_NO_NOTIFICATION,
                mPhoneGlobalsInstance.getCurrentRoamingNotification());
    }

    @Test
    public void testUpdateDataRoamingStatus_RoamingDisconnected() throws Exception {
        // Roaming is on, but data is disallowed due to ROAMING_DISABLED
        when(mServiceState.getDataRoaming()).thenReturn(true);
        List<DataDisallowedReason> disallowReasons = new ArrayList<>();
        disallowReasons.add(DataDisallowedReason.ROAMING_DISABLED);

        mPhoneGlobalsInstance.updateDataRoamingStatus(
                PhoneGlobals.ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED,
                disallowReasons,
                mServiceState);

        // Verify that ROAMING_NOTIFICATION_DISCONNECTED is set
        assertEquals(
                PhoneGlobals.ROAMING_NOTIFICATION_DISCONNECTED,
                mPhoneGlobalsInstance.getCurrentRoamingNotification());
    }

    @Test
    public void testGetImsResources() throws Exception {
        // Do not use test context here, we are testing that overlaying for different locales works
        // correctly
        Context realContext = InstrumentationRegistry.getTargetContext();
        String defaultImsMmtelPackage =
                getResourcesForLocale(realContext, Locale.US)
                        .getString(R.string.config_ims_mmtel_package);
        String defaultImsMmtelPackageUk =
                getResourcesForLocale(realContext, Locale.UK)
                        .getString(R.string.config_ims_mmtel_package);
        assertEquals(
                "locales changed IMS package configuration!",
                defaultImsMmtelPackage,
                defaultImsMmtelPackageUk);
    }

    private Resources getResourcesForLocale(Context context, Locale locale) {
        Configuration config = new Configuration();
        config.setToDefaults();
        config.setLocale(locale);
        Context localeContext = context.createConfigurationContext(config);
        return localeContext.getResources();
    }
}
