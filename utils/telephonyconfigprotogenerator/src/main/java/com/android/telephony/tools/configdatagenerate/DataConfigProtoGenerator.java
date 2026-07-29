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

import com.android.internal.telephony.TelephonyConfigData;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator for Data Config Protobuf.
 *
 * <p>This class is responsible for parsing data configuration from an XML document and building
 * the corresponding {@link TelephonyConfigData.DataConfigProto}. It handles connection capability
 * configs and metered capability configs for both home and roaming scenarios.
 */
public class DataConfigProtoGenerator extends BaseConfigGenerator {

    private static final String TAG_DATA_CONFIG = "dataconfig";
    private static final String TAG_VERSION = "version";
    private static final String TAG_CONNECTION_CAPABILITY_CONFIGS = "connection_capability_configs";
    private static final String TAG_DEFAULT_CONNECTION_CAPABILITY_CONFIG =
            "default_connection_capability_config";
    private static final String TAG_CARRIER_CONNECTION_CAPABILITY_CONFIGS =
            "carrier_connection_capability_configs";
    private static final String TAG_RULES = "rules";
    private static final String TAG_CARRIER_ID = "carrier_id";

    private static final String TAG_HOME_METERED_CAPABILITY_CONFIGS =
            "home_metered_capability_configs";
    private static final String TAG_ROAM_METERED_CAPABILITY_CONFIGS =
            "roam_metered_capability_configs";
    private static final String TAG_DEFAULT_METERED_CAPABILITY_CONFIG =
            "default_metered_capability_config";
    private static final String TAG_CARRIER_METERED_CAPABILITY_CONFIGS =
            "carrier_metered_capability_configs";
    private static final String TAG_CAPABILITY_IDS = "capability_ids";

    private int mVersion;
    private ConnectionCapabilityMap mDefaultConnectionCapabilityMap;
    private List<ConnectionCapabilityMap> mCarrierConnectionCapabilityMaps;
    private MeteredCapabilities mDefaultHomeMeteredCapabilities;
    private List<MeteredCapabilities> mCarrierHomeMeteredCapabilities;
    private MeteredCapabilities mDefaultRoamMeteredCapabilities;
    private List<MeteredCapabilities> mCarrierRoamMeteredCapabilities;

    /**
     * Parses the data configuration from the provided XML document.
     *
     * <p>This method looks for the {@code dataconfig} tag and extracts the version,
     * connection capability configs, home metered capability configs, and roaming metered
     * capability configs.
     *
     * @param doc The XML document containing the configuration data.
     */
    @Override
    public void parse(Document doc) {
        NodeList dataConfigList = doc.getElementsByTagName(TAG_DATA_CONFIG);
        if (dataConfigList.getLength() > 0) {
            Element dataConfigElement = (Element) dataConfigList.item(0);
            System.out.println("\nData Config:");

            // Version
            NodeList versionList = dataConfigElement.getElementsByTagName(TAG_VERSION);
            if (versionList.getLength() > 0) {
                mVersion = Integer.parseInt(versionList.item(0).getTextContent());
                System.out.println("└ Version: " + mVersion);
            }

            parseConnectionCapabilityConfig(dataConfigElement);
            parseHomeMeteredCapabilityConfig(dataConfigElement);
            parseRoamMeteredCapabilityConfig(dataConfigElement);

        } else {
            System.out.println("\nData Config is empty");
        }
    }

    /**
     * Parses the connection capability configurations from the data config element.
     *
     * @param dataConfigElement The {@code dataconfig} XML element.
     */
    private void parseConnectionCapabilityConfig(Element dataConfigElement) {
        NodeList connConfigList = dataConfigElement.getElementsByTagName(
                TAG_CONNECTION_CAPABILITY_CONFIGS);
        if (connConfigList.getLength() > 0) {
            Element connConfigElement = (Element) connConfigList.item(0);

            // Default
            NodeList defaultList = connConfigElement.getElementsByTagName(
                    TAG_DEFAULT_CONNECTION_CAPABILITY_CONFIG);
            if (defaultList.getLength() > 0) {
                Element defaultElement = (Element) defaultList.item(0);
                mDefaultConnectionCapabilityMap =
                        parseConnectionCapabilityMap(defaultElement, false);
            }

            // Carrier Specific
            NodeList carrierList = connConfigElement.getElementsByTagName(
                    TAG_CARRIER_CONNECTION_CAPABILITY_CONFIGS);
            if (carrierList.getLength() > 0) {
                mCarrierConnectionCapabilityMaps = new ArrayList<>();
                for (int i = 0; i < carrierList.getLength(); i++) {
                    Element carrierElement = (Element) carrierList.item(i);
                    mCarrierConnectionCapabilityMaps.add(
                            parseConnectionCapabilityMap(carrierElement, true));
                }
            }
        }
    }

