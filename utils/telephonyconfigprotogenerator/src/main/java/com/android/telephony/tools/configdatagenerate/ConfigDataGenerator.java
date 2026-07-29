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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Creates a protubuf file **/
public class ConfigDataGenerator {

    /**
     * Creates a protubuf file with user inputs
     */
    public static void main(String[] args) {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        // Refer to the README file for an example of the input XML file
        String inputFile = arguments.inputFile;
        String outputFile = arguments.outputFile;

        Document doc = getDocumentFromInput(inputFile);

        System.out.println("-----------------------------------------------------------------");

        SatelliteConfigProtoGenerator satelliteConfigGenerator =
                new SatelliteConfigProtoGenerator();
        satelliteConfigGenerator.parse(doc);

        DataConfigProtoGenerator dataConfigGenerator = new DataConfigProtoGenerator();
        dataConfigGenerator.parse(doc);

        TelephonyConfigData.TelephonyConfigProto.Builder builder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();

        satelliteConfigGenerator.build(builder);
        dataConfigGenerator.build(builder);

        writeToResultFile(builder, outputFile);

        System.out.println("-----------------------------------------------------------------");
        System.out.println(outputFile + " is generated");
        System.out.println("-----------------------------------------------------------------");
    }

    private static void writeToResultFile(TelephonyConfigData
            .TelephonyConfigProto.Builder telephonyConfigBuilder, String outputFile) {
        try {
            File file = new File(outputFile);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(file);
            TelephonyConfigData.TelephonyConfigProto telephonyConfigData =
                    telephonyConfigBuilder.build();
            telephonyConfigData.writeTo(fos);

            fos.close();
        } catch (Exception e) {
            throw new RuntimeException("Got exception in writing the file "
                    + outputFile + ", e=" + e);
        }
    }

    private static class Arguments {
        @Parameter(names = "--input-file",
                description = "input xml file",
                required = true)
        public String inputFile;

        @Parameter(names = "--output-file",
                description = "out protobuf file",
                required = false)
        public String outputFile = "telephony_config.pb";
    }

    private static Document getDocumentFromInput(String inputFile) {
        File xmlFile = new File(inputFile);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        Document doc = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(xmlFile);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("getDocumentFromInput: e=" + e);
        }
        doc.getDocumentElement().normalize();
        return doc;
    }
}
