/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.phone.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.PersistableBundle;
import android.telephony.TelephonyManager;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.phone.GsmUmtsCallOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class SuppServicesUiUtilTest extends TelephonyTestBase {

    private TelephonyManager mTelephonyManager;

    private static final int SUB_ID = 1;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        Mockito.doReturn(SUB_ID).when(mPhone).getSubId();
        Mockito.doReturn(0).when(mPhone).getPhoneId();

        PersistableBundle b = new PersistableBundle();
        Mockito.doReturn(b).when(mPhoneGlobals).getCarrierConfig();
        Mockito.doReturn(b).when(mPhoneGlobals).getCarrierConfigForSubId(Mockito.anyInt());
    }

    @Test
    public void testIsSsOverUtPrecautions_MobileDataOff() {
        when(mTelephonyManager.getDataEnabled(SUB_ID)).thenReturn(false);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(false);

        assertTrue(SuppServicesUiUtil.isSsOverUtPrecautions(mContext, mPhone));
    }

    @Test
    public void testIsSsOverUtPrecautions_RoamingDataOff() {
        when(mTelephonyManager.getDataEnabled(SUB_ID)).thenReturn(true);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(true);
        when(mPhone.getDataRoamingEnabled()).thenReturn(false);

        assertTrue(SuppServicesUiUtil.isSsOverUtPrecautions(mContext, mPhone));
    }

    @Test
    public void testIsSsOverUtPrecautions_AllOn() {
        when(mTelephonyManager.getDataEnabled(SUB_ID)).thenReturn(true);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(false);

        assertFalse(SuppServicesUiUtil.isSsOverUtPrecautions(mContext, mPhone));
    }

    @Test
    public void testMakeMessage_SingleSim_NotRoaming() {
        when(mTelephonyManager.getSimCount()).thenReturn(1);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(false);

        String message = SuppServicesUiUtil.makeMessage(
                mContext, GsmUmtsCallOptions.CALL_FORWARDING_KEY, mPhone);
        assertNotNull(message);
        assertTrue(message.contains("mobile data"));
        assertFalse(message.contains("data roaming"));
    }

    @Test
    public void testMakeMessage_SingleSim_Roaming() {
        when(mTelephonyManager.getSimCount()).thenReturn(1);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(true);

        String message = SuppServicesUiUtil.makeMessage(
                mContext, GsmUmtsCallOptions.CALL_FORWARDING_KEY, mPhone);
        assertNotNull(message);
        assertTrue(message.contains("mobile data"));
        assertTrue(message.contains("data roaming"));
    }

    @Test
    public void testMakeMessage_MultiSim_NotRoaming() {
        when(mTelephonyManager.getSimCount()).thenReturn(2);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(false);
        when(mPhone.getPhoneId()).thenReturn(0);

        String message = SuppServicesUiUtil.makeMessage(
                mContext, GsmUmtsCallOptions.CALL_FORWARDING_KEY, mPhone);
        assertNotNull(message);
        assertTrue(message.contains("mobile data"));
        assertTrue(message.contains("SIM 1"));
    }

    @Test
    public void testMakeMessage_MultiSim_Roaming() {
        when(mTelephonyManager.getSimCount()).thenReturn(2);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(true);
        when(mPhone.getPhoneId()).thenReturn(1);

        String message = SuppServicesUiUtil.makeMessage(
                mContext, GsmUmtsCallOptions.CALL_FORWARDING_KEY, mPhone);
        assertNotNull(message);
        assertTrue(message.contains("mobile data"));
        assertTrue(message.contains("data roaming"));
        assertTrue(message.contains("SIM 2"));
    }

    @Test
    public void testShowBlockingSuppServicesDialog() {
        when(mTelephonyManager.getSimCount()).thenReturn(1);
        when(mTelephonyManager.isNetworkRoaming(SUB_ID)).thenReturn(false);

        try (ActivityScenario<VoicemailSettingsActivity> scenario =
                     ActivityScenario.launch(VoicemailSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                Dialog dialog = SuppServicesUiUtil.showBlockingSuppServicesDialog(
                        activity, mPhone, GsmUmtsCallOptions.CALL_FORWARDING_KEY);
                assertNotNull(dialog);
                assertTrue(dialog instanceof AlertDialog);

                AlertDialog alertDialog = (AlertDialog) dialog;
                // show() is required to inflate the layout and find views by ID
                alertDialog.show();
                TextView messageView = alertDialog.findViewById(android.R.id.message);
                assertNotNull(messageView);
                assertTrue(messageView.getText().toString().contains("mobile data"));
                alertDialog.dismiss();
            });
        }
    }
}
