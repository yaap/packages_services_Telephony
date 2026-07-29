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
 * A data class representing connection capability configuration rules.
 *
 * <p>This class stores the configuration for connection capabilities, which can be associated with
 * a specific carrier or applied as a default. It includes the carrier ID and a list of rules string
 * defining the capabilities.
 */
public class ConnectionCapabilityMap {
    /**
     * The unique identifier for the carrier.
     * Can be null if this represents a default configuration.
     */
    public final Integer mCarrierId;

    /**
     * A list of rules defining the connection capabilities.
     * The format of the rule string is expected to be validated by the generator.
     */
    public final List<String> mRules;

    /**
     * Constructs a new ConnectionCapabilityMap instance.
     *
     * @param carrierId The carrier ID.
     * @param rules     The list of rules strings.
     */
    public ConnectionCapabilityMap(Integer carrierId, List<String> rules) {
        mCarrierId = carrierId;
        mRules = rules;
    }
}
