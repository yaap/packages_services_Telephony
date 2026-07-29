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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CallForwardInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class VoicemailProviderSettingsUtilTest {

    private static final String TEST_KEY = "test_provider";
    private static final String TEST_VM_NUMBER = "1234567890";
    private static final String TEST_FWD_NUMBER = "0987654321";
    private static final int TEST_TIME = 20;

    @Mock
    private Context mContext;
    @Mock
    private SharedPreferences mSharedPreferences;
    @Mock
    private SharedPreferences.Editor mEditor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putString(any(), any())).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
    }

    @Test
    public void testSave() {
        when(mSharedPreferences.getString(eq(TEST_KEY + "#VMNumber"), any()))
                .thenReturn(null);

        VoicemailProviderSettings settings = new VoicemailProviderSettings(
                TEST_VM_NUMBER, TEST_FWD_NUMBER, TEST_TIME);

        VoicemailProviderSettingsUtil.save(mContext, TEST_KEY, settings);

        verify(mEditor).putString(eq(TEST_KEY + "#VMNumber"), eq(TEST_VM_NUMBER));
        verify(mEditor).putInt(eq(TEST_KEY + "#FWDSettings#Length"), eq(4));
        verify(mEditor, atLeastOnce()).putInt(contains("#Status"), anyInt());
        verify(mEditor, atLeastOnce()).putInt(contains("#Reason"), anyInt());
        verify(mEditor, atLeastOnce()).putString(contains("#Number"), eq(TEST_FWD_NUMBER));
        verify(mEditor, atLeastOnce()).putInt(contains("#Time"), eq(TEST_TIME));
        verify(mEditor, atLeastOnce()).apply();
    }

    @Test
    public void testSave_NoChange() {
        when(mSharedPreferences.getString(eq(TEST_KEY + "#VMNumber"), any()))
                .thenReturn(TEST_VM_NUMBER);
        when(mSharedPreferences.getInt(eq(TEST_KEY + "#FWDSettings#Length"), anyInt()))
                .thenReturn(0);

        VoicemailProviderSettings settings = new VoicemailProviderSettings(
                TEST_VM_NUMBER, (CallForwardInfo[]) null);

        VoicemailProviderSettingsUtil.save(mContext, TEST_KEY, settings);

        verify(mEditor, never()).apply();
    }

    @Test
    public void testLoad() {
        when(mSharedPreferences.getString(eq(TEST_KEY + "#VMNumber"), any()))
                .thenReturn(TEST_VM_NUMBER);
        when(mSharedPreferences.getInt(eq(TEST_KEY + "#FWDSettings#Length"), anyInt()))
                .thenReturn(1);
        when(mSharedPreferences.getInt(eq(TEST_KEY + "#FWDSettings#Setting0#Status"), anyInt()))
                .thenReturn(1);
        when(mSharedPreferences.getInt(eq(TEST_KEY + "#FWDSettings#Setting0#Reason"), anyInt()))
                .thenReturn(3);
        when(mSharedPreferences.getString(eq(TEST_KEY + "#FWDSettings#Setting0#Number"), any()))
                .thenReturn(TEST_FWD_NUMBER);
        when(mSharedPreferences.getInt(eq(TEST_KEY + "#FWDSettings#Setting0#Time"), anyInt()))
                .thenReturn(TEST_TIME);

        VoicemailProviderSettings settings = VoicemailProviderSettingsUtil.load(mContext, TEST_KEY);

        assertNotNull(settings);
        assertEquals(TEST_VM_NUMBER, settings.getVoicemailNumber());
        CallForwardInfo[] cfi = settings.getForwardingSettings();
        assertNotNull(cfi);
        assertEquals(1, cfi.length);
        assertEquals(1, cfi[0].status);
        assertEquals(3, cfi[0].reason);
        assertEquals(TEST_FWD_NUMBER, cfi[0].number);
        assertEquals(TEST_TIME, cfi[0].timeSeconds);
    }

    @Test
    public void testLoad_NoSettings() {
        when(mSharedPreferences.getString(eq(TEST_KEY + "#VMNumber"), any()))
                .thenReturn(null);

        VoicemailProviderSettings settings = VoicemailProviderSettingsUtil.load(mContext, TEST_KEY);

        assertNull(settings);
    }

    @Test
    public void testDelete() {
        VoicemailProviderSettingsUtil.delete(mContext, TEST_KEY);

        verify(mEditor).putString(eq(TEST_KEY + "#VMNumber"), eq(null));
        verify(mEditor).putInt(eq(TEST_KEY + "#FWDSettings#Length"), eq(0));
        verify(mEditor).apply();
    }

    @Test
    public void testDelete_EmptyKey() {
        VoicemailProviderSettingsUtil.delete(mContext, "");
        verify(mSharedPreferences, never()).edit();
    }

    @Test
    public void testDelete_NullKey() {
        VoicemailProviderSettingsUtil.delete(mContext, null);
        verify(mSharedPreferences, never()).edit();
    }

    @Test
    public void testSave_EmptyKey() {
        VoicemailProviderSettings settings = new VoicemailProviderSettings(
                TEST_VM_NUMBER, (CallForwardInfo[]) null);
        VoicemailProviderSettingsUtil.save(mContext, "", settings);
        verify(mEditor).putString(eq("#VMNumber"), eq(TEST_VM_NUMBER));
    }
}
