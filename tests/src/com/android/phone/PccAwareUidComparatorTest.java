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

package com.android.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.privatecompute.flags.Flags;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class PccAwareUidComparatorTest {

    @Mock
    private PackageManager mPackageManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int APP_UID_1 = 10001;
    private static final int APP_UID_2 = 10002;
    private static final int PCC_UID_1 = 30001;
    private static final int PCC_UID_2 = 30002;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testIsSameApp_regularUids_sameApp() {
        assertTrue(PccAwareUidComparator.isSameApp(mPackageManager, APP_UID_1, APP_UID_1));
    }

    @Test
    public void testIsSameApp_regularUids_differentApp() {
        assertFalse(PccAwareUidComparator.isSameApp(mPackageManager, APP_UID_1, APP_UID_2));
    }

    @Test
    public void testIsSameApp_pccUidAndAppUid_sameApp() {
        // We can't easily mock static Flags.enablePccFrameworkSupport() without extra setup
        // But we can check if it works when enabled.
        if (Flags.enablePccFrameworkSupport()) {
            when(mPackageManager.getAppUidForPrivateComputeCoreUid(PCC_UID_1))
                    .thenReturn(APP_UID_1);
            assertTrue(PccAwareUidComparator.isSameApp(mPackageManager, PCC_UID_1, APP_UID_1));
        }
    }

    @Test
    public void testIsSameApp_pccUidAndAppUid_differentApp() {
        if (Flags.enablePccFrameworkSupport()) {
            when(mPackageManager.getAppUidForPrivateComputeCoreUid(PCC_UID_1))
                    .thenReturn(APP_UID_1);
            assertFalse(PccAwareUidComparator.isSameApp(mPackageManager, PCC_UID_1, APP_UID_2));
        }
    }

    @Test
    public void testIsSameApp_twoPccUids_sameApp() {
        if (Flags.enablePccFrameworkSupport()) {
            when(mPackageManager.getAppUidForPrivateComputeCoreUid(PCC_UID_1))
                    .thenReturn(APP_UID_1);
            when(mPackageManager.getAppUidForPrivateComputeCoreUid(PCC_UID_2))
                    .thenReturn(APP_UID_1);
            assertTrue(PccAwareUidComparator.isSameApp(mPackageManager, PCC_UID_1, PCC_UID_2));
        }
    }
}
