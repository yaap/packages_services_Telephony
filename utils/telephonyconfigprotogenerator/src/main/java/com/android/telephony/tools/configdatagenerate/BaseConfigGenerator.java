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

/**
 * Abstract base class for configuration generators.
 *
 * <p>This class defines abstracts for parsing XML config data and building protobuf.
 * Subclasses should implement the {@link #parse(Document)} method to extract data from XML
 * and the {@link #build(TelephonyConfigData.TelephonyConfigProto.Builder)} method to
 * populate the protobuf builder.
 */
public abstract class BaseConfigGenerator {

    /**
     * Parse the document and load the data.
     *
     * @param doc the input document.
     */
    public abstract void parse(Document doc);

    /**
     * Build the configuration data into the main builder.
     *
     * @param builder The main TelephonyConfigProto builder.
     */
    public abstract void build(TelephonyConfigData.TelephonyConfigProto.Builder builder);
}

