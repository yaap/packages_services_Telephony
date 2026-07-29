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

import static com.android.telephony.tools.configdatagenerate.Util.SATELLITE_DATA_SUPPORT_ALL;
import static com.android.telephony.tools.configdatagenerate.Util.SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED;
import static com.android.telephony.tools.configdatagenerate.Util.SERVICE_TYPE_MMS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.TelephonyConfigData.CarrierRoamingConfigProto;
import com.android.internal.telephony.TelephonyConfigData.CarrierSupportedSatelliteServicesProto;
import com.android.internal.telephony.TelephonyConfigData.SatelliteConfigProto;
import com.android.internal.telephony.TelephonyConfigData.SatelliteProviderCapabilityProto;
import com.android.internal.telephony.TelephonyConfigData.SatelliteRegionProto;

import com.beust.jcommander.ParameterException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class SatelliteConfigProtoGeneratorTest {

    private static final String PLMN_VALID_310062 = "310062";
    private static final List<String> DEVICE_SATELLITE_PLMNS = List.of("310210");
    private static final String COUNTRY_CODE_US = "US";
    private static final int VERSION_VALID = 14;
    private static final int CARRIER_ID_VALID = 1;
    private static final String ENTITLEMENT_URL = "https://example.com";

    private Path mTempDirPath;
    private Path mInputDirPath;
    private Path mOutputDirPath;

    private File mInputXmlFile;
    private Path mInputXmlFilePath;
    private Path mOutputPbFilePath;

    private SatelliteConfigProtoGenerator mGenerator;

    @Before
    public void setUp() throws IOException {
        mTempDirPath = Files.createTempDirectory(this.getClass().getSimpleName());
        mInputDirPath = mTempDirPath.resolve("input");
        mOutputDirPath = mTempDirPath.resolve("output");
        mGenerator = new SatelliteConfigProtoGenerator();
    }

    @After
    public void tearDown() throws IOException {
        if (mTempDirPath != null) {
            deleteDirectory(mTempDirPath);
        }
    }

    private void prepareInAndOutData(int count) throws IOException {
        if (!Files.exists(mInputDirPath)) {
            Files.createDirectory(mInputDirPath);
        }

        mInputXmlFilePath = mInputDirPath.resolve(String.format("test_input_%s.xml", count));
        mInputXmlFile = mInputXmlFilePath.toFile();

        if (!Files.exists(mOutputDirPath)) {
            Files.createDirectory(mOutputDirPath);
        }
        mOutputPbFilePath = mOutputDirPath.resolve(String.format("test_out_%s.pb", count));
    }

    private String getS2CellFile(boolean empty) throws IOException {
        if (!empty) {
            return null;
        }
        if (!Files.exists(mInputDirPath)) {
            Files.createDirectory(mInputDirPath);
        }
        Path inputS2CellFilePath = mInputDirPath.resolve("sats2.dat");
        Files.write(inputS2CellFilePath, "Test ByteString!".getBytes(StandardCharsets.UTF_8));
        return inputS2CellFilePath.toAbsolutePath().toString();
    }

    private String getSACJsonFile(boolean empty) throws IOException {
        if (!empty) {
            return null;
        }
        if (!Files.exists(mInputDirPath)) {
            Files.createDirectory(mInputDirPath);
        }
        Path inputSatelliteAccessConfigFilePath =
                mInputDirPath.resolve("satellite_access_config.json");
        Files.write(inputSatelliteAccessConfigFilePath,
                "Test ByteString for satellite access config!".getBytes(StandardCharsets.UTF_8));
        return inputSatelliteAccessConfigFilePath.toAbsolutePath().toString();
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                    throws IOException {
                Files.deleteIfExists(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Document createDocumentFromXmlFile(File xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(xmlFile);
    }

    @Test
    public void testParseAndBuild_ValidInput() throws Exception {
        prepareInAndOutData(1);
        createInputXml(
                mInputXmlFile,
                VERSION_VALID,
                CARRIER_ID_VALID,
                PLMN_VALID_310062,
                SERVICE_TYPE_MMS,
                1,    // ntnConnectType, MANUAL(1)
                SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED,
                DEVICE_SATELLITE_PLMNS,
                true, // overrideWfcRoamingMode
                true, // attachSupported
                SATELLITE_DATA_SUPPORT_ALL,
                2,    // ntnConnectType HYBRID(2),
                true, // emergencyMessagingSupported
                true, // entitlementSupported
                ENTITLEMENT_URL,
                COUNTRY_CODE_US,
                true,
                getS2CellFile(true),
                getSACJsonFile(true));

        Document doc = createDocumentFromXmlFile(mInputXmlFile);
        mGenerator.parse(doc);
        TelephonyConfigData.TelephonyConfigProto.Builder builder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();
        mGenerator.build(builder);

        SatelliteConfigProto satelliteConfigProto = builder.getSatellite();

        assertEquals(VERSION_VALID, satelliteConfigProto.getVersion());
        CarrierSupportedSatelliteServicesProto serviceProto =
                satelliteConfigProto.getCarrierSupportedSatelliteServices(0);
        assertEquals(CARRIER_ID_VALID, serviceProto.getCarrierId());
        assertTrue(serviceProto.getAttachSupported());
        assertEquals(SATELLITE_DATA_SUPPORT_ALL, serviceProto.getDataSupportMode());
        assertEquals(2 /* HYBRID */, serviceProto.getNtnConnectType());
        assertTrue(serviceProto.getEmergencyMessagingSupported());
        assertTrue(serviceProto.getEntitlementSupported());
        assertEquals(ENTITLEMENT_URL, serviceProto.getEntitlementServerUrl());

        SatelliteProviderCapabilityProto providerCapabilityProto =
                serviceProto.getSupportedSatelliteProviderCapabilities(0);
        assertEquals(PLMN_VALID_310062, providerCapabilityProto.getCarrierPlmn());
        assertEquals(SERVICE_TYPE_MMS, providerCapabilityProto.getAllowedServices(0));
        assertEquals(1 /* MANUAL */, providerCapabilityProto.getNtnConnectType());

        CarrierRoamingConfigProto carrierRoamingConfigProto =
                satelliteConfigProto.getCarrierRoamingConfig();
        assertEquals(SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED,
                carrierRoamingConfigProto.getMaxAllowedDataMode());
        assertEquals(DEVICE_SATELLITE_PLMNS,
                carrierRoamingConfigProto.getDeviceSatellitePlmnList());
        assertTrue(carrierRoamingConfigProto.getOverrideWfcRoamingModeWhileUsingNtn());

        SatelliteRegionProto regionProto = satelliteConfigProto.getDeviceSatelliteRegion();
        assertEquals(COUNTRY_CODE_US, regionProto.getCountryCodes(0));
        assertTrue(regionProto.hasS2CellFile());
        assertTrue(regionProto.getIsAllowed());
        assertTrue(regionProto.hasSatelliteAccessConfigFile());
    }

    @Test(expected = ParameterException.class)
    public void testParse_InvalidVersion() throws Exception {
        prepareInAndOutData(2);
        // Create XML without version
        createInputXml(
                mInputXmlFile,
                null, // Version null
                CARRIER_ID_VALID,
                PLMN_VALID_310062,
                SERVICE_TYPE_MMS,
                null,
                SATELLITE_DATA_SUPPORT_BANDWIDTH_CONSTRAINED,
                DEVICE_SATELLITE_PLMNS,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                COUNTRY_CODE_US,
                true,
                getS2CellFile(true),
                getSACJsonFile(true));

        Document doc = createDocumentFromXmlFile(mInputXmlFile);
        mGenerator.parse(doc);
    }

    @Test
    public void testParse_OptionalFieldsMissing() throws Exception {
        prepareInAndOutData(3);
        // Create XML with optional fields missing (e.g., roaming config empty)
        createInputXml(
                mInputXmlFile,
                VERSION_VALID,
                CARRIER_ID_VALID,
                PLMN_VALID_310062,
                SERVICE_TYPE_MMS,
                null, // providerNtnConnectType
                null, // Max allowed data mode null
                null, // Device satellite plmns null
                null, // overrideWfcRoamingMode
                null, // attachSupported
                null, // dataSupportMode
                null, // ntnConnectType
                null, // emergencyMessagingSupported
                null, // entitlementSupported
                null, // entitlementServerUrl
                COUNTRY_CODE_US,
                true,
                getS2CellFile(true),
                getSACJsonFile(true));

        Document doc = createDocumentFromXmlFile(mInputXmlFile);
        mGenerator.parse(doc);

        TelephonyConfigData.TelephonyConfigProto.Builder builder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();
        mGenerator.build(builder);
        SatelliteConfigProto satelliteConfigProto = builder.getSatellite();

        // Roaming config should be present but empty fields
        assertTrue(satelliteConfigProto.hasCarrierRoamingConfig());
        assertFalse(satelliteConfigProto.getCarrierRoamingConfig().hasMaxAllowedDataMode());
        assertTrue(satelliteConfigProto.getCarrierRoamingConfig()
                .getDeviceSatellitePlmnList().isEmpty());
        assertFalse(satelliteConfigProto.getCarrierRoamingConfig()
                .hasOverrideWfcRoamingModeWhileUsingNtn());

        CarrierSupportedSatelliteServicesProto serviceProto =
                satelliteConfigProto.getCarrierSupportedSatelliteServices(0);
        assertFalse(serviceProto.hasAttachSupported());
        assertFalse(serviceProto.hasDataSupportMode());
        assertFalse(serviceProto.hasNtnConnectType());
        assertFalse(serviceProto.hasEmergencyMessagingSupported());
        assertFalse(serviceProto.hasEntitlementSupported());
        assertFalse(serviceProto.hasEntitlementServerUrl());

        SatelliteProviderCapabilityProto providerCapabilityProto =
                serviceProto.getSupportedSatelliteProviderCapabilities(0);
        assertFalse(providerCapabilityProto.hasNtnConnectType());
    }

    private void createInputXml(
            File outputFile,
            Integer version,
            int carrierId,
            String plmn,
            int allowedService,
            Integer providerNtnConnectType,
            Integer maxAllowedDataMode,
            List<String> satellitePlmnList,
            Boolean overrideWfcRoamingMode,
            Boolean attachSupported,
            Integer dataSupportMode,
            Integer ntnConnectType,
            Boolean emergencyMessagingSupported,
            Boolean entitlementSupported,
            String entitlementServerUrl,
            String countryCode,
            boolean isAllowed,
            String inputS2CellFileName,
            String inputSatelliteAccessConfigFileName) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootElement = doc.createElement(
                    SatelliteConfigProtoGenerator.TAG_SATELLITE_CONFIG);
            doc.appendChild(rootElement);

            if (version != null) {
                Element versionElement = doc.createElement(
                        SatelliteConfigProtoGenerator.TAG_VERSION);
                versionElement.appendChild(doc.createTextNode(String.valueOf(version)));
                rootElement.appendChild(versionElement);
            }

            Element carrierSupportedServices = doc.createElement(
                    SatelliteConfigProtoGenerator.TAG_SUPPORTED_SERVICES);
            carrierSupportedServices.appendChild(createElementWithText(doc,
                    SatelliteConfigProtoGenerator.TAG_CARRIER_ID, String.valueOf(carrierId)));

            if (attachSupported != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_ATTACH_SUPPORTED,
                        String.valueOf(attachSupported)));
            }
            if (dataSupportMode != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_DATA_SUPPORT_MODE,
                        String.valueOf(dataSupportMode)));
            }
            if (ntnConnectType != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_NTN_CONNECT_TYPE,
                        String.valueOf(ntnConnectType)));
            }
            if (emergencyMessagingSupported != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_EMERGENCY_MESSAGING_SUPPORTED,
                        String.valueOf(emergencyMessagingSupported)));
            }
            if (entitlementSupported != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_ENTITLEMENT_SUPPORTED,
                        String.valueOf(entitlementSupported)));
            }
            if (entitlementServerUrl != null) {
                carrierSupportedServices.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_ENTITLEMENT_SERVER_URL,
                        entitlementServerUrl));
            }

            Element providerCapability = doc.createElement(
                    SatelliteConfigProtoGenerator.TAG_PROVIDER_CAPABILITY);
            providerCapability.appendChild(createElementWithText(doc,
                    SatelliteConfigProtoGenerator.TAG_CARRIER_PLMN, plmn));
            providerCapability.appendChild(createElementWithText(doc,
                    SatelliteConfigProtoGenerator.TAG_SERVICE, String.valueOf(allowedService)));
            if (providerNtnConnectType != null) {
                providerCapability.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_NTN_CONNECT_TYPE,
                        String.valueOf(providerNtnConnectType)));
            }
            carrierSupportedServices.appendChild(providerCapability);
            rootElement.appendChild(carrierSupportedServices);

            Element carrierRoamingConfig = doc.createElement(
                    SatelliteConfigProtoGenerator.TAG_CARRIER_ROAMING_CONFIG);
            if (maxAllowedDataMode != null) {
                carrierRoamingConfig.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_MAX_ALLOWED_DATA_MODE,
                        String.valueOf(maxAllowedDataMode)));
            }
            if (satellitePlmnList != null) {
                for (String satPlmn : satellitePlmnList) {
                    carrierRoamingConfig.appendChild(createElementWithText(doc,
                            SatelliteConfigProtoGenerator.TAG_DEVICE_SATELLITE_PLMN, satPlmn));
                }
            }
            if (overrideWfcRoamingMode != null) {
                carrierRoamingConfig.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN,
                        String.valueOf(overrideWfcRoamingMode)));
            }
            rootElement.appendChild(carrierRoamingConfig);

            Element satelliteRegion = doc.createElement(
                    SatelliteConfigProtoGenerator.TAG_SATELLITE_REGION);
            if (inputS2CellFileName != null) {
                satelliteRegion.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_S2_CELL_FILE, inputS2CellFileName));
            }
            satelliteRegion.appendChild(createElementWithText(doc,
                    SatelliteConfigProtoGenerator.TAG_COUNTRY_CODE, countryCode));
            satelliteRegion.appendChild(createElementWithText(doc,
                    SatelliteConfigProtoGenerator.TAG_IS_ALLOWED, isAllowed ? "TRUE" : "FALSE"));
            if (inputSatelliteAccessConfigFileName != null) {
                satelliteRegion.appendChild(createElementWithText(doc,
                        SatelliteConfigProtoGenerator.TAG_SATELLITE_ACCESS_CONFIG_FILE,
                        inputSatelliteAccessConfigFileName));
            }
            rootElement.appendChild(satelliteRegion);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(outputFile);
            transformer.transform(source, result);

        } catch (Exception e) {
            throw new RuntimeException("Got exception in creating input file , e=" + e);
        }
    }

    private static Element createElementWithText(Document doc, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(textContent));
        return element;
    }
}
