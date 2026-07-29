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

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.protobuf.ByteString;

import com.beust.jcommander.ParameterException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SatelliteConfigProtoGenerator extends BaseConfigGenerator {

    public static final String TAG_SATELLITE_CONFIG = "satelliteconfig";
    public static final String TAG_VERSION = "version";

    public static final String TAG_SUPPORTED_SERVICES = "carriersupportedservices";
    public static final String TAG_CARRIER_ID = "carrier_id";
    public static final String TAG_PROVIDER_CAPABILITY = "providercapability";
    public static final String TAG_CARRIER_PLMN = "carrier_plmn";
    public static final String TAG_SERVICE = "service";

    public static final String TAG_CARRIER_ROAMING_CONFIG = "carrier_roaming_config";
    public static final String TAG_MAX_ALLOWED_DATA_MODE = "max_allowed_data_mode";
    public static final String TAG_DEVICE_SATELLITE_PLMN = "device_satellite_plmn";
    public static final String TAG_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN =
            "override_wfc_roaming_mode_while_using_ntn";

    public static final String TAG_SATELLITE_REGION =  "satelliteregion";
    public static final String TAG_S2_CELL_FILE = "s2_cell_file";
    public static final String TAG_COUNTRY_CODE = "country_code";
    public static final String TAG_IS_ALLOWED = "is_allowed";
    public static final String TAG_SATELLITE_ACCESS_CONFIG_FILE = "satellite_access_config_file";

    public static final String TAG_ATTACH_SUPPORTED = "attach_supported";
    public static final String TAG_DATA_SUPPORT_MODE = "data_support_mode";
    public static final String TAG_NTN_CONNECT_TYPE = "ntn_connect_type";
    public static final String TAG_EMERGENCY_MESSAGING_SUPPORTED = "emergency_messaging_supported";
    public static final String TAG_ENTITLEMENT_SUPPORTED = "entitlement_supported";
    public static final String TAG_ENTITLEMENT_SERVER_URL = "entitlement_server_url";

    private int mVersion;
    private ArrayList<ServiceProto> mServiceProtoList;
    private RoamingConfigProto mCarrierRoamingConfig;
    private RegionProto mRegionProto;

    @Override
    public void parse(Document doc) {
        setSatelliteConfigVersion(doc);
        createCarrierRoamingConfigProto(doc);
        createSkyloConfigProto(doc);
    }

    /**
     * Set version after getting version from the input document
     *
     * @param doc the input document. Format of document should be
     * <pre>
     * &lt;version&gt;value1&lt;/version&gt;
     * </pre>
     */
    private void setSatelliteConfigVersion(Document doc) {
        NodeList satelliteConfigList = doc.getElementsByTagName(TAG_SATELLITE_CONFIG);
        if (satelliteConfigList.getLength() > 0) {
            Element satelliteConfigElement = (Element) satelliteConfigList.item(0);
            NodeList versionList = satelliteConfigElement.getElementsByTagName(TAG_VERSION);

            if (versionList.getLength() > 0) {
                Node versionNode = versionList.item(0);
                System.out.println("Satellite Version: " + versionNode.getTextContent());
                mVersion = Integer.parseInt(versionNode.getTextContent());
            } else {
                throw new ParameterException(
                        "Satellite Version is mandatory in " + TAG_SATELLITE_CONFIG);
            }
        } else {
            throw new ParameterException(
                    "Tag " + TAG_SATELLITE_CONFIG + " is missing. It is mandatory.");
        }
    }

    /**
     * Creates a list of ServiceProto from the input document
     *
     * @param doc the input document. Format of document should be
     * <pre>
     * &lt;carriersupportedservices&gt;
     *   &lt;carrier_id&gt;value1&lt;/carrier_id&gt;
     *   &lt;providercapability&gt;
     *     &lt;carrier_plmn&gt;value2&lt;/carrier_plmn&gt;
     *     &lt;service&gt;value3&lt;/service&gt;
     *   &lt;/providercapability&gt;
     * &lt;/carriersupportedservices&gt;
     * </pre>
     */
    private void createCarrierRoamingConfigProto(Document doc) {
        Node carrierRoamingConfig = doc.getElementsByTagName(TAG_CARRIER_ROAMING_CONFIG).item(0);
        if (carrierRoamingConfig != null) {
            Element carrierRoamingConfigElement = (Element) carrierRoamingConfig;
            System.out.println("\nCarrier Roaming Config ");

            Node nodeMaxAllowedDataMode = carrierRoamingConfigElement.getElementsByTagName(
                    TAG_MAX_ALLOWED_DATA_MODE).item(0);
            Integer maxAllowedDataMode = null;
            if (nodeMaxAllowedDataMode != null) {
                maxAllowedDataMode = Integer.parseInt(nodeMaxAllowedDataMode.getTextContent());
                if (!Util.isValidMaxAllowedDataMode(maxAllowedDataMode)) {
                    throw new ParameterException("Invalid maxAllowedDataModel: "
                            + maxAllowedDataMode);
                }
                System.out.println("└ MaxAllowedDataMode: " + maxAllowedDataMode);
            } else {
                System.out.println("└ MaxAllowedDataMode: empty");
            }

            NodeList nodeDeviceSatellitePlmnList =
                    carrierRoamingConfigElement.getElementsByTagName(TAG_DEVICE_SATELLITE_PLMN);
            List<String> satellitePlmnList = new ArrayList<>();
            if (nodeDeviceSatellitePlmnList != null) {
                System.out.print("└ Satellite Plmn List: ");
                for (int k = 0; k < nodeDeviceSatellitePlmnList.getLength(); k++) {
                    String plmn = nodeDeviceSatellitePlmnList.item(k).getTextContent();
                    System.out.print(plmn + " ");
                    if (!Util.isValidPlmn(plmn)) {
                        throw new ParameterException("Invalid PLMN: " + plmn);
                    }
                    satellitePlmnList.add(plmn);
                }
            } else {
                System.out.println("└ SatellitePLMNList: empty");
            }

            System.out.println();

            Node nodeOverrideWfcRoamingMode = carrierRoamingConfigElement.getElementsByTagName(
                    TAG_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN).item(0);
            Boolean overrideWfcRoamingMode = null;
            if (nodeOverrideWfcRoamingMode != null) {
                overrideWfcRoamingMode = Boolean.parseBoolean(
                        nodeOverrideWfcRoamingMode.getTextContent());
                System.out.println("└ OverrideWfcRoamingModeWhileUsingNtn: "
                        + overrideWfcRoamingMode);
            } else {
                System.out.println("└ OverrideWfcRoamingModeWhileUsingNtn: empty");
            }

            mCarrierRoamingConfig =
                    new RoamingConfigProto(maxAllowedDataMode, satellitePlmnList,
                            overrideWfcRoamingMode);
        } else {
            System.out.println("\nCarrier Roaming Config is empty");
            mCarrierRoamingConfig = null;
        }

        NodeList carrierServicesList = doc.getElementsByTagName(TAG_SUPPORTED_SERVICES);
        mServiceProtoList = new ArrayList<>();

        if (carrierServicesList.getLength() == 0) {
            System.out.println("\nCarrier Supported Satellite Services is empty");
        } else {
            System.out.println("\nCarrier Supported Satellite Services ");
            for (int i = 0; i < carrierServicesList.getLength(); i++) {
                Node carrierServiceNode = carrierServicesList.item(i);
                if (carrierServiceNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element carrierServiceElement = (Element) carrierServiceNode;

                    NodeList providerCapabilityList = carrierServiceElement.getElementsByTagName(
                            TAG_PROVIDER_CAPABILITY);
                    ProviderCapabilityProto[] capabilityProtoList = new ProviderCapabilityProto[0];
                    if (providerCapabilityList != null) {
                        capabilityProtoList =
                                new ProviderCapabilityProto[providerCapabilityList.getLength()];
                        for (int j = 0; j < providerCapabilityList.getLength(); j++) {
                            Node providerCapabilityNode = providerCapabilityList.item(j);
                            if (providerCapabilityNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element providerCapabilityElement =
                                        (Element) providerCapabilityNode;

                                NodeList carrierPlmnList = providerCapabilityElement
                                        .getElementsByTagName(TAG_CARRIER_PLMN);
                                String carrierPlmn = "";
                                if (carrierPlmnList.getLength() == 0) {
                                    throw new ParameterException("carrier_plmn is empty");
                                } else {
                                    carrierPlmn = carrierPlmnList.item(0).getTextContent();
                                    System.out.println("└ Carrier PLMN: " + carrierPlmn);
                                    if (!Util.isValidPlmn(carrierPlmn)) {
                                        throw new ParameterException("Invalid plmn:" + carrierPlmn);
                                    }
                                }

                                NodeList allowedServicesList = providerCapabilityElement
                                        .getElementsByTagName(TAG_SERVICE);
                                if (allowedServicesList.getLength() == 0) {
                                    throw new ParameterException("allowedServicesList is empty");
                                } else {
                                    System.out.print("└ Allowed services: ");
                                    int[] allowedServiceArray =
                                            new int[allowedServicesList.getLength()];
                                    for (int k = 0; k < allowedServicesList.getLength(); k++) {
                                        int service = Integer.parseInt(allowedServicesList.item(k)
                                                .getTextContent());
                                        System.out.print(service + " ");
                                        if (!Util.isValidService(service)) {
                                            throw new ParameterException(
                                                    "Invalid service:" + service);
                                        }
                                        allowedServiceArray[k] = service;
                                    }
                                    System.out.println();

                                    NodeList ntnConnectTypeList = providerCapabilityElement
                                            .getElementsByTagName(TAG_NTN_CONNECT_TYPE);
                                    Integer providerNtnConnectType = null;
                                    if (ntnConnectTypeList.getLength() > 0) {
                                        providerNtnConnectType = Integer.parseInt(
                                                ntnConnectTypeList.item(0).getTextContent());
                                        System.out.println("└ Provider NtnConnectType: "
                                                + providerNtnConnectType);
                                        if (!Util.isValidNtnConnectType(providerNtnConnectType)) {
                                            throw new ParameterException("Invalid ntnConnectType:"
                                                    + providerNtnConnectType);
                                        }
                                    } else {
                                        System.out.println("└ Provider NtnConnectType: empty");
                                    }

                                    ProviderCapabilityProto capabilityProto =
                                            new ProviderCapabilityProto(carrierPlmn,
                                                    allowedServiceArray, providerNtnConnectType);
                                    capabilityProtoList[j] = capabilityProto;
                                }
                            }
                        }
                    }

                    String carrierId = "";
                    Node nodeCarrierId = carrierServiceElement.getElementsByTagName(TAG_CARRIER_ID)
                            .item(0);
                    if (nodeCarrierId != null) {
                        carrierId = nodeCarrierId.getTextContent();
                        System.out.println("└ Carrier ID: " + nodeCarrierId.getTextContent());
                    } else {
                        throw new ParameterException("* carrierId is empty");
                    }

                    Node nodeAttachSupported = carrierServiceElement.getElementsByTagName(
                            TAG_ATTACH_SUPPORTED).item(0);
                    Boolean attachSupported = (nodeAttachSupported != null)
                            ? Boolean.parseBoolean(nodeAttachSupported.getTextContent()) : null;
                    if (attachSupported != null) {
                        System.out.println("└ Attach Supported: " + attachSupported);
                    }

                    Node nodeDataSupportMode = carrierServiceElement.getElementsByTagName(
                            TAG_DATA_SUPPORT_MODE).item(0);
                    Integer dataSupportMode = (nodeDataSupportMode != null)
                            ? Integer.parseInt(nodeDataSupportMode.getTextContent()) : null;
                    if (dataSupportMode != null) {
                        System.out.println("└ Data Support Mode: " + dataSupportMode);
                        if (!Util.isValidMaxAllowedDataMode(dataSupportMode)) {
                            throw new ParameterException("Invalid dataSupportMode: "
                                    + dataSupportMode);
                        }
                    }

                    Node nodeNtnConnectType = carrierServiceElement.getElementsByTagName(
                            TAG_NTN_CONNECT_TYPE).item(0);
                    Integer ntnConnectType = (nodeNtnConnectType != null)
                            ? Integer.parseInt(nodeNtnConnectType.getTextContent()) : null;
                    if (ntnConnectType != null) {
                        System.out.println("└ Ntn Connect Type: " + ntnConnectType);
                        if (!Util.isValidNtnConnectType(ntnConnectType)) {
                            throw new ParameterException("Invalid ntnConnectType: "
                                    + ntnConnectType);
                        }
                    }

                    Node nodeEmergencyMessagingSupported = carrierServiceElement
                            .getElementsByTagName(TAG_EMERGENCY_MESSAGING_SUPPORTED).item(0);
                    Boolean emergencyMessagingSupported = (nodeEmergencyMessagingSupported != null)
                            ? Boolean.parseBoolean(nodeEmergencyMessagingSupported.getTextContent())
                            : null;
                    if (emergencyMessagingSupported != null) {
                        System.out.println("└ Emergency Messaging Supported: "
                                + emergencyMessagingSupported);
                    }

                    Node nodeEntitlementSupported = carrierServiceElement.getElementsByTagName(
                            TAG_ENTITLEMENT_SUPPORTED).item(0);
                    Boolean entitlementSupported = (nodeEntitlementSupported != null)
                            ? Boolean.parseBoolean(nodeEntitlementSupported.getTextContent())
                            : null;
                    if (entitlementSupported != null) {
                        System.out.println("└ Entitlement Supported: " + entitlementSupported);
                    }

                    Node nodeEntitlementServerUrl = carrierServiceElement.getElementsByTagName(
                            TAG_ENTITLEMENT_SERVER_URL).item(0);
                    String entitlementServerUrl = (nodeEntitlementServerUrl != null)
                            ? nodeEntitlementServerUrl.getTextContent() : null;
                    if (entitlementServerUrl != null) {
                        System.out.println("└ Entitlement Server URL: " + entitlementServerUrl);
                    }

                    if (capabilityProtoList.length != 0) {
                        ServiceProto serviceProto = new ServiceProto(Integer.parseInt(carrierId),
                                capabilityProtoList, attachSupported, dataSupportMode,
                                ntnConnectType, emergencyMessagingSupported, entitlementSupported,
                                entitlementServerUrl);
                        mServiceProtoList.add(serviceProto);
                    } else {
                        throw new ParameterException("capabilityProtoList is empty");
                    }
                }
            }
        }
    }

    /**
     * Creates a RegionProto from the input document
     *
     * @param doc the input document. Format of document should be
     * <pre>
     * &lt;satelliteregion&gt;
     *   &lt;s2_cell_file&gt;value1&lt;/s2_cell_file&gt;
     *   &lt;country_code&gt;value2&lt;/country_code&gt;
     *   &lt;country_code&gt;value3&lt;/country_code&gt;
     *   &lt;is_allowed&gt;value4&lt;/is_allowed&gt;
     *   &lt;satellite_access_config_file&gt;value5lt;/satellite_access_config_file&gt;
     * &lt;/satelliteregion&gt;
     * </pre>
     */
    private void createSkyloConfigProto(Document doc) {
        NodeList satelliteRegionList = doc.getElementsByTagName(TAG_SATELLITE_REGION);
        Node satelliteRegionNode = satelliteRegionList.item(0);
        if (satelliteRegionNode != null && satelliteRegionNode.getNodeType() == Node.ELEMENT_NODE) {
            Element satelliteRegionElement = (Element) satelliteRegionNode;

            String s2CellFileName = "";
            if (satelliteRegionElement.getElementsByTagName(TAG_S2_CELL_FILE).getLength() > 0) {
                s2CellFileName = satelliteRegionElement.getElementsByTagName(TAG_S2_CELL_FILE)
                        .item(0).getTextContent();
            }

            String satelliteAccessConfigFileName = "";
            if (satelliteRegionElement.getElementsByTagName(TAG_SATELLITE_ACCESS_CONFIG_FILE)
                            .getLength() > 0) {
                satelliteAccessConfigFileName = satelliteRegionElement
                                .getElementsByTagName(TAG_SATELLITE_ACCESS_CONFIG_FILE)
                                .item(0).getTextContent();
            }

            Node nodeIsAllowed = satelliteRegionElement.getElementsByTagName(TAG_IS_ALLOWED)
                    .item(0);
            boolean isAllowed = true;
            if (nodeIsAllowed != null) {
                String isAllowedString = nodeIsAllowed.getTextContent();
                if (isAllowedString.equalsIgnoreCase("FALSE")) {
                    isAllowed = false;
                }
            } else {
                throw new ParameterException(" "
                        + "** isAllowed is empty, please put the value explicitly");
            }

            System.out.println("\nSatellite Region:");
            System.out.println("└ S2 Cell File: " + s2CellFileName);
            System.out.println("└ Is Allowed: " + isAllowed);
            System.out.println(
                    "└ Satellite Access Config File Name: " + satelliteAccessConfigFileName);

            NodeList countryCodesList = satelliteRegionElement.getElementsByTagName(
                    TAG_COUNTRY_CODE);
            String[] listCountryCode = new String[0];
            if (countryCodesList != null) {
                listCountryCode = new String[countryCodesList.getLength()];
                System.out.print("└ Country Codes: ");
                for (int k = 0; k < countryCodesList.getLength(); k++) {
                    String countryCode = countryCodesList.item(k).getTextContent();
                    System.out.print(countryCode + " ");
                    if (!Util.isValidCountryCode(countryCode)) {
                        throw new ParameterException("Invalid countryCode:" + countryCode);
                    }
                    listCountryCode[k] = countryCode;
                }
            }

            System.out.println();
            mRegionProto =
                    new RegionProto(
                            s2CellFileName,
                            listCountryCode,
                            isAllowed,
                            satelliteAccessConfigFileName);
        }
    }

    /**
     * Generate Protobuf.
     *
     * The output file is a binary file of TelephonyConfigProto.
     *
     * The format of TelephonyConfigProto is defined in
     * https://source.corp.google.com/android/frameworks/opt/telephony/proto/src/
     * telephony_config_update.proto
     */
    @Override
    public void build(
            TelephonyConfigData.TelephonyConfigProto.Builder builder) {

        TelephonyConfigData.SatelliteConfigProto.Builder satelliteConfigBuilder =
                TelephonyConfigData.SatelliteConfigProto.newBuilder();

        satelliteConfigBuilder.setVersion(mVersion);    // Input version

        if (mServiceProtoList != null) {
            // carrierSupportedSatelliteServiceBuilder
            TelephonyConfigData.CarrierSupportedSatelliteServicesProto.Builder
                    carrierSupportedSatelliteServiceBuilder =
                    TelephonyConfigData.CarrierSupportedSatelliteServicesProto.newBuilder();
            for (int i = 0; i < mServiceProtoList.size(); i++) {
                ServiceProto proto = mServiceProtoList.get(i);
                carrierSupportedSatelliteServiceBuilder.setCarrierId(proto.mCarrierId);
                if (proto.mAttachSupported != null) {
                    carrierSupportedSatelliteServiceBuilder.setAttachSupported(
                            proto.mAttachSupported);
                }
                if (proto.mDataSupportMode != null) {
                    carrierSupportedSatelliteServiceBuilder.setDataSupportMode(
                            proto.mDataSupportMode);
                }
                if (proto.mNtnConnectType != null) {
                    carrierSupportedSatelliteServiceBuilder.setNtnConnectType(
                            proto.mNtnConnectType);
                }
                if (proto.mEmergencyMessagingSupported != null) {
                    carrierSupportedSatelliteServiceBuilder.setEmergencyMessagingSupported(
                            proto.mEmergencyMessagingSupported);
                }
                if (proto.mEntitlementSupported != null) {
                    carrierSupportedSatelliteServiceBuilder.setEntitlementSupported(
                            proto.mEntitlementSupported);
                }
                if (proto.mEntitlementServerUrl != null) {
                    carrierSupportedSatelliteServiceBuilder.setEntitlementServerUrl(
                            proto.mEntitlementServerUrl);
                }

                TelephonyConfigData.SatelliteProviderCapabilityProto.Builder
                        satelliteProviderCapabilityBuilder =
                        TelephonyConfigData.SatelliteProviderCapabilityProto.newBuilder();
                ProviderCapabilityProto[] capabilityProtoList = proto.mCapabilityProtoList;
                for (int j = 0; j < capabilityProtoList.length; j++) {
                    ProviderCapabilityProto capabilityProto = capabilityProtoList[j];
                    satelliteProviderCapabilityBuilder.setCarrierPlmn(capabilityProto.mPlmn);
                    int[] allowedServiceList = capabilityProto.mAllowedServices;
                    for (int k = 0; k < allowedServiceList.length; k++) {
                        satelliteProviderCapabilityBuilder
                                .addAllowedServices(allowedServiceList[k]);
                    }
                    if (capabilityProto.mNtnConnectType != null) {
                        satelliteProviderCapabilityBuilder.setNtnConnectType(
                                capabilityProto.mNtnConnectType);
                    }
                    carrierSupportedSatelliteServiceBuilder
                            .addSupportedSatelliteProviderCapabilities(
                                    satelliteProviderCapabilityBuilder);
                    satelliteProviderCapabilityBuilder.clear();
                }
                satelliteConfigBuilder.addCarrierSupportedSatelliteServices(
                        carrierSupportedSatelliteServiceBuilder);
                carrierSupportedSatelliteServiceBuilder.clear();
            }
        } else {
            System.out.println("ServiceProtoList does not exist");
        }

        if (mCarrierRoamingConfig != null) {
            // carrierRoamingConfigBuilder
            TelephonyConfigData.CarrierRoamingConfigProto.Builder carrierRoamingConfigBuilder =
                    TelephonyConfigData.CarrierRoamingConfigProto.newBuilder();
            if (mCarrierRoamingConfig.mMaxAllowedDataMode != null) {
                carrierRoamingConfigBuilder.setMaxAllowedDataMode(
                        mCarrierRoamingConfig.mMaxAllowedDataMode);
            }

            if (mCarrierRoamingConfig.mDeviceSatellitePlmns != null) {
                carrierRoamingConfigBuilder.addAllDeviceSatellitePlmn(
                        mCarrierRoamingConfig.mDeviceSatellitePlmns);
            }

            if (mCarrierRoamingConfig.mOverrideWfcRoamingModeWhileUsingNtn != null) {
                carrierRoamingConfigBuilder.setOverrideWfcRoamingModeWhileUsingNtn(
                        mCarrierRoamingConfig.mOverrideWfcRoamingModeWhileUsingNtn);
            }

            satelliteConfigBuilder.setCarrierRoamingConfig(carrierRoamingConfigBuilder);
        }

        if (mRegionProto != null) {
            System.out.println("sRegionProto");
            // satelliteRegionBuilder
            TelephonyConfigData.SatelliteRegionProto.Builder satelliteRegionBuilder =
                    TelephonyConfigData.SatelliteRegionProto.newBuilder();

            // mS2CellFileName
            if (mRegionProto.mS2CellFileName != null
                    && !mRegionProto.mS2CellFileName.isEmpty()) {
                byte[] s2SatBinaryData;
                try {
                    s2SatBinaryData = readFileToByteArray(mRegionProto.mS2CellFileName);
                } catch (IOException e) {
                    throw new RuntimeException("Got exception in reading the file "
                            + mRegionProto.mS2CellFileName + ", e=" + e);
                }
                if (s2SatBinaryData != null) {
                    satelliteRegionBuilder.setS2CellFile(ByteString.copyFrom(s2SatBinaryData));
                }
            }

            // mCountryCodeList
            String[] countryCodeList = mRegionProto.mCountryCodeList;
            for (int i = 0; i < countryCodeList.length; i++) {
                satelliteRegionBuilder.addCountryCodes(countryCodeList[i]);
            }

            // mIsAllowed
            satelliteRegionBuilder.setIsAllowed(mRegionProto.mIsAllowed);

            // mSatelliteAccessConfigFileName
            if (mRegionProto.mSatelliteAccessConfigFileName != null
                    && !mRegionProto.mSatelliteAccessConfigFileName.isEmpty()) {
                byte[] satelliteAccessBinaryData;
                try {
                    satelliteAccessBinaryData =
                            readFileToByteArray(mRegionProto.mSatelliteAccessConfigFileName);
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Got exception in reading the mSatelliteAccessConfigFileName "
                                    + mRegionProto.mSatelliteAccessConfigFileName
                                    + ", e="
                                    + e);
                }
                if (satelliteAccessBinaryData != null) {
                    satelliteRegionBuilder.setSatelliteAccessConfigFile(
                            ByteString.copyFrom(satelliteAccessBinaryData));
                }
            }

            satelliteConfigBuilder.setDeviceSatelliteRegion(satelliteRegionBuilder);
        } else {
            System.out.println("\nRegionProto does not exist");
        }

        builder.setSatellite(satelliteConfigBuilder);
    }

    private static byte[] readFileToByteArray(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("File: " + fileName + " does not exist");
        }

        if (file.exists() && file.canRead()) {
            FileInputStream fileInputStream = new FileInputStream(file);
            long fileSize = fileInputStream.available();
            byte[] bytes = new byte[(int) fileSize];
            int bytesRead = fileInputStream.read(bytes);
            fileInputStream.close();
            if (bytesRead != fileSize) {
                throw new IOException("file read fail: " + file.getCanonicalPath());
            }
            return bytes;
        }
        return null;
    }
}