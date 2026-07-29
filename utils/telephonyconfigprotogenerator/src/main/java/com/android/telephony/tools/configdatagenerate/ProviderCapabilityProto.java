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

package com.android.telephony.tools.configdatagenerate;

/**
 * Represents the detailed satellite settings and capabilities per provider PLMN.
 *
 * Some of these fields are mapped from: KEY_SATELLITE_CONFIGS_PER_PLMN_BUNDLE.
 */
public class ProviderCapabilityProto {

    /** The satellite provider's PLMN identifier. */
    public String mPlmn;

    /** Array of allowed services (e.g., SMS, MMS) for this satellite provider. */
    public int[] mAllowedServices;

    /**
     * Satellite connection method.
     * 0: Automatic, 1: Manual, 2: Hybrid.
     * Mapped from: KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT.
     */
    public Integer mNtnConnectType;

    /**
     * Constructor for ProviderCapabilityProto
     */
    public ProviderCapabilityProto(String plmn, int[] allowedServices, Integer ntnConnectType) {
        mPlmn = plmn;
        mAllowedServices = allowedServices;
        mNtnConnectType = ntnConnectType;
    }
}
