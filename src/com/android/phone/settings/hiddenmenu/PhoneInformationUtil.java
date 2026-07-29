/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.phone.settings.hiddenmenu;

import static android.telephony.CarrierConfigManager.KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_ROAMING_NTN_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_INT;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_NOTIFICATION_HYSTERESIS_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE;
import static android.telephony.CarrierConfigManager.KEY_EMERGENCY_CALL_TO_SATELLITE_T911_HANDOVER_TIMEOUT_MILLIS_INT;
import static android.telephony.CarrierConfigManager.KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_REGIONAL_SATELLITE_EARFCN_BUNDLE;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONFIGS_PER_PLMN_BUNDLE;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTED_NOTIFICATION_THROTTLE_MILLIS_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_DATA_SUPPORT_MODE_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_DISPLAY_NAME_STRING;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_NIDD_APN_NAME_STRING;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_SCREEN_OFF_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_TURN_OFF_SESSION_FOR_EMERGENCY_CALL_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_SOS_MAX_DATAGRAM_SIZE_BYTES_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_SUPPORTED_DISASTER_PLMN_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_SUPPORTED_EMERGENCY_PLMN_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY;

import static com.android.internal.telephony.configupdate.ConfigProviderAdaptor.DOMAIN_SATELLITE;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.satellite.SatelliteManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConfigParser;
import com.android.phone.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PhoneInformationUtil {
    private static final String DSDS_MODE_PROPERTY = "ro.boot.hardware.dsds";
    private static final int ALWAYS_ON_DSDS_MODE = 1;
    private static final String TAG = "PhoneInformationUtil";
    private static SatelliteConfigParser sBackedUpSatelliteConfigParser;
    private static SatelliteConfig sBackedUpSatelliteConfig;
    public static Intent mNonEsosIntent;
    private static CarrierConfigManager sCarrierConfigManager;

    /** Returns whether DSDS is supported. */
    public static boolean isDsdsSupported() {
        return (TelephonyManager.getDefault().isMultiSimSupported()
                == TelephonyManager.MULTISIM_ALLOWED);
    }

    /** Returns whether DSDS is enabled. */
    public static boolean isDsdsEnabled() {
        return TelephonyManager.getDefault().getPhoneCount() > 1;
    }

    /** Returns whether the device is in a DSDS-only mode. */
    public static boolean dsdsModeOnly() {
        String dsdsMode = SystemProperties.get(DSDS_MODE_PROPERTY);
        return !TextUtils.isEmpty(dsdsMode) && Integer.parseInt(dsdsMode) == ALWAYS_ON_DSDS_MODE;
    }

    /**
     * Gets the labels for the phone indexes.
     *
     * @param tm The {@link TelephonyManager} instance.
     * @return An array of strings for each phone index. The array index is equal to the phone
     * index.
     */
    public static String[] getPhoneIndexLabels(TelephonyManager tm) {
        int phones = tm.getActiveModemCount();
        String[] labels = new String[phones];
        for (int i = 0; i < phones; i++) {
            labels[i] = "Phone " + i;
        }
        return labels;
    }

    /**
     * Gets the phone for the given subscription ID.
     */
    public static Phone getPhone(int subId) {
        log("getPhone subId = " + subId);
        Phone phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        if (phone == null) {
            log("return the default phone");
            return PhoneFactory.getDefaultPhone();
        }

        return phone;
    }

    private static String getCellInfoDisplayString(int i) {
        return (i != Integer.MAX_VALUE) ? Integer.toString(i) : "";
    }

    private static String getCellInfoDisplayString(long i) {
        return (i != Long.MAX_VALUE) ? Long.toString(i) : "";
    }

    private static String getConnectionStatusString(CellInfo ci) {
        String regStr = "";
        String connector = "";

        if (ci.isRegistered()) {
            regStr = "R";
        }
        String connStatStr = switch (ci.getCellConnectionStatus()) {
            case CellInfo.CONNECTION_PRIMARY_SERVING -> "P";
            case CellInfo.CONNECTION_SECONDARY_SERVING -> "S";
            case CellInfo.CONNECTION_NONE -> "N";
            case CellInfo.CONNECTION_UNKNOWN -> "";
            default -> "";
        };
        if (!TextUtils.isEmpty(regStr) && !TextUtils.isEmpty(connStatStr)) {
            connector = "+";
        }

        return regStr + connector + connStatStr;
    }

    private static String buildGsmInfoString(CellInfoGsm ci) {
        CellIdentityGsm cidGsm = ci.getCellIdentity();
        CellSignalStrengthGsm ssGsm = ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-4.4s %-4.4s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidGsm.getMcc()),
                getCellInfoDisplayString(cidGsm.getMnc()),
                getCellInfoDisplayString(cidGsm.getLac()),
                getCellInfoDisplayString(cidGsm.getCid()),
                getCellInfoDisplayString(cidGsm.getArfcn()),
                getCellInfoDisplayString(cidGsm.getBsic()),
                getCellInfoDisplayString(ssGsm.getDbm()));
    }

    private static String buildLteInfoString(CellInfoLte ci) {
        CellIdentityLte cidLte = ci.getCellIdentity();
        CellSignalStrengthLte ssLte = ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s %-6.6s %-2.2s %-4.4s %-4.4s %-2.2s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidLte.getMcc()),
                getCellInfoDisplayString(cidLte.getMnc()),
                getCellInfoDisplayString(cidLte.getTac()),
                getCellInfoDisplayString(cidLte.getCi()),
                getCellInfoDisplayString(cidLte.getPci()),
                getCellInfoDisplayString(cidLte.getEarfcn()),
                getCellInfoDisplayString(cidLte.getBandwidth()),
                getCellInfoDisplayString(ssLte.getDbm()),
                getCellInfoDisplayString(ssLte.getRsrq()),
                getCellInfoDisplayString(ssLte.getTimingAdvance()));
    }

    private static String buildNrInfoString(CellInfoNr ci) {
        CellIdentityNr cidNr = (CellIdentityNr) ci.getCellIdentity();
        CellSignalStrengthNr ssNr = (CellSignalStrengthNr) ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s %-7.7s %-7.7s %-7.7s\n",
                getConnectionStatusString(ci),
                cidNr.getMccString(),
                cidNr.getMncString(),
                getCellInfoDisplayString(cidNr.getTac()),
                getCellInfoDisplayString(cidNr.getNci()),
                getCellInfoDisplayString(cidNr.getPci()),
                getCellInfoDisplayString(cidNr.getNrarfcn()),
                getCellInfoDisplayString(ssNr.getSsRsrp()),
                getCellInfoDisplayString(ssNr.getSsRsrq()));
    }

    private static String buildWcdmaInfoString(CellInfoWcdma ci) {
        CellIdentityWcdma cidWcdma = ci.getCellIdentity();
        CellSignalStrengthWcdma ssWcdma = ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-3.3s %-4.4s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidWcdma.getMcc()),
                getCellInfoDisplayString(cidWcdma.getMnc()),
                getCellInfoDisplayString(cidWcdma.getLac()),
                getCellInfoDisplayString(cidWcdma.getCid()),
                getCellInfoDisplayString(cidWcdma.getUarfcn()),
                getCellInfoDisplayString(cidWcdma.getPsc()),
                getCellInfoDisplayString(ssWcdma.getDbm()));
    }

    /**
     * Builds a string representation of a list of {@link CellInfo} objects.
     *
     * @param arrayCi The list of {@link CellInfo} objects.
     * @return A string representation of the cell info.
     */
    public static String buildCellInfoString(java.util.List<CellInfo> arrayCi) {
        String value = new String();
        StringBuilder gsmCells = new StringBuilder(),
                lteCells = new StringBuilder(),
                wcdmaCells = new StringBuilder(),
                nrCells = new StringBuilder();

        if (arrayCi != null) {
            for (CellInfo ci : arrayCi) {

                if (ci instanceof CellInfoLte) {
                    lteCells.append(buildLteInfoString((CellInfoLte) ci));
                } else if (ci instanceof CellInfoWcdma) {
                    wcdmaCells.append(buildWcdmaInfoString((CellInfoWcdma) ci));
                } else if (ci instanceof CellInfoGsm) {
                    gsmCells.append(buildGsmInfoString((CellInfoGsm) ci));
                } else if (ci instanceof CellInfoNr) {
                    nrCells.append(buildNrInfoString((CellInfoNr) ci));
                }
            }
            if (nrCells.length() != 0) {
                value +=
                        String.format(
                                "NR\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s"
                                        + " %-7.7s %-7.7s %-7.7s\n",
                                "SRV", "MCC", "MNC", "TAC", "NCI", "PCI", "NRARFCN", "SS-RSRP",
                                "SS-RSRQ");
                value += nrCells.toString();
            }

            if (lteCells.length() != 0) {
                value +=
                        String.format(
                                "LTE\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s"
                                        + " %-6.6s %-2.2s %-4.4s %-4.4s %-2.2s\n",
                                "SRV", "MCC", "MNC", "TAC", "CID", "PCI", "EARFCN", "BW", "RSRP",
                                "RSRQ", "TA");
                value += lteCells.toString();
            }
            if (wcdmaCells.length() != 0) {
                value +=
                        String.format(
                                "WCDMA\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-3.3s %-4.4s\n",
                                "SRV", "MCC", "MNC", "LAC", "CID", "UARFCN", "PSC", "RSCP");
                value += wcdmaCells.toString();
            }
            if (gsmCells.length() != 0) {
                value +=
                        String.format(
                                "GSM\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-4.4s %-4.4s\n",
                                "SRV", "MCC", "MNC", "LAC", "CID", "ARFCN", "BSIC", "RSSI");
                value += gsmCells.toString();
            }
        } else {
            value = "unknown";
        }

        return value.toString();
    }

    /**
     * Returns whether voice service is available over IMS.
     *
     * @param imsMmTelManager The {@link ImsMmTelManager} instance.
     * @return {@code true} if voice service is available, {@code false} otherwise.
     */
    public static boolean isVoiceServiceAvailable(ImsMmTelManager imsMmTelManager) {
        if (imsMmTelManager == null) {
            return false;
        }

        final int[] radioTechs = {
            ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
            ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM,
            ImsRegistrationImplBase.REGISTRATION_TECH_NR,
            ImsRegistrationImplBase.REGISTRATION_TECH_3G
        };

        boolean isAvailable = false;
        for (int tech : radioTechs) {
            try {
                isAvailable |= imsMmTelManager.isAvailable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE, tech);
                if (isAvailable) {
                    break;
                }
            } catch (Exception e) {
                loge("isVoiceServiceAvailable: exception=" + e);
            }
        }
        return isAvailable;
    }

    /**
     * Returns whether video service is available over IMS.
     *
     * @param imsMmTelManager The {@link ImsMmTelManager} instance.
     * @return {@code true} if video service is available, {@code false} otherwise.
     */
    public static boolean isVideoServiceAvailable(ImsMmTelManager imsMmTelManager) {
        if (imsMmTelManager == null) {
            return false;
        }

        final int[] radioTechs = {
            ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
            ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
            ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM,
            ImsRegistrationImplBase.REGISTRATION_TECH_NR,
            ImsRegistrationImplBase.REGISTRATION_TECH_3G
        };

        boolean isAvailable = false;
        for (int tech : radioTechs) {
            try {
                isAvailable |= imsMmTelManager.isAvailable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO, tech);
                if (isAvailable) {
                    break;
                }
            } catch (Exception e) {
                loge("isVideoServiceAvailable: exception=" + e);
            }
        }
        return isAvailable;
    }

    /**
     * Returns whether Wi-Fi calling service is available.
     *
     * @param imsMmTelManager The {@link ImsMmTelManager} instance.
     * @return {@code true} if Wi-Fi calling is available, {@code false} otherwise.
     */
    public static boolean isWfcServiceAvailable(ImsMmTelManager imsMmTelManager) {
        if (imsMmTelManager == null) {
            return false;
        }

        boolean isAvailable = false;
        try {
            isAvailable = imsMmTelManager.isAvailable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        } catch (Exception e) {
            loge("isWfcServiceAvailable: exception=" + e);
        }
        return isAvailable;
    }

    public static final String[] PREFERRED_NETWORK_LABELS_RF = {
            "GSM/WCDMA preferred",
            "GSM only",
            "WCDMA only",
            "GSM/WCDMA auto (PRL)",
            "GSM/WCDMA/LTE (PRL)",
            "LTE only",
            "LTE/WCDMA",
            "TDSCDMA only",
            "TDSCDMA/WCDMA",
            "LTE/TDSCDMA",
            "TDSCDMA/GSM",
            "LTE/TDSCDMA/GSM",
            "TDSCDMA/GSM/WCDMA",
            "LTE/TDSCDMA/WCDMA",
            "LTE/TDSCDMA/GSM/WCDMA",
            "NR only",
            "NR/LTE",
            "NR/LTE/GSM/WCDMA",
            "NR/LTE/WCDMA",
            "NR/LTE/TDSCDMA",
            "NR/LTE/TDSCDMA/GSM",
            "NR/LTE/TDSCDMA/WCDMA",
            "NR/LTE/TDSCDMA/GSM/WCDMA",
            "Unknown"
    };

    public static final List<Integer> PREFERRED_NETWORK_MODES_RF = Arrays.asList(
            RILConstants.NETWORK_MODE_WCDMA_PREF,
            RILConstants.NETWORK_MODE_GSM_ONLY,
            RILConstants.NETWORK_MODE_WCDMA_ONLY,
            RILConstants.NETWORK_MODE_GSM_UMTS,
            RILConstants.NETWORK_MODE_LTE_GSM_WCDMA,
            RILConstants.NETWORK_MODE_LTE_ONLY,
            RILConstants.NETWORK_MODE_LTE_WCDMA,
            RILConstants.NETWORK_MODE_TDSCDMA_ONLY,
            RILConstants.NETWORK_MODE_TDSCDMA_WCDMA,
            RILConstants.NETWORK_MODE_LTE_TDSCDMA,
            RILConstants.NETWORK_MODE_TDSCDMA_GSM,
            RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM,
            RILConstants.NETWORK_MODE_TDSCDMA_GSM_WCDMA,
            RILConstants.NETWORK_MODE_LTE_TDSCDMA_WCDMA,
            RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA,
            RILConstants.NETWORK_MODE_NR_ONLY,
            RILConstants.NETWORK_MODE_NR_LTE,
            RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA,
            RILConstants.NETWORK_MODE_NR_LTE_WCDMA,
            RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA,
            RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM,
            RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA,
            RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA,
            -1  // Unknown
    );

    public static final Integer[]SIGNAL_STRENGTH_LEVEL =
            new Integer[] {
                -1 /*clear mock*/,
                CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN,
                CellSignalStrength.SIGNAL_STRENGTH_POOR,
                CellSignalStrength.SIGNAL_STRENGTH_MODERATE,
                CellSignalStrength.SIGNAL_STRENGTH_GOOD,
                CellSignalStrength.SIGNAL_STRENGTH_GREAT
            };

    public static final Integer[] MOCK_DATA_NETWORK_TYPE =
            new Integer[] {
                -1 /*clear mock*/,
                ServiceState.RIL_RADIO_TECHNOLOGY_GPRS,
                ServiceState.RIL_RADIO_TECHNOLOGY_EDGE,
                ServiceState.RIL_RADIO_TECHNOLOGY_UMTS,
                ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA,
                ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA,
                ServiceState.RIL_RADIO_TECHNOLOGY_HSPA,
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE,
                ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP,
                ServiceState.RIL_RADIO_TECHNOLOGY_GSM,
                ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA,
                ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA,
                ServiceState.RIL_RADIO_TECHNOLOGY_NR
            };

    /**
     * Uncaps the maximum allowed data mode for satellite communication.
     *
     * <p>This method overrides the satellite configuration to allow all data support modes. It
     * backs up the current satellite configuration before applying the override.
     */
    public static void uncapMaxAllowedDataMode() {
        log("uncapMaxAllowedDataMode: uncap max allowed data mode by overriding satellite config");
        SatelliteConfigParser satelliteConfigParser =
                (SatelliteConfigParser)
                        TelephonyConfigUpdateInstallReceiver.getInstance()
                                .getConfigParser(DOMAIN_SATELLITE);
        SatelliteConfig satelliteConfig =
                satelliteConfigParser != null ? satelliteConfigParser.getConfig() : null;

        log("uncapMaxAllowedDataMode: backing up satellite config parser: "
                + satelliteConfigParser);
        sBackedUpSatelliteConfigParser = satelliteConfigParser;

        log("uncapMaxAllowedDataMode: backing up satellite config: " + satelliteConfig);
        sBackedUpSatelliteConfig = satelliteConfig;

        SatelliteConfig uncappedSatelliteConfig;
        if (satelliteConfig == null) {
            log("uncapMaxAllowedDataMode: satelliteConfig is null, creating new SatelliteConfig"
                    + " just to uncap max allowed data mode");
            uncappedSatelliteConfig = new SatelliteConfig();
        } else {
            log("uncapMaxAllowedDataMode: satelliteConfig is not null, make a deepcopy just to"
                    + " uncap max allowed data mode");
            uncappedSatelliteConfig = new SatelliteConfig(satelliteConfig);
        }
        uncappedSatelliteConfig.overrideSatelliteMaxAllowedDataMode(
                CarrierConfigManager.SATELLITE_DATA_SUPPORT_ALL);

        log(
                "uncapMaxAllowedDataMode: creating uncappedSatelliteConfigParser to uncap max"
                        + " allowed data mode");
        SatelliteConfigParser uncappedSatelliteConfigParser =
                new SatelliteConfigParser(new byte[] {});

        uncappedSatelliteConfigParser.overrideConfig(uncappedSatelliteConfig);
        TelephonyConfigUpdateInstallReceiver.getInstance()
                .overrideConfigParser(uncappedSatelliteConfigParser);
    }

    /**
     * Restores the original maximum allowed data mode for satellite communication.
     *
     * <p>This method restores the satellite configuration that was backed up before {@link
     * #uncapMaxAllowedDataMode()} was called.
     */
    public static void restoreMaxAllowedDataMode() {
        log("restoreMaxAllowedDataMode: restoring max allowed data mode by restoring the backed"
                + " up satellite config parser: " + sBackedUpSatelliteConfigParser + " and config: "
                + sBackedUpSatelliteConfig);
        if (sBackedUpSatelliteConfigParser == null) {
            log("restoreMaxAllowedDataMode: mBackedUpSatelliteConfigParser is null, therefore"
                    + " don't have to override mBackedUpSatelliteConfig, as it would null" + " as"
                    + " well");
            TelephonyConfigUpdateInstallReceiver.getInstance().clearOverriddenConfigParser(
                    DOMAIN_SATELLITE);
            return;
        }
        TelephonyConfigUpdateInstallReceiver.getInstance().overrideConfigParser(
                sBackedUpSatelliteConfigParser);
        TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE).overrideConfig(sBackedUpSatelliteConfig);
    }

    /**
     * Determines whether the UI for starting a non-emergency satellite session should be displayed.
     *
     * <p>This method performs several checks to validate if the feature is enabled and correctly
     * configured:
     * <ul>
     *     <li>The build must be debuggable.</li>
     *     <li>The carrier configuration for the given subscription must indicate support for both
     *         satellite attach and ESOS (Emergency SOS).</li>
     *     <li>The system overlays must define a valid package and class name for the satellite
     *         gateway service and the non-emergency session receiver.</li>
     *     <li>A {@link android.content.BroadcastReceiver} must be available to handle the
     *         {@link SatelliteManager#ACTION_SATELLITE_START_NON_EMERGENCY_SESSION} intent.</li>
     * </ul>
     *
     * <p>As a side effect, if all conditions are met, this method populates the static
     * {@link #mNonEsosIntent} field with the created {@link Intent} for later use.
     *
     * @param context The {@link Context} to access system services and resources.
     * @param mSubId The subscription ID to check carrier configurations against.
     * @return {@code true} if the non-emergency mode UI should be displayed, {@code false} if it
     *         should be hidden.
     */
    public static boolean shouldHideNonEmergencyMode(Context context, int mSubId) {
        if (!Build.isDebuggable()) {
            return true;
        }
        String action = SatelliteManager.ACTION_SATELLITE_START_NON_EMERGENCY_SESSION;
        if (TextUtils.isEmpty(action)) {
            return true;
        }
        if (mNonEsosIntent != null) {
            mNonEsosIntent = null;
        }
        CarrierConfigManager carrierConfigManager =
                context.getSystemService(CarrierConfigManager.class);
        if (carrierConfigManager == null) {
            loge("shouldHideNonEmergencyMode: cm is null");
            return true;
        }
        android.os.PersistableBundle bundle = carrierConfigManager.getConfigForSubId(mSubId,
                KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL);
        if (!bundle.getBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, false)) {
            log("shouldHideNonEmergencyMode: esos_supported false");
            return true;
        }
        if (!bundle.getBoolean(KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, false)) {
            log("shouldHideNonEmergencyMode: attach_supported false");
            return true;
        }

        String packageName = getStringFromOverlayConfig(context,
                com.android.internal.R.string.config_satellite_gateway_service_package);

        String className = getStringFromOverlayConfig(context,
                com.android.internal
                        .R.string.config_satellite_carrier_roaming_non_emergency_session_class);
        if (packageName == null || className == null || packageName.isEmpty()
                || className.isEmpty()) {
            log("shouldHideNonEmergencyMode:" + " packageName or className is null or empty.");
            return true;
        }
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(packageName, className));
        if (pm.queryBroadcastReceivers(intent, 0).isEmpty()) {
            log("shouldHideNonEmergencyMode: Broadcast receiver not found for intent: " + intent);
            return true;
        }
        mNonEsosIntent = intent;
        return false;
    }

    /**
     * Method will create the PersistableBundle and pack the satellite services like
     * SMS, MMS, EMERGENCY CALL, DATA in it.
     *
     * @param telephonyManager The TelephonyManager instance.
     * @param phoneId The phone ID.
     * @param subId The subscription ID.
     * @param originalBundle The original PersistableBundle.
     * @return A new PersistableBundle with satellite services.
     */
    public static PersistableBundle getSatelliteServicesBundleForOperatorPlmn(
            TelephonyManager telephonyManager,
            int phoneId,
            int subId,
            PersistableBundle originalBundle) {
        String plmn = telephonyManager.getNetworkOperatorForPhone(phoneId);
        if (TextUtils.isEmpty(plmn)) {
            loge("satData: NetworkOperator PLMN is empty");
            plmn = telephonyManager.getSimOperatorNumeric(subId);
            loge("satData: SimOperator PLMN = " + plmn);
        }
        int[] supportedServicesArray = {
            NetworkRegistrationInfo.SERVICE_TYPE_DATA,
            NetworkRegistrationInfo.SERVICE_TYPE_SMS,
            NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY,
            NetworkRegistrationInfo.SERVICE_TYPE_MMS
        };

        PersistableBundle satServicesPerBundle = originalBundle.getPersistableBundle(
                KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE);
        // New bundle is required, as existed one will throw `ArrayMap is immutable` when we try
        // to modify.
        PersistableBundle newSatServicesPerBundle = new PersistableBundle();
        //Copy the values from the old bundle into the new bundle.
        boolean hasPlmnKey = false;
        if (satServicesPerBundle != null) {
            for (String key : satServicesPerBundle.keySet()) {
                if (!TextUtils.isEmpty(key) && key.equalsIgnoreCase(plmn)) {
                    newSatServicesPerBundle.putIntArray(plmn, supportedServicesArray);
                    hasPlmnKey = true;
                } else {
                    newSatServicesPerBundle.putIntArray(key, satServicesPerBundle.getIntArray(key));
                }
            }
        }
        if (!hasPlmnKey) {
            newSatServicesPerBundle.putIntArray(plmn, supportedServicesArray);
        }
        log("satData: New SatelliteServicesBundle = " + newSatServicesPerBundle);
        return newSatServicesPerBundle;
    }

    /**
     *This method will check the required carrier config keys which plays role in enabling /
     * supporting satellite data and update the keys accordingly.
     *
     * @param carrierConfigManager The CarrierConfigManager instance.
     * @param subId The subscription ID.
     * @param bundleToModify The PersistableBundle to modify.
     */
    public static void updateCarrierConfigToSupportData(
            CarrierConfigManager carrierConfigManager,
            int subId,
            PersistableBundle bundleToModify) {
        int[] availableServices = bundleToModify.getIntArray(
                KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY);
        int[] newServices;
        if (availableServices != null && availableServices.length > 0) {
            if (Arrays.stream(availableServices)
                    .anyMatch(element -> element == NetworkRegistrationInfo.SERVICE_TYPE_DATA)) {
                newServices = new int[availableServices.length];
                System.arraycopy(availableServices, 0, newServices, 0, availableServices.length);
            } else {
                newServices = new int[availableServices.length + 1];
                System.arraycopy(availableServices, 0, newServices, 0, availableServices.length);
                newServices[newServices.length - 1] = NetworkRegistrationInfo.SERVICE_TYPE_DATA;
            }
        } else {
            newServices = new int[1];
            newServices[0] = NetworkRegistrationInfo.SERVICE_TYPE_DATA;
        }
        bundleToModify.putIntArray(
                KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY, newServices);
        bundleToModify.putBoolean(KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);
        bundleToModify.remove(KEY_SATELLITE_DATA_SUPPORT_MODE_INT);
        log("satData: changing carrierConfig to : " + bundleToModify);
        carrierConfigManager.overrideConfig(subId, bundleToModify, false);
    }

    private static String getStringFromOverlayConfig(Context context, int resourceId) {
        String name;
        try {
            name = context.getResources().getString(resourceId);
        } catch (Resources.NotFoundException ex) {
            loge("getStringFromOverlayConfig: ex=" + ex);
            name = null;
        }
        return name;
    }

    /**
     * Gets a cached instance of the {@link CarrierConfigManager}.
     *
     * <p>This method lazily initializes the {@link CarrierConfigManager} on the first call
     * and returns the cached instance on subsequent calls to avoid repeated lookups.
     *
     * @param context The {@link Context} used to retrieve the system service.
     * @return The singleton {@link CarrierConfigManager} instance.
     */
    public static CarrierConfigManager getCarrierConfig(Context context) {
        if (sCarrierConfigManager == null) {
            sCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        }
        return sCarrierConfigManager;
    }

    /**
     * Configures the visibility and labels of phone selection buttons and titles.
     *
     * <p>This method adjusts the UI based on the number of active modems. If two phones are
     * active, it displays and labels both selection buttons. If only one is active, it shows
     * a single button. If no phones are active, all selection buttons are hidden.
     *
     * @param phoneButton0     The button for selecting the first phone (Phone 0).
     * @param phoneButton1     The button for selecting the second phone (Phone 1).
     * @param phoneTitle0      The TextView displaying the label for the first phone.
     * @param phoneTitle1      The TextView displaying the label for the second phone.
     * @param phoneIndexLabels An array of labels for each phone, e.g., ["Phone 0", "Phone 1"].
     */
    public static void configurePhoneSelectionUi(LinearLayout phoneButton0,
            LinearLayout phoneButton1, TextView phoneTitle0, TextView phoneTitle1,
            String[] phoneIndexLabels) {
        Context context = phoneButton0.getContext();
        boolean phone0Restricted = isRadioInfoRestricted(context, 0);
        boolean phone1Restricted = isRadioInfoRestricted(context, 1);

        if (phoneIndexLabels.length > 1) {
            phoneTitle0.setText(phoneIndexLabels[0]);
            phoneTitle1.setText(phoneIndexLabels[1]);
            phoneButton0.setVisibility(phone0Restricted ? View.GONE : View.VISIBLE);
            phoneButton1.setVisibility(phone1Restricted ? View.GONE : View.VISIBLE);
        } else if (phoneIndexLabels.length == 1) {
            phoneTitle0.setText(phoneIndexLabels[0]);
            phoneButton0.setVisibility(phone0Restricted ? View.GONE : View.VISIBLE);
            phoneButton1.setVisibility(View.GONE);
        } else {
            phoneButton0.setVisibility(View.GONE);
            phoneButton1.setVisibility(View.GONE);
        }
    }

    private static void log(String s) {
        Log.d(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    // Starlink configs
    public static final int SATELLITE_CHANNEL_STARLINK_US = 8665;
    public static final int[] STARLINK_CHANNELS = {SATELLITE_CHANNEL_STARLINK_US};
    public static final int[] STARLINK_BAND = {AccessNetworkConstants.EutranBand.BAND_25};

    // AST configs
    public static final int SATELLITE_CHANNEL_AST_US_1 = 2625;
    public static final int SATELLITE_CHANNEL_AST_US_2 = 2630;
    public static final int[] AST_CHANNELS = {SATELLITE_CHANNEL_AST_US_1,
            SATELLITE_CHANNEL_AST_US_2};
    public static final int[] AST_BAND = {AccessNetworkConstants.EutranBand.BAND_5};

    public static final Integer[] BAND_VALUES =
            new Integer[]{
                    -1,
                    AccessNetworkConstants.EutranBand.BAND_1,
                    AccessNetworkConstants.EutranBand.BAND_2,
                    AccessNetworkConstants.EutranBand.BAND_3,
                    AccessNetworkConstants.EutranBand.BAND_4,
                    AccessNetworkConstants.EutranBand.BAND_5,
                    AccessNetworkConstants.EutranBand.BAND_6,
                    AccessNetworkConstants.EutranBand.BAND_7,
                    AccessNetworkConstants.EutranBand.BAND_8,
                    AccessNetworkConstants.EutranBand.BAND_9,
                    AccessNetworkConstants.EutranBand.BAND_10,
                    AccessNetworkConstants.EutranBand.BAND_11,
                    AccessNetworkConstants.EutranBand.BAND_12,
                    AccessNetworkConstants.EutranBand.BAND_13,
                    AccessNetworkConstants.EutranBand.BAND_14,
                    AccessNetworkConstants.EutranBand.BAND_17,
                    AccessNetworkConstants.EutranBand.BAND_18,
                    AccessNetworkConstants.EutranBand.BAND_19,
                    AccessNetworkConstants.EutranBand.BAND_20,
                    AccessNetworkConstants.EutranBand.BAND_21,
                    AccessNetworkConstants.EutranBand.BAND_22,
                    AccessNetworkConstants.EutranBand.BAND_23,
                    AccessNetworkConstants.EutranBand.BAND_24,
                    AccessNetworkConstants.EutranBand.BAND_25,
                    AccessNetworkConstants.EutranBand.BAND_26,
                    AccessNetworkConstants.EutranBand.BAND_27,
                    AccessNetworkConstants.EutranBand.BAND_28,
                    AccessNetworkConstants.EutranBand.BAND_30,
                    AccessNetworkConstants.EutranBand.BAND_31,
                    AccessNetworkConstants.EutranBand.BAND_33,
                    AccessNetworkConstants.EutranBand.BAND_34,
                    AccessNetworkConstants.EutranBand.BAND_35,
                    AccessNetworkConstants.EutranBand.BAND_36,
                    AccessNetworkConstants.EutranBand.BAND_37,
                    AccessNetworkConstants.EutranBand.BAND_38,
                    AccessNetworkConstants.EutranBand.BAND_39,
                    AccessNetworkConstants.EutranBand.BAND_40,
                    AccessNetworkConstants.EutranBand.BAND_41,
                    AccessNetworkConstants.EutranBand.BAND_42,
                    AccessNetworkConstants.EutranBand.BAND_43,
                    AccessNetworkConstants.EutranBand.BAND_44,
                    AccessNetworkConstants.EutranBand.BAND_45,
                    AccessNetworkConstants.EutranBand.BAND_46,
                    AccessNetworkConstants.EutranBand.BAND_47,
                    AccessNetworkConstants.EutranBand.BAND_48,
                    AccessNetworkConstants.EutranBand.BAND_49,
                    AccessNetworkConstants.EutranBand.BAND_50,
                    AccessNetworkConstants.EutranBand.BAND_51,
                    AccessNetworkConstants.EutranBand.BAND_52,
                    AccessNetworkConstants.EutranBand.BAND_53,
                    AccessNetworkConstants.EutranBand.BAND_65,
                    AccessNetworkConstants.EutranBand.BAND_66,
                    AccessNetworkConstants.EutranBand.BAND_68,
                    AccessNetworkConstants.EutranBand.BAND_70,
                    AccessNetworkConstants.EutranBand.BAND_71,
                    AccessNetworkConstants.EutranBand.BAND_72,
                    AccessNetworkConstants.EutranBand.BAND_73,
                    AccessNetworkConstants.EutranBand.BAND_74,
                    AccessNetworkConstants.EutranBand.BAND_85,
                    AccessNetworkConstants.EutranBand.BAND_87,
                    AccessNetworkConstants.EutranBand.BAND_88
            };

    public static final String[] BAND_LABELS = {
            "SELECT", "BAND_1", "BAND_2", "BAND_3", "BAND_4", "BAND_5", "BAND_6", "BAND_7",
            "BAND_8", "BAND_9", "BAND_10", "BAND_11", "BAND_12", "BAND_13", "BAND_14", "BAND_17",
            "BAND_18", "BAND_19", "BAND_20", "BAND_21", "BAND_22", "BAND_23", "BAND_24", "BAND_25",
            "BAND_26", "BAND_27", "BAND_28", "BAND_30", "BAND_31", "BAND_33", "BAND_34", "BAND_35",
            "BAND_36", "BAND_37", "BAND_38", "BAND_39", "BAND_40", "BAND_41", "BAND_42", "BAND_43",
            "BAND_44", "BAND_45", "BAND_46", "BAND_47", "BAND_48", "BAND_49", "BAND_50", "BAND_51",
            "BAND_52", "BAND_53", "BAND_65", "BAND_66", "BAND_68", "BAND_70", "BAND_71", "BAND_72",
            "BAND_73", "BAND_74", "BAND_85", "BAND_87", "BAND_88"
    };

    public static final String KEY_SATELLITE_BANDS = "force_camp_satellite_bands";
    public static final String KEY_FORCE_CAMP_SATELLITE_BAND_SELECTED =
            "force_camp_satellite_band_selected";
    public static final String KEY_SATELLITE_CHANNELS = "force_camp_satellite_channels";

    /**
     * Utility function to take an old APN setting and a Infrastructure bitmask value
     * to construct new APN setting to be updated with the infrastructure bitmask value
     * to allow satellite on current APN
     *
     * @param oldApn
     * @param newInfrastructureBitmask
     * @return newApnSetting with correct new bitmask and other old values
     */
    public static ApnSetting createUpdatedApnSetting(ApnSetting oldApn,
            int newInfrastructureBitmask) {
        if (oldApn == null) {
            return null;
        }
        return new ApnSetting.Builder()
                .setEntryName(oldApn.getEntryName())
                .setApnName(oldApn.getApnName())
                .setProxyAddress(oldApn.getProxyAddressAsString())
                .setProxyPort(oldApn.getProxyPort())
                .setMmsc(oldApn.getMmsc())
                .setMmsProxyAddress(oldApn.getMmsProxyAddressAsString())
                .setMmsProxyPort(oldApn.getMmsProxyPort())
                .setUser(oldApn.getUser())
                .setPassword(oldApn.getPassword())
                .setAuthType(oldApn.getAuthType())
                .setApnTypeBitmask(oldApn.getApnTypeBitmask())
                .setOperatorNumeric(oldApn.getOperatorNumeric())
                .setProtocol(oldApn.getProtocol())
                .setRoamingProtocol(oldApn.getRoamingProtocol())
                .setMtuV4(oldApn.getMtuV4())
                .setMtuV6(oldApn.getMtuV6())
                .setCarrierEnabled(oldApn.isEnabled())
                .setNetworkTypeBitmask(oldApn.getNetworkTypeBitmask())
                .setLingeringNetworkTypeBitmask(oldApn.getLingeringNetworkTypeBitmask())
                .setProfileId(oldApn.getProfileId())
                .setPersistent(oldApn.isPersistent())
                .setMaxConns(oldApn.getMaxConns())
                .setWaitTime(oldApn.getWaitTime())
                .setMaxConnsTime(oldApn.getMaxConnsTime())
                .setMvnoType(oldApn.getMvnoType())
                .setMvnoMatchData(oldApn.getMvnoMatchData())
                .setApnSetId(oldApn.getApnSetId())
                .setCarrierId(oldApn.getCarrierId())
                .setSkip464Xlat(oldApn.getSkip464Xlat())
                .setAlwaysOn(oldApn.isAlwaysOn())
                .setInfrastructureBitmask(newInfrastructureBitmask)
                .setEsimBootstrapProvisioning(oldApn.isEsimBootstrapProvisioning())
                .build();
    }

    // In PhoneInformationUtil.java

    private static String apnTypesToString(int bitmask) {
        List<String> types = new ArrayList<>();
        if ((bitmask & ApnSetting.TYPE_DEFAULT) == ApnSetting.TYPE_DEFAULT) {
            types.add("DEFAULT");
        }
        if ((bitmask & ApnSetting.TYPE_MMS) == ApnSetting.TYPE_MMS) {
            types.add("MMS");
        }
        if ((bitmask & ApnSetting.TYPE_SUPL) == ApnSetting.TYPE_SUPL) {
            types.add("SUPL");
        }
        if ((bitmask & ApnSetting.TYPE_DUN) == ApnSetting.TYPE_DUN) {
            types.add("DUN");
        }
        if ((bitmask & ApnSetting.TYPE_HIPRI) == ApnSetting.TYPE_HIPRI) {
            types.add("HIPRI");
        }
        if ((bitmask & ApnSetting.TYPE_FOTA) == ApnSetting.TYPE_FOTA) {
            types.add("FOTA");
        }
        if ((bitmask & ApnSetting.TYPE_IMS) == ApnSetting.TYPE_IMS) {
            types.add("IMS");
        }
        if ((bitmask & ApnSetting.TYPE_CBS) == ApnSetting.TYPE_CBS) {
            types.add("CBS");
        }
        if ((bitmask & ApnSetting.TYPE_IA) == ApnSetting.TYPE_IA) {
            types.add("IA");
        }
        if ((bitmask & ApnSetting.TYPE_EMERGENCY) == ApnSetting.TYPE_EMERGENCY) {
            types.add("EMERGENCY");
        }
        return TextUtils.join(", ", types);
    }


    /**
     *
     * Update Infrastructure bitmask value in APN setting to enable satellite
     *
     * @param context
     * @param subId
     * @param logTag
     * @return
     */
    public static List<ContentValues> updateApnInfrastructureBitmaskForSatellite(
            Context context, int subId, String logTag) {
        List<ContentValues> originalApnSettings = new ArrayList<>();
        try {
            ContentResolver resolver = context.getContentResolver();
            // Use the modern SIM_APN_URI, which is aware of the current subscription.
            Uri uri = Telephony.Carriers.SIM_APN_URI;
            Cursor cursor = resolver.query(uri, null,
                    Telephony.Carriers.CARRIER_ENABLED + " = 1", null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ApnSetting oldApn = ApnSetting.makeApnSetting(cursor);

                    Log.d(logTag, "UpdateAPN: Checking APN: " + oldApn.getApnName()
                            + ", Types: " + apnTypesToString(oldApn.getApnTypeBitmask())
                            + ", Infrastructure Bitmask: " + oldApn.getInfrastructureBitmask());

                    if ((oldApn.canHandleType(ApnSetting.TYPE_DEFAULT))
                            || (oldApn.canHandleType(ApnSetting.TYPE_IA))) {

                        if ((oldApn.getInfrastructureBitmask()
                                & ApnSetting.INFRASTRUCTURE_SATELLITE) == 0) {
                            ContentValues originalValues = new ContentValues();
                            DatabaseUtils.cursorRowToContentValues(cursor, originalValues);
                            originalApnSettings.add(originalValues);

                            int newInfrastructureBitmask =
                                    oldApn.getInfrastructureBitmask()
                                            | ApnSetting.INFRASTRUCTURE_SATELLITE;
                            ApnSetting newApn = createUpdatedApnSetting(oldApn,
                                    newInfrastructureBitmask);

                            Log.d(logTag, "UpdateAPN: New APN: " + newApn.getApnName()
                                    + ", Types: " + apnTypesToString(newApn.getApnTypeBitmask())
                                    + ", Infrastructure Bitmask: "
                                    + newApn.getInfrastructureBitmask());

                            if (newApn != null) {
                                ContentValues newValues = newApn.toContentValues();
                                String where = Telephony.Carriers.APN + " = ?";
                                String[] selectionArgs = new String[]{oldApn.getApnName()};

                                int rowsUpdated = resolver.update(Telephony.Carriers.CONTENT_URI,
                                        newValues, where, selectionArgs);
                                Log.d(logTag, "UpdateAPN: Rows updated: " + rowsUpdated);
                            }
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(logTag, "Error modifying APN for satellite mock: " + e);
        }
        return originalApnSettings;
    }

    /**
     *
     * Restore APN settings to original values
     *
     * @param context
     * @param originalApnSettings
     * @param logTag
     */
    public static void restoreOriginalApns(Context context,
            List<ContentValues> originalApnSettings, String logTag) {
        if (originalApnSettings == null || originalApnSettings.isEmpty()) {
            return;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            for (ContentValues values : originalApnSettings) {
                String where = Telephony.Carriers.APN + " = ?";
                String[] selectionArgs = new String[]{values.getAsString(Telephony.Carriers.APN)};
                ContentValues restoreValues = new ContentValues();
                restoreValues.put(Telephony.Carriers.INFRASTRUCTURE_BITMASK,
                        values.getAsInteger(Telephony.Carriers.INFRASTRUCTURE_BITMASK));
                int rowsUpdated = resolver.update(Telephony.Carriers.CONTENT_URI,
                        restoreValues, where, selectionArgs);
                Log.d(logTag, "UpdateAPN: RestoreAPN: Rows updated: " + rowsUpdated);
            }
        } catch (Exception e) {
            Log.e(logTag, "Error restoring APNs: " + e);
        }
    }

    /**
     * Returns the Subscriber ID (IMSI).
     *
     * @param subIdTelephonyManager The TelephonyManager instance.
     * @param r The Resources object to fetch strings.
     * @return The Subscriber ID string or "Unknown".
     */
    public static String getSubscriberId(TelephonyManager subIdTelephonyManager, Resources r) {
        String subscriberId = subIdTelephonyManager.getSubscriberId();
        return subscriberId != null ? subscriberId : r.getString(R.string.radioInfo_unknown);
    }

    /**
     * Returns the Group Identifier Level 1 (GID1).
     *
     * @param subIdTelephonyManager The TelephonyManager instance.
     * @param r The Resources object to fetch strings.
     * @return The GID1 string or "Unknown".
     */
    public static String getGid1(TelephonyManager subIdTelephonyManager, Resources r) {
        String gid1 = subIdTelephonyManager.getGroupIdLevel1();
        return gid1 != null ? gid1 : r.getString(R.string.radioInfo_unknown);
    }

    /**
     * Returns the Carrier ID and Name.
     *
     * @param subIdTelephonyManager The TelephonyManager instance.
     * @param r The Resources object to fetch strings.
     * @return The formatted Carrier ID string or "Unknown".
     */
    public static String getCarrierIdString(
            TelephonyManager subIdTelephonyManager, Resources r) {
        int carrierId = subIdTelephonyManager.getSimCarrierId();
        CharSequence carrierIdName = subIdTelephonyManager.getSimCarrierIdName();

        if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
            return r.getString(R.string.radioInfo_unknown);
        }

        if (TextUtils.isEmpty(carrierIdName)) {
            return String.valueOf(carrierId);
        }

        return carrierId + " (" + carrierIdName + ")";
    }

    public static boolean isUserBuild() {
        return "user".equals(Build.TYPE);
    }

    /**
     * Checks if the RadioInfo access is restricted for a specific phone ID.
     *
     * @param context The context.
     * @param phoneId The phone ID to check configuration for.
     * @return true if the activity should be disabled for this phone, false otherwise.
     */
    public static boolean isRadioInfoRestricted(Context context, int phoneId) {
        if (!isUserBuild()) return false;
        int subId = SubscriptionManager.getSubscriptionId(phoneId);
        // If subId is invalid, we check default config.
        // If default is false (allowed), then return false.
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        return isRadioInfoMenuDisabled(context, subId);
    }

    /**
     * Checks if the RadioInfo access is restricted for ANY active phone.
     * If there are no active phones, it checks the default config.
     * Returns true if the activity should be restricted (i.e., any SIM is restricted).
     */
    public static boolean isRadioInfoAccessRestricted(Context context) {
        if (!isUserBuild()) return false;
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        int phoneCount = tm.getActiveModemCount();

        for (int phoneIndex = 0; phoneIndex < phoneCount; phoneIndex++) {
            int subId = SubscriptionManager.getSubscriptionId(phoneIndex);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (isRadioInfoMenuDisabled(context, subId)) {
                    // ANY SIM restricted -> Restricted access
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if RadioInfo and related activities should be disabled on user builds based on
     * build type and carrier configuration.
     *
     * @param context The context.
     * @param subId The subscription ID to check configuration for.
     * @return true if the activity should be disabled, false otherwise.
     */
    private static boolean isRadioInfoMenuDisabled(Context context, int subId) {
        CarrierConfigManager configManager = getCarrierConfig(context);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForSubId(subId);
            if (b != null) {
                return b.getBoolean(CarrierConfigManager.KEY_HIDE_RADIO_INFO_ON_USER_BUILD_BOOL,
                        false);
            }
        }
        return false;
    }

    public static PersistableBundle getSatelliteConfigsForSubId(Context context, int subId) {
        Log.d(TAG, "getSatelliteConfigsForSubId: " + subId);
        CarrierConfigManager carrierConfigManager = getCarrierConfig(context);
        if (carrierConfigManager == null) {
            Log.w(TAG, "getSatelliteConfigsForSubId: carrierConfigManager is null");
            return CarrierConfigManager.getDefaultConfig();
        }
        PersistableBundle config = null;
        try {
            config = carrierConfigManager.getConfigForSubId(subId,
                    KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                    KEY_SATELLITE_ATTACH_SUPPORTED_BOOL,
                    KEY_SATELLITE_DISPLAY_NAME_STRING,
                    KEY_SATELLITE_ROAMING_TURN_OFF_SESSION_FOR_EMERGENCY_CALL_BOOL,
                    KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT,
                    KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL,
                    KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY,
                    KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL,
                    KEY_EMERGENCY_CALL_TO_SATELLITE_T911_HANDOVER_TIMEOUT_MILLIS_INT,
                    KEY_SATELLITE_ESOS_SUPPORTED_BOOL,
                    KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL,
                    KEY_SATELLITE_NIDD_APN_NAME_STRING,
                    KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT,
                    KEY_CARRIER_SUPPORTED_SATELLITE_NOTIFICATION_HYSTERESIS_SEC_INT,
                    KEY_CARRIER_ROAMING_NTN_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_INT,
                    KEY_SATELLITE_ROAMING_SCREEN_OFF_INACTIVITY_TIMEOUT_SEC_INT,
                    KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                    KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                    KEY_SATELLITE_SOS_MAX_DATAGRAM_SIZE_BYTES_INT,
                    KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY,
                    KEY_REGIONAL_SATELLITE_EARFCN_BUNDLE,
                    KEY_SATELLITE_DATA_SUPPORT_MODE_INT,
                    KEY_SATELLITE_CONNECTED_NOTIFICATION_THROTTLE_MILLIS_INT,
                    KEY_SATELLITE_CONFIGS_PER_PLMN_BUNDLE,
                    KEY_SATELLITE_SUPPORTED_EMERGENCY_PLMN_STRING_ARRAY,
                    KEY_SATELLITE_SUPPORTED_DISASTER_PLMN_STRING_ARRAY
            );
        } catch (Exception e) {
            Log.w(TAG, "getSatelliteConfigsForSubId: " + e);
        }
        if (config == null || config.isEmpty()) {
            Log.w(TAG, "getSatelliteConfigsForSubId: config is null or empty,"
                    + " using default config");
            config = CarrierConfigManager.getDefaultConfig();
        }
        return config;
    }
}
