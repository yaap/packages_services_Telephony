/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.util.List;

/**
 * Satellite Roaming Config Proto.
 *
 * Represents the device-specific carrier roaming configuration. Container for all device-level
 * satellite-related configurations that can be dynamically updated. These settings apply
 * globally to the device, regardless of the active carrier.
 */
public class RoamingConfigProto {

    /** The maximum allowed data mode for satellite roaming. */
    public Integer mMaxAllowedDataMode;

    /** List of satellite PLMNs supported by the device. */
    public List<String> mDeviceSatellitePlmns;

    /**
     * Determines whether to forcefully override the roaming mode of Wi-Fi Calling
     * when connected to NTN.
     */
    public Boolean mOverrideWfcRoamingModeWhileUsingNtn;

    /**
     * Constructor for RoamingConfigProto
     */
    public RoamingConfigProto(Integer maxAllowedDataMode, List<String> deviceSatellitePlmns,
            Boolean overrideWfcRoamingModeWhileUsingNtn) {
        this.mMaxAllowedDataMode = maxAllowedDataMode;
        this.mDeviceSatellitePlmns = deviceSatellitePlmns;
        this.mOverrideWfcRoamingModeWhileUsingNtn = overrideWfcRoamingModeWhileUsingNtn;
    }
}
