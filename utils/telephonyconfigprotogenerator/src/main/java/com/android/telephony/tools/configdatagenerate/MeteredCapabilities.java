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

package com.android.telephony.tools.configdatagenerate;

import java.util.List;

/**
 * A data class representing metered capabilities configuration.
 *
 * <p>This class stores the configuration for metered network capabilities, which can be associated
 * with a specific carrier or applied as a default. It includes the carrier ID and a list of
 * capability IDs that are treated as metered.
 */
public class MeteredCapabilities {
    /**
     * The unique identifier for the carrier.
     * Can be null if this represents a default configuration.
     */
    public final Integer mCarrierId;

    /**
     * A list of capability IDs representing the metered capabilities.
     * Examples might include specific network capability constants.
     */
    public final List<Integer> mCapabilityIds;

    /**
     * Constructs a new MeteredCapabilities instance.
     *
     * @param carrierId     The carrier ID.
     * @param capabilityIds The list of capability IDs.
     */
    public MeteredCapabilities(Integer carrierId, List<Integer> capabilityIds) {
        mCarrierId = carrierId;
        mCapabilityIds = capabilityIds;
    }
}
