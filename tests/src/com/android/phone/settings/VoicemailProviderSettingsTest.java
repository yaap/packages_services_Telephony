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

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CallForwardInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoicemailProviderSettingsTest {

    private static final String VM_NUMBER = "123456";
    private static final String FWD_NUMBER = "654321";
    private static final int TIME = 20;

    @Test
    public void testConstructor_WithForwarding() {
        VoicemailProviderSettings settings = new VoicemailProviderSettings(
                VM_NUMBER, FWD_NUMBER, TIME);
        assertEquals(VM_NUMBER, settings.getVoicemailNumber());
        CallForwardInfo[] fwd = settings.getForwardingSettings();
        assertNotNull(fwd);
        assertEquals(4, fwd.length);
        for (CallForwardInfo fi : fwd) {
            assertEquals(FWD_NUMBER, fi.number);
            assertEquals(TIME, fi.timeSeconds);
        }
    }

    @Test
    public void testConstructor_NoForwarding() {
        VoicemailProviderSettings settings = new VoicemailProviderSettings(VM_NUMBER, null, TIME);
        assertEquals(VM_NUMBER, settings.getVoicemailNumber());
        assertNull(settings.getForwardingSettings());

        settings = new VoicemailProviderSettings(VM_NUMBER, "", TIME);
        assertNull(settings.getForwardingSettings());
    }

    @Test
    public void testEquals() {
        VoicemailProviderSettings s1 = new VoicemailProviderSettings(VM_NUMBER, FWD_NUMBER, TIME);
        VoicemailProviderSettings s2 = new VoicemailProviderSettings(VM_NUMBER, FWD_NUMBER, TIME);
        VoicemailProviderSettings s3 = new VoicemailProviderSettings("different", FWD_NUMBER, TIME);

        assertTrue(s1.equals(s2));
        assertFalse(s1.equals(s3));
        assertFalse(s1.equals(null));
        assertFalse(s1.equals("not a settings object"));
    }

    @Test
    public void testToString() {
        VoicemailProviderSettings settings = new VoicemailProviderSettings(
                VM_NUMBER, (CallForwardInfo[]) null);
        assertEquals(VM_NUMBER, settings.toString());
    }
}

