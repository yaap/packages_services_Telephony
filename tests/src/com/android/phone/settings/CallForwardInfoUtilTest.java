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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.os.Message;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CallForwardInfoUtilTest {

    @Mock
    private Phone mPhone;
    private Message mMessage;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mMessage = new Message();
    }

    @Test
    public void testInfoForReason() {
        CallForwardInfo fi1 = new CallForwardInfo();
        fi1.reason = CommandsInterface.CF_REASON_BUSY;
        CallForwardInfo fi2 = new CallForwardInfo();
        fi2.reason = CommandsInterface.CF_REASON_NO_REPLY;
        CallForwardInfo[] infos = new CallForwardInfo[] {fi1, fi2};

        assertEquals(fi1, CallForwardInfoUtil.infoForReason(
                infos, CommandsInterface.CF_REASON_BUSY));
        assertEquals(fi2, CallForwardInfoUtil.infoForReason(
                infos, CommandsInterface.CF_REASON_NO_REPLY));
        assertNull(CallForwardInfoUtil.infoForReason(
                infos, CommandsInterface.CF_REASON_UNCONDITIONAL));
        assertNull(CallForwardInfoUtil.infoForReason(null, CommandsInterface.CF_REASON_BUSY));
    }

    @Test
    public void testIsUpdateRequired_bothInactive() {
        CallForwardInfo oldInfo = new CallForwardInfo();
        CallForwardInfo newInfo = new CallForwardInfo();
        oldInfo.status = 0;
        newInfo.status = 0;
        assertFalse(CallForwardInfoUtil.isUpdateRequired(oldInfo, newInfo));
    }

    @Test
    public void testIsUpdateRequired_oldInfoNull() {
        CallForwardInfo newInfo = new CallForwardInfo();
        assertTrue(CallForwardInfoUtil.isUpdateRequired(null, newInfo));
    }

    @Test
    public void testIsUpdateRequired_statusChanged() {
        CallForwardInfo oldInfo = new CallForwardInfo();
        CallForwardInfo newInfo = new CallForwardInfo();

        // Inactive to Active
        oldInfo.status = 0;
        newInfo.status = 1;
        assertTrue(CallForwardInfoUtil.isUpdateRequired(oldInfo, newInfo));

        // Active to Inactive
        oldInfo.status = 1;
        newInfo.status = 0;
        assertTrue(CallForwardInfoUtil.isUpdateRequired(oldInfo, newInfo));
    }

    @Test
    public void testIsUpdateRequired_bothActive() {
        CallForwardInfo oldInfo = new CallForwardInfo();
        CallForwardInfo newInfo = new CallForwardInfo();

        oldInfo.status = 1;
        newInfo.status = 1;
        newInfo.number = "123";
        oldInfo.number = "123";
        // Matches original behavior where updates are required even if identical
        assertTrue(CallForwardInfoUtil.isUpdateRequired(oldInfo, newInfo));
    }

    @Test
    public void testSetCallForwardingOption_Active() {
        CallForwardInfo fi = new CallForwardInfo();
        fi.status = 1;
        fi.reason = CommandsInterface.CF_REASON_BUSY;
        fi.number = "123456";
        fi.serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;
        fi.timeSeconds = 20;

        CallForwardInfoUtil.setCallForwardingOption(mPhone, fi, mMessage);

        verify(mPhone).setCallForwardingOption(
                eq(CommandsInterface.CF_ACTION_REGISTRATION),
                eq(fi.reason),
                eq(fi.number),
                eq(fi.serviceClass),
                eq(fi.timeSeconds),
                eq(mMessage));
    }

    @Test
    public void testSetCallForwardingOption_Inactive() {
        CallForwardInfo fi = new CallForwardInfo();
        fi.status = 0;
        fi.reason = CommandsInterface.CF_REASON_BUSY;
        fi.number = "123456";
        fi.serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;
        fi.timeSeconds = 20;

        CallForwardInfoUtil.setCallForwardingOption(mPhone, fi, mMessage);

        verify(mPhone).setCallForwardingOption(
                eq(CommandsInterface.CF_ACTION_DISABLE),
                eq(fi.reason),
                eq(fi.number),
                eq(fi.serviceClass),
                eq(fi.timeSeconds),
                eq(mMessage));
    }

    @Test
    public void testGetCallForwardInfo_FromList() {
        CallForwardInfo fi = new CallForwardInfo();
        fi.serviceClass = CommandsInterface.SERVICE_CLASS_VOICE;
        fi.number = "123";
        CallForwardInfo[] infos = new CallForwardInfo[] {fi};

        CallForwardInfo result = CallForwardInfoUtil.getCallForwardInfo(
                infos, CommandsInterface.CF_REASON_BUSY);
        assertEquals(fi, result);
    }

    @Test
    public void testGetCallForwardInfo_Default() {
        CallForwardInfo result = CallForwardInfoUtil.getCallForwardInfo(
                null, CommandsInterface.CF_REASON_BUSY);
        assertNotNull(result);
        assertEquals(0, result.status);
        assertEquals(CommandsInterface.CF_REASON_BUSY, result.reason);
        assertEquals(CommandsInterface.SERVICE_CLASS_VOICE, result.serviceClass);
    }
}

