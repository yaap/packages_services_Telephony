This directory contains the tool for generating telephony config data protobuf file.

`telephony_configdatagenerator`
- Runs the `telephony_configdatagenerator` to create a binary file of TelephonyConfigProto whose format
  is defined in telephony_config_update.proto
- Command: `telephony_configdatagenerator --input-file <input.xml> --output-file <telephony_config.pb>`
  - `--input-file` input XML file contains input information such as satellite or data config.
  - `--output-file` The created binary TelephonyConfigProto file.
- Build the tools: Go to the tool directory (`packages/services/Telephony/utils/telephonyconfigprotogenerator`)
  in the local workspace and run `mm`.
- Example run command: `telephony_configdatagenerator --input-file input.xml --output-file
  telephony_config.pb`

Run unit tests
=
- Build the tools and test code: Go to the tool directory (`packages/services/Telephony/utils/telephonyconfigprotogenerator`) in the local workspace and run `mm`
- Run unit tests: `$atest TelephonyConfigDataGeneratorTests`

Input File Format
=
The input file is an XML file that contains both satellite configuration and data configuration.
The root element can be named anything, e.g., `<telephony_config>`.

### Example Structure
```xml
<telephony_config>
    <satelliteconfig>
        <version>1</version>
        <carriersupportedservices>
            <carrier_id>1</carrier_id>
            <providercapability>
                <carrier_plmn>45005</carrier_plmn>
                <service>6</service> <!-- 6: MMS, 3: SMS -->
                ...
            </providercapability>
        </carriersupportedservices>
        <carrier_roaming_config>
            <max_allowed_data_mode>1</max_allowed_data_mode>
            <device_satellite_plmn>310062</device_satellite_plmn>
            ...
        </carrier_roaming_config>
        <satelliteregion>
            <s2_cell_file>sats2.dat</s2_cell_file>
            <country_code>US</country_code>
            <is_allowed>true</is_allowed>
            <satellite_access_config_file>satellite_access_config.json</satellite_access_config_file>
        </satelliteregion>
    </satelliteconfig>

    <dataconfig>
        <version>1</version>
        <connection_capability_configs>
            <default_connection_capability_config>
                <rules>12:8:true</rules>
            </default_connection_capability_config>
            <carrier_connection_capability_configs>
                <carrier_id>1839</carrier_id>
                <rules>4:2:false</rules>
            </carrier_connection_capability_configs>
        </connection_capability_configs>
        <home_metered_capability_configs>
            <default_metered_capability_config>
                <capability_ids>1</capability_ids>
            </default_metered_capability_config>
            <carrier_metered_capability_configs>
                <carrier_id>1839</carrier_id>
                <capability_ids>2</capability_ids>
            </carrier_metered_capability_configs>
        </home_metered_capability_configs>
        <roam_metered_capability_configs>
            <default_metered_capability_config>
                <capability_ids>1</capability_ids>
            </default_metered_capability_config>
            <carrier_metered_capability_configs>
                <carrier_id>1839</carrier_id>
                <capability_ids>2</capability_ids>
            </carrier_metered_capability_configs>
        </roam_metered_capability_configs>
    </dataconfig>
</telephony_config>
```

### Element Descriptions

#### Satellite Config Elements (`<satelliteconfig>`)
- `version`: Version number of the satellite config.
- `carriersupportedservices`: Defines supported services for carriers.
    - `carrier_id`: The carrier ID.
    - `providercapability`: Capability of the provider.
        - `carrier_plmn`: PLMN of the carrier.
        - `service`: Supported service type (e.g., 6 for MMS, 3 for SMS).
        - `ntn_connect_type`: (Optional) Satellite connection type for this PLMN.
    - `attach_supported`: (Optional) Whether satellite PLMN scan and attachment are supported.
    - `data_support_mode`: (Optional) Satellite network data traffic support mode (0: Restricted, 1: Bandwidth restricted, 2: Full).
    - `ntn_connect_type`: (Optional) Satellite connection type (0: Automatic, 1: Manual, 2: Hybrid).
    - `emergency_messaging_supported`: (Optional) Whether satellite emergency messaging is supported.
    - `entitlement_supported`: (Optional) Whether satellite entitlement is supported.
    - `entitlement_server_url`: (Optional) The URL of the entitlement server.
- `carrier_roaming_config`: Roaming configuration.
    - `max_allowed_data_mode`: Maximum allowed data mode.
    - `device_satellite_plmn`: List of satellite PLMNs.
    - `override_wfc_roaming_mode_while_using_ntn`: (Optional) Whether to forcefully override the roaming mode of Wi-Fi Calling when connected to NTN.
- `satelliteregion`: Region-specific configuration.
    - `s2_cell_file`: Path to the S2 cell file.
    - `country_code`: Country code (ISO 3166-1 alpha-2).
    - `is_allowed`: Whether satellite service is allowed in this region.
    - `satellite_access_config_file`: Path to the satellite access config JSON file.

#### Data Config Elements (`<dataconfig>`)
- `version`: Version number of the data config.
- `connection_capability_configs`: Configuration for connection capabilities.
    - `default_connection_capability_config`: Default configuration.
        - `rules`: Connection capability rules. Format: `NetworkCapability:ConnectionCapability:ApnRequired`. Example: `12:8:true`.
    - `carrier_connection_capability_configs`: Carrier-specific configuration.
        - `carrier_id`: The carrier ID.
        - `rules`: Connection capability rules. Format: `NetworkCapability:ConnectionCapability:ApnRequired`.
- `home_metered_capability_configs`: Metered capabilities when in home network.
    - `default_metered_capability_config`: Default configuration.
        - `capability_ids`: Metered capability IDs.
    - `carrier_metered_capability_configs`: Carrier-specific configuration.
        - `carrier_id`: The carrier ID.
        - `capability_ids`: Metered capability IDs.
- `roam_metered_capability_configs`: Metered capabilities when roaming.
    - Similar structure to `home_metered_capability_configs`.
