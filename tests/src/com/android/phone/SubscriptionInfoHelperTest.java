/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import org.junit.Test;

public class SubscriptionInfoHelperTest {
    private static final String EXTRA_SUBSCRIPTION_LABEL =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

    /**
     * Ensures {@link SubscriptionInfoHelper#addExtrasToIntent(Intent, SubscriptionInfo)} can
     * properly handle a null display name without crashing.
     */
    @Test
    public void testAddExtrasToIntentWithNullDisplayName() {
        Intent intent = new Intent();
        SubscriptionInfo info = new SubscriptionInfo.Builder()
                .setId(1)
                .setIccId("90210")
                .setSimSlotIndex(1)
                .setDisplayName(null)
                .setCarrierName(null)
                .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_CARRIER_ID)
                .setIconTint(0)
                .setNumber("555-1212")
                .setDataRoaming(0)
                .setIcon(null)
                .setMcc("401")
                .setMnc("384")
                .setCountryIso("us")
                .setEmbedded(false)
                .setNativeAccessRules(null)
                .setCardString("")
                .build();
        SubscriptionInfoHelper.addExtrasToIntent(intent, info);
        assertTrue(intent.hasExtra(EXTRA_SUBSCRIPTION_LABEL));
        assertTrue(TextUtils.isEmpty(intent.getStringExtra(EXTRA_SUBSCRIPTION_LABEL)));
    }
}
