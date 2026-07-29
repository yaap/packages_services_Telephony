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
 * Represents the carrier supported satellite services and configuration for a specific carrier
 *
 * These fields map to the corresponding carrier config keys for satellite enablement.
 */
public class ServiceProto {

    /** The carrier ID. */
    public Integer mCarrierId;

    /**
     * Array of supported satellite provider capabilities for this carrier.
     * Mapped from: KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE
     */
    public ProviderCapabilityProto[] mCapabilityProtoList;

    /**
     * Whether satellite PLMN scan and attachment are supported.
     * Mapped from: KEY_SATELLITE_ATTACH_SUPPORTED_BOOL
     */
    public Boolean mAttachSupported;

    /**
     * Satellite network data traffic support mode.
     * (0: Restricted, 1: Bandwidth restricted, 2: Full)
     * Mapped from: KEY_SATELLITE_DATA_SUPPORT_MODE_INT
     */
    public Integer mDataSupportMode;

    /**
     * Satellite network data traffic support mode.
     * (0: Restricted, 1: Bandwidth restricted, 2: Full)
     * Mapped from: KEY_SATELLITE_DATA_SUPPORT_MODE_INT
     */
    public Integer mNtnConnectType;

    /**
     * Whether satellite emergency messaging is supported.
     * Mapped from: KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL
     */
    public Boolean mEmergencyMessagingSupported;

    /**
     * Whether to use satellite entitlement check server query.
     * Mapped from: KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL
     */
    public Boolean mEntitlementSupported;

    /**
     * The address of the entitlement configuration server.
     * Mapped from: KEY_ENTITLEMENT_SERVER_URL_STRING
     */
    public String mEntitlementServerUrl;

    /**
     * Constructor for carrier supported satellite services
     */
    public ServiceProto(Integer carrierId, ProviderCapabilityProto[] capabilityProtoList,
            Boolean attachSupported, Integer dataSupportMode, Integer ntnConnectType,
            Boolean emergencyMessagingSupported, Boolean entitlementSupported,
            String entitlementServerUrl) {
        mCarrierId = carrierId;
        mCapabilityProtoList = capabilityProtoList;
        mAttachSupported = attachSupported;
        mDataSupportMode = dataSupportMode;
        mNtnConnectType = ntnConnectType;
        mEmergencyMessagingSupported = emergencyMessagingSupported;
        mEntitlementSupported = entitlementSupported;
        mEntitlementServerUrl = entitlementServerUrl;
    }
}