    /**
     * Parses the home metered capability configurations from the data config element.
     *
     * @param dataConfigElement The {@code dataconfig} XML element.
     */
    private void parseHomeMeteredCapabilityConfig(Element dataConfigElement) {
        NodeList homeMeteredList = dataConfigElement.getElementsByTagName(
                TAG_HOME_METERED_CAPABILITY_CONFIGS);
        if (homeMeteredList.getLength() > 0) {
            Element homeMeteredElement = (Element) homeMeteredList.item(0);

            // Default
            NodeList defaultList = homeMeteredElement.getElementsByTagName(
                    TAG_DEFAULT_METERED_CAPABILITY_CONFIG);
            if (defaultList.getLength() > 0) {
                Element defaultElement = (Element) defaultList.item(0);
                mDefaultHomeMeteredCapabilities =
                        parseMeteredCapabilities(defaultElement, false);
            }

            // Carrier Specific
            NodeList carrierList = homeMeteredElement.getElementsByTagName(
                    TAG_CARRIER_METERED_CAPABILITY_CONFIGS);
            if (carrierList.getLength() > 0) {
                mCarrierHomeMeteredCapabilities = new ArrayList<>();
                for (int i = 0; i < carrierList.getLength(); i++) {
                    Element carrierElement = (Element) carrierList.item(i);
                    mCarrierHomeMeteredCapabilities.add(
                            parseMeteredCapabilities(carrierElement, true));
                }
            }
        }
    }

    /**
     * Parses the roaming metered capability configurations from the data config element.
     *
     * @param dataConfigElement The {@code dataconfig} XML element.
     */
    private void parseRoamMeteredCapabilityConfig(Element dataConfigElement) {
        NodeList roamMeteredList = dataConfigElement.getElementsByTagName(
                TAG_ROAM_METERED_CAPABILITY_CONFIGS);
        if (roamMeteredList.getLength() > 0) {
            Element roamMeteredElement = (Element) roamMeteredList.item(0);

            // Default
            NodeList defaultList = roamMeteredElement.getElementsByTagName(
                    TAG_DEFAULT_METERED_CAPABILITY_CONFIG);
            if (defaultList.getLength() > 0) {
                Element defaultElement = (Element) defaultList.item(0);
                mDefaultRoamMeteredCapabilities =
                        parseMeteredCapabilities(defaultElement, false);
            }

            // Carrier Specific
            NodeList carrierList = roamMeteredElement.getElementsByTagName(
                    TAG_CARRIER_METERED_CAPABILITY_CONFIGS);
            if (carrierList.getLength() > 0) {
                mCarrierRoamMeteredCapabilities = new ArrayList<>();
                for (int i = 0; i < carrierList.getLength(); i++) {
                    Element carrierElement = (Element) carrierList.item(i);
                    mCarrierRoamMeteredCapabilities.add(
                            parseMeteredCapabilities(carrierElement, true));
                }
            }
        }
    }

    /**
     * Parses a single connection capability map from an XML element.
     *
     * @param element           The XML element containing the connection capability configuration.
     * @param isCarrierSpecific True if the configuration is specific to a carrier, false otherwise.
     * @return A {@link ConnectionCapabilityMap} object containing the parsed data.
     * @throws IllegalArgumentException If required fields are missing or invalid.
     */
    private ConnectionCapabilityMap parseConnectionCapabilityMap(
            Element element, boolean isCarrierSpecific) {
        Integer carrierId = null;
        if (isCarrierSpecific) {
            NodeList carrierIdList = element.getElementsByTagName(TAG_CARRIER_ID);
            if (carrierIdList.getLength() > 0) {
                carrierId = Integer.parseInt(carrierIdList.item(0).getTextContent());
            } else {
                throw new IllegalArgumentException(
                        "Carrier ID is missing in ConnectionCapabilityMap");
            }
        }

        List<String> rules = new ArrayList<>();
        NodeList rulesList = element.getElementsByTagName(TAG_RULES);
        for (int i = 0; i < rulesList.getLength(); i++) {
            String rule = rulesList.item(i).getTextContent();
            if (!Util.isValidConnectionCapabilityRule(rule)) {
                throw new IllegalArgumentException("Invalid Connection Capability Rule: " + rule);
            }
            rules.add(rule);
        }
        return new ConnectionCapabilityMap(carrierId, rules);
    }

    /**
     * Parses a single metered capabilities configuration from an XML element.
     *
     * @param element           The XML element containing the metered capability configuration.
     * @param isCarrierSpecific True if the configuration is specific to a carrier, false otherwise.
     * @return A {@link MeteredCapabilities} object containing the parsed data.
     * @throws IllegalArgumentException If required fields are missing or invalid.
     */
    private MeteredCapabilities parseMeteredCapabilities(
            Element element, boolean isCarrierSpecific) {
        Integer carrierId = null;
        if (isCarrierSpecific) {
            NodeList carrierIdList = element.getElementsByTagName(TAG_CARRIER_ID);
            if (carrierIdList.getLength() > 0) {
                carrierId = Integer.parseInt(carrierIdList.item(0).getTextContent());
            } else {
                throw new IllegalArgumentException("Carrier ID is missing in MeteredCapabilities");
            }
        }

        List<Integer> capabilities = new ArrayList<>();
        NodeList capList = element.getElementsByTagName(TAG_CAPABILITY_IDS);
        for (int i = 0; i < capList.getLength(); i++) {
            int capId = Integer.parseInt(capList.item(i).getTextContent());
            if (!Util.isValidCapabilityId(capId)) {
                throw new IllegalArgumentException("Invalid Capability ID: " + capId);
            }
            capabilities.add(capId);
        }
        return new MeteredCapabilities(carrierId, capabilities);
    }

