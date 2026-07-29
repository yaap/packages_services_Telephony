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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.TelephonyConfigData.DataConfigProto;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DataConfigProtoGeneratorTest {

    private DataConfigProtoGenerator mGenerator;

    @Before
    public void setUp() {
        mGenerator = new DataConfigProtoGenerator();
    }

    private Document createDocumentFromXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testParseAndBuild_ValidXml() throws Exception {
        String xml = "<telephony_config>"
                + "<dataconfig>"
                + "  <version>1</version>"
                + "  <connection_capability_configs>"
                + "    <default_connection_capability_config>"
                + "      <rules>10:20:true</rules>"
                + "    </default_connection_capability_config>"
                + "    <carrier_connection_capability_configs>"
                + "      <carrier_id>1001</carrier_id>"
                + "      <rules>30:40:false</rules>"
                + "    </carrier_connection_capability_configs>"
                + "  </connection_capability_configs>"
                + "  <home_metered_capability_configs>"
                + "    <default_metered_capability_config>"
                + "      <capability_ids>123</capability_ids>"
                + "    </default_metered_capability_config>"
                + "    <carrier_metered_capability_configs>"
                + "      <carrier_id>1001</carrier_id>"
                + "      <capability_ids>456</capability_ids>"
                + "    </carrier_metered_capability_configs>"
                + "  </home_metered_capability_configs>"
                + "  <roam_metered_capability_configs>"
                + "    <default_metered_capability_config>"
                + "      <capability_ids>789</capability_ids>"
                + "    </default_metered_capability_config>"
                + "    <carrier_metered_capability_configs>"
                + "      <carrier_id>1001</carrier_id>"
                + "      <capability_ids>101112</capability_ids>"
                + "    </carrier_metered_capability_configs>"
                + "  </roam_metered_capability_configs>"
                + "</dataconfig>"
                + "</telephony_config>";

        Document doc = createDocumentFromXml(xml);
        mGenerator.parse(doc);

        TelephonyConfigData.TelephonyConfigProto.Builder builder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();
        mGenerator.build(builder);

        TelephonyConfigData.TelephonyConfigProto configProto = builder.build();
        assertTrue(configProto.hasData());
        DataConfigProto dataProto = configProto.getData();

        assertEquals(1, dataProto.getVersion());

        // Verify Connection Config
        assertEquals(1, dataProto.getConnectionCapabilityConfigs()
                .getDefaultConnectionCapabilityConfig().getRulesCount());
        assertEquals("10:20:true", dataProto.getConnectionCapabilityConfigs()
                .getDefaultConnectionCapabilityConfig().getRules(0));
        assertEquals(1, dataProto.getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigsCount());
        assertEquals(1001, dataProto.getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigs(0).getCarrierId());
        assertEquals("30:40:false", dataProto.getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigs(0).getRules(0));

        // Verify Home Metered Config
        assertEquals(1, dataProto.getHomeMeteredCapabilityConfigs()
                .getDefaultMeteredCapabilityConfig().getCapabilityIdsCount());
        assertEquals(123, dataProto.getHomeMeteredCapabilityConfigs()
                .getDefaultMeteredCapabilityConfig().getCapabilityIds(0));
        assertEquals(1, dataProto.getHomeMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigsCount());
        assertEquals(1001, dataProto.getHomeMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigs(0).getCarrierId());
        assertEquals(456, dataProto.getHomeMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigs(0).getCapabilityIds(0));

        // Verify Roam Metered Config
        assertEquals(1, dataProto.getRoamMeteredCapabilityConfigs()
                .getDefaultMeteredCapabilityConfig().getCapabilityIdsCount());
        assertEquals(789, dataProto.getRoamMeteredCapabilityConfigs()
                .getDefaultMeteredCapabilityConfig().getCapabilityIds(0));
        assertEquals(1, dataProto.getRoamMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigsCount());
        assertEquals(1001, dataProto.getRoamMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigs(0).getCarrierId());
        assertEquals(101112, dataProto.getRoamMeteredCapabilityConfigs()
                .getCarrierMeteredCapabilityConfigs(0).getCapabilityIds(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_InvalidRule() throws Exception {
        String xml = "<telephony_config><dataconfig><connection_capability_configs>"
                + "<default_connection_capability_config><rules>invalid_rule</rules>"
                + "</default_connection_capability_config></connection_capability_configs>"
                + "</dataconfig></telephony_config>";
        Document doc = createDocumentFromXml(xml);
        mGenerator.parse(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_InvalidCapabilityId() throws Exception {
        String xml = "<telephony_config><dataconfig><home_metered_capability_configs>"
                + "<default_metered_capability_config><capability_ids>-1</capability_ids>"
                + "</default_metered_capability_config></home_metered_capability_configs>"
                + "</dataconfig></telephony_config>";
        Document doc = createDocumentFromXml(xml);
        mGenerator.parse(doc);
    }

    @Test
    public void testParseAndBuild_CarrierConnectionRules() throws Exception {
        String xml = "<telephony_config>"
                + "<dataconfig>"
                + "  <version>1</version>"
                + "  <connection_capability_configs>"
                // Case 1: Empty rules (Carrier 1001)
                + "    <carrier_connection_capability_configs>"
                + "      <carrier_id>1001</carrier_id>"
                + "    </carrier_connection_capability_configs>"
                // Case 2: One rule (Carrier 1002)
                + "    <carrier_connection_capability_configs>"
                + "      <carrier_id>1002</carrier_id>"
                + "      <rules>10:20:true</rules>"
                + "    </carrier_connection_capability_configs>"
                // Case 3: Multiple rules (Carrier 1003)
                + "    <carrier_connection_capability_configs>"
                + "      <carrier_id>1003</carrier_id>"
                + "      <rules>30:40:false</rules>"
                + "      <rules>50:60:true</rules>"
                + "    </carrier_connection_capability_configs>"
                + "  </connection_capability_configs>"
                + "</dataconfig>"
                + "</telephony_config>";

        Document doc = createDocumentFromXml(xml);
        mGenerator.parse(doc);

        TelephonyConfigData.TelephonyConfigProto.Builder builder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();
        mGenerator.build(builder);

        TelephonyConfigData.TelephonyConfigProto configProto = builder.build();
        assertTrue(configProto.hasData());
        DataConfigProto dataProto = configProto.getData();

        assertEquals(1, dataProto.getVersion());
        assertTrue(dataProto.hasConnectionCapabilityConfigs());

        // Verify Carrier configs
        assertEquals(3, dataProto.getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigsCount());

        // Carrier 1001 - Empty rules
        TelephonyConfigData.ConnectionCapabilityMap carrier1 = dataProto
                .getConnectionCapabilityConfigs().getCarrierConnectionCapabilityConfigs(0);
        assertEquals(1001, carrier1.getCarrierId());
        assertEquals(0, carrier1.getRulesCount());

        // Carrier 1002 - One rule
        TelephonyConfigData.ConnectionCapabilityMap carrier2 = dataProto
                .getConnectionCapabilityConfigs().getCarrierConnectionCapabilityConfigs(1);
        assertEquals(1002, carrier2.getCarrierId());
        assertEquals(1, carrier2.getRulesCount());
        assertEquals("10:20:true", carrier2.getRules(0));

        // Carrier 1003 - Multiple rules
        TelephonyConfigData.ConnectionCapabilityMap carrier3 = dataProto
                .getConnectionCapabilityConfigs().getCarrierConnectionCapabilityConfigs(2);
        assertEquals(1003, carrier3.getCarrierId());
        assertEquals(2, carrier3.getRulesCount());
        assertEquals("30:40:false", carrier3.getRules(0));
        assertEquals("50:60:true", carrier3.getRules(1));
    }
}
