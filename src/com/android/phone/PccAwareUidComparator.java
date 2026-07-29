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

import android.app.privatecompute.flags.Flags;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

/**
 * Utility class for PCC-aware UID comparisons.
 */
public class PccAwareUidComparator {

    /**
     * Checks if two UIDs belong to the same app, considering PCC (Private Compute Core) UIDs.
     * If a UID is a PCC UID, it is mapped back to its original app UID before comparison.
     *
     * @param pm PackageManager instance to use for mapping PCC UIDs.
     * @param uid1 First UID to compare.
     * @param uid2 Second UID to compare.
     * @return true if both UIDs belong to the same app.
     */
    public static boolean isSameApp(PackageManager pm, int uid1, int uid2) {
        boolean isPccEnabled = Flags.enablePccFrameworkSupport();
        int appUid1 = uid1;
        if (isPccEnabled && Process.isPrivateComputeCoreUid(uid1)) {
            appUid1 = pm.getAppUidForPrivateComputeCoreUid(uid1);
        }
        int appUid2 = uid2;
        if (isPccEnabled && Process.isPrivateComputeCoreUid(uid2)) {
            appUid2 = pm.getAppUidForPrivateComputeCoreUid(uid2);
        }
        return UserHandle.isSameApp(appUid1, appUid2);
    }
}