    /**
     * Builds the DataConfigProto message using the parsed data.
     *
     * @param builder The TelephonyConfigProto builder to populate.
     */
    @Override
    public void build(TelephonyConfigData.TelephonyConfigProto.Builder builder) {
        TelephonyConfigData.DataConfigProto.Builder dataConfigBuilder =
                TelephonyConfigData.DataConfigProto.newBuilder();

        dataConfigBuilder.setVersion(mVersion);

        // ConnectionCapabilityConfig
        if (mDefaultConnectionCapabilityMap != null || mCarrierConnectionCapabilityMaps != null) {
            TelephonyConfigData.ConnectionCapabilityConfig.Builder connCapConfigBuilder =
                    TelephonyConfigData.ConnectionCapabilityConfig.newBuilder();

            if (mDefaultConnectionCapabilityMap != null) {
                TelephonyConfigData.ConnectionCapabilityMap.Builder mapBuilder =
                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder();
                mapBuilder.addAllRules(mDefaultConnectionCapabilityMap.mRules);
                // carrier_id is ignored for default
                connCapConfigBuilder.setDefaultConnectionCapabilityConfig(mapBuilder);
            }

            if (mCarrierConnectionCapabilityMaps != null) {
                for (ConnectionCapabilityMap map : mCarrierConnectionCapabilityMaps) {
                    TelephonyConfigData.ConnectionCapabilityMap.Builder mapBuilder =
                            TelephonyConfigData.ConnectionCapabilityMap.newBuilder();
                    mapBuilder.setCarrierId(map.mCarrierId);
                    mapBuilder.addAllRules(map.mRules);
                    connCapConfigBuilder.addCarrierConnectionCapabilityConfigs(mapBuilder);
                }
            }
            dataConfigBuilder.setConnectionCapabilityConfigs(connCapConfigBuilder);
        }

        // Home MeteredCapabilityConfig
        if (mDefaultHomeMeteredCapabilities != null || mCarrierHomeMeteredCapabilities != null) {
            TelephonyConfigData.MeteredCapabilityConfig.Builder homeMeteredBuilder =
                    TelephonyConfigData.MeteredCapabilityConfig.newBuilder();

            if (mDefaultHomeMeteredCapabilities != null) {
                TelephonyConfigData.MeteredCapabilities.Builder capsBuilder =
                        TelephonyConfigData.MeteredCapabilities.newBuilder();
                capsBuilder.addAllCapabilityIds(mDefaultHomeMeteredCapabilities.mCapabilityIds);
                homeMeteredBuilder.setDefaultMeteredCapabilityConfig(capsBuilder);
            }

            if (mCarrierHomeMeteredCapabilities != null) {
                for (MeteredCapabilities caps : mCarrierHomeMeteredCapabilities) {
                    TelephonyConfigData.MeteredCapabilities.Builder capsBuilder =
                            TelephonyConfigData.MeteredCapabilities.newBuilder();
                    capsBuilder.setCarrierId(caps.mCarrierId);
                    capsBuilder.addAllCapabilityIds(caps.mCapabilityIds);
                    homeMeteredBuilder.addCarrierMeteredCapabilityConfigs(capsBuilder);
                }
            }
            dataConfigBuilder.setHomeMeteredCapabilityConfigs(homeMeteredBuilder);
        }

        // Roam MeteredCapabilityConfig
        if (mDefaultRoamMeteredCapabilities != null || mCarrierRoamMeteredCapabilities != null) {
            TelephonyConfigData.MeteredCapabilityConfig.Builder roamMeteredBuilder =
                    TelephonyConfigData.MeteredCapabilityConfig.newBuilder();

            if (mDefaultRoamMeteredCapabilities != null) {
                TelephonyConfigData.MeteredCapabilities.Builder capsBuilder =
                        TelephonyConfigData.MeteredCapabilities.newBuilder();
                capsBuilder.addAllCapabilityIds(mDefaultRoamMeteredCapabilities.mCapabilityIds);
                roamMeteredBuilder.setDefaultMeteredCapabilityConfig(capsBuilder);
            }

            if (mCarrierRoamMeteredCapabilities != null) {
                for (MeteredCapabilities caps : mCarrierRoamMeteredCapabilities) {
                    TelephonyConfigData.MeteredCapabilities.Builder capsBuilder =
                            TelephonyConfigData.MeteredCapabilities.newBuilder();
                    capsBuilder.setCarrierId(caps.mCarrierId);
                    capsBuilder.addAllCapabilityIds(caps.mCapabilityIds);
                    roamMeteredBuilder.addCarrierMeteredCapabilityConfigs(capsBuilder);
                }
            }
            dataConfigBuilder.setRoamMeteredCapabilityConfigs(roamMeteredBuilder);
        }

        builder.setData(dataConfigBuilder);
    }
}