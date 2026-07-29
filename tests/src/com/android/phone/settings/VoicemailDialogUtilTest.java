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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.PersistableBundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class VoicemailDialogUtilTest extends TelephonyTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Mockito.doReturn(0).when(mPhone).getPhoneId();
        Mockito.doReturn(1).when(mPhone).getSubId();
        PersistableBundle b = new PersistableBundle();
        Mockito.doReturn(b).when(mPhoneGlobals).getCarrierConfig();
        Mockito.doReturn(b).when(mPhoneGlobals).getCarrierConfigForSubId(Mockito.anyInt());
    }

    @Test
    public void testGetDialog_All() {
        try (ActivityScenario<VoicemailSettingsActivity> scenario =
                     ActivityScenario.launch(VoicemailSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                int[] dialogIds = {
                    VoicemailDialogUtil.VM_RESPONSE_ERROR_DIALOG,
                    VoicemailDialogUtil.VM_CONFIRM_DIALOG,
                    VoicemailDialogUtil.VM_NOCHANGE_ERROR_DIALOG,
                    VoicemailDialogUtil.FWD_SET_RESPONSE_ERROR_DIALOG,
                    VoicemailDialogUtil.FWD_GET_RESPONSE_ERROR_DIALOG,
                    VoicemailDialogUtil.TTY_SET_RESPONSE_ERROR
                };
                for (int id : dialogIds) {
                    Dialog dialog = VoicemailDialogUtil.getDialog(activity, id);
                    assertNotNull("Dialog should not be null for ID: " + id, dialog);
                    assertTrue("Dialog should be AlertDialog for ID: " + id,
                            dialog instanceof AlertDialog);
                }

                int[] progressDialogIds = {
                    VoicemailDialogUtil.VM_FWD_SAVING_DIALOG,
                    VoicemailDialogUtil.VM_FWD_READING_DIALOG,
                    VoicemailDialogUtil.VM_REVERTING_DIALOG
                };
                for (int id : progressDialogIds) {
                    Dialog dialog = VoicemailDialogUtil.getDialog(activity, id);
                    assertNotNull("Dialog should not be null for ID: " + id, dialog);
                    assertTrue("Dialog should be ProgressDialog for ID: " + id,
                            dialog instanceof ProgressDialog);
                }

                assertNull(VoicemailDialogUtil.getDialog(activity, -1));
            });
        }
    }
}

