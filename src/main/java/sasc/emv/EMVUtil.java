/*
 * Copyright 2010 sasc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sasc.emv;

import sasc.iso7816.TagValueType;
import sasc.iso7816.TagAndLength;
import sasc.iso7816.Tag;
import sasc.util.Log;
import sasc.iso7816.SmartCardException;
import sasc.iso7816.BERTLV;
import sasc.iso7816.AID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import sasc.terminal.CardResponse;
import sasc.terminal.TerminalException;
import sasc.terminal.CardConnection;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class EMVUtil {

    /**
     * No response parsing
     * 
     * @param terminal
     * @param command
     * @return
     * @throws TerminalException 
     */
    //TODO remove this and replace with CLS/INS bit indication (response formatted in a TLV structure)
    public static CardResponse sendCmdNoParse(CardConnection terminal, String command) throws TerminalException {
        return sendCmdInternal(terminal, command, false);
    }

    public static CardResponse sendCmd(CardConnection terminal, String command) throws TerminalException {
        return sendCmdInternal(terminal, command, true);
    }

    //TODO move this to generic ISO7816 routine?
    private static CardResponse sendCmdInternal(CardConnection terminal, String command, boolean doParseTLVData) throws TerminalException {
        Log.command(command);
        byte[] cmdBytes = Util.fromHexString(command);
        long startTime = System.nanoTime();
        CardResponse response = terminal.transmit(cmdBytes);

//      //handle procedure bytes here, and not in the lower level TerminalProvider Implementations.
        //That way we can process procedure bytes from any Provider (if they are not handled at that level)

        byte sw1 = (byte) response.getSW1();
        byte sw2 = (byte) response.getSW2();
        byte[] data = response.getData(); //Copy
        Log.debug("Received data+SW1+SW2: " + Util.byteArrayToHexString(data) + " " + Util.byte2Hex(sw1) + " " + Util.byte2Hex((byte) sw2));

        if (sw1 == (byte) 0x6c) { //"Wrong length" (resend last command with correct length)
            Log.procedureByte("Received procedure byte SW1=0x6c. Re-issuing command with correct length: " + Util.byte2Hex(sw2));
            //Re-issue command with correct length
            cmdBytes[4] = sw2;
            response = terminal.transmit(cmdBytes);
            sw1 = (byte) response.getSW1();
            sw2 = (byte) response.getSW2();
            data = response.getData(); //Copy
            Log.procedureByte("Received data+SW1+SW2: " + Util.byteArrayToHexString(data) + " " + Util.byte2Hex(sw1) + " " + Util.byte2Hex(sw2));
        }

        //Note some non-EMV cards (and terminal software) seem to re-issue the last command with length=SW2 when getting SW1=61
        while (sw1 == (byte) 0x61) { //Procedure byte: send GET RESPONSE to receive more data
            //this command is EMV specific, since EMV locks CLS to 0x00 only (Book 1, 9.3.1.3). ISO7816-4 specifies CLS in GET RESPONSE in "section 5.4.1 Class byte" to be 0x0X
            cmdBytes = new byte[]{(byte) 0x00, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) sw2};
            Log.procedureByte("Received procedure byte SW1=0x61. Sending GET RESPONSE command: " + Util.byteArrayToHexString(cmdBytes));
            response = terminal.transmit(cmdBytes);
            byte[] newData = response.getData();
            byte[] tmpData = new byte[data.length + newData.length];
            System.arraycopy(data, 0, tmpData, 0, data.length);
            System.arraycopy(newData, 0, tmpData, data.length, newData.length);
            sw1 = (byte) response.getSW1();
            sw2 = (byte) response.getSW2();
            Log.procedureByte("Received newData+SW1+SW2: " + Util.byteArrayToHexString(newData) + " " + Util.byte2Hex(sw1) + " " + Util.byte2Hex(sw2));
            data = tmpData;
        }


        long endTime = System.nanoTime();
        printResponse(response, doParseTLVData);
        Log.debug("Time: " + Util.getFormattedNanoTime(endTime - startTime));
        return response;
    }

    public static void printResponse(CardResponse response, boolean doParseTLVData) {
        Log.info("response hex    :\n" + Util.prettyPrintHex(Util.byteArrayToHexString(response.getData())));
        String swDescription = "";
        String tmp = EMVUtil.getSWDescription(Util.short2Hex(response.getSW()));
        if (tmp != null && tmp.trim().length() > 0) {
            swDescription = " (" + tmp + ")";
        }
        Log.info("response SW1SW2 : " + Util.byte2Hex(response.getSW1()) + " " + Util.byte2Hex(response.getSW2()) + swDescription);
        Log.info("response ascii  : " + Util.getSafePrintChars(response.getData()));
        if (doParseTLVData) {
            Log.info("response parsed :\n" + EMVUtil.prettyPrintAPDUResponse(response.getData()));
        }
    }

    //parseFCI_PSE ?? TODO split PSE/PPSE
    public static DDF parseFCIDDF(byte[] data, EMVCard card) {

        DDF ddf = new DDF();

        BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(data));

        if (tlv.getTag().equals(EMVTags.FCI_TEMPLATE)) {
            ByteArrayInputStream templateStream = tlv.getValueStream();

            while (templateStream.available() >= 2) {
                tlv = EMVUtil.getNextTLV(templateStream);
                if (tlv.getTag().equals(EMVTags.DEDICATED_FILE_NAME)) {
                    ddf.setName(tlv.getValueBytes());
                } else if (tlv.getTag().equals(EMVTags.FCI_PROPRIETARY_TEMPLATE)) {
                    ByteArrayInputStream bis2 = new ByteArrayInputStream(tlv.getValueBytes());
                    int totalLen = bis2.available();
                    int templateLen = tlv.getLength();
                    while (bis2.available() > (totalLen - templateLen)) {
                        tlv = EMVUtil.getNextTLV(bis2);

                        if (tlv.getTag().equals(EMVTags.SFI)) {
                            ShortFileIdentifier sfi = new ShortFileIdentifier(Util.byteArrayToInt(tlv.getValueBytes()));
                            ddf.setSFI(sfi);
                        } else if (tlv.getTag().equals(EMVTags.LANGUAGE_PREFERENCE)) {
                            LanguagePreference languagePreference = new LanguagePreference(tlv.getValueBytes());
                            ddf.setLanguagePreference(languagePreference);
                        } else if (tlv.getTag().equals(EMVTags.ISSUER_CODE_TABLE_INDEX)) {
                            int index = Util.byteArrayToInt(tlv.getValueBytes());
                            ddf.setIssuerCodeTableIndex(index);
                        } else if (tlv.getTag().equals(EMVTags.FCI_ISSUER_DISCRETIONARY_DATA)) { //PPSE
                            ByteArrayInputStream discrStream = new ByteArrayInputStream(tlv.getValueBytes());
                            int total3Len = discrStream.available();
                            int template3Len = tlv.getLength();
                            while (discrStream.available() > (total3Len - template3Len)) {
                                tlv = EMVUtil.getNextTLV(discrStream);

                                if (tlv.getTag().equals(EMVTags.APPLICATION_TEMPLATE)) {
                                    ByteArrayInputStream appTemplateStream = new ByteArrayInputStream(tlv.getValueBytes());
                                    int appTemplateTotalLen = appTemplateStream.available();
                                    int template4Len = tlv.getLength();
                                    EMVApplication app = new EMVApplication();
                                    while (discrStream.available() > (appTemplateTotalLen - template4Len)) {
                                        tlv = EMVUtil.getNextTLV(appTemplateStream);

                                        if (tlv.getTag().equals(EMVTags.AID_CARD)) {
                                            app.setAID(new AID(tlv.getValueBytes()));
                                        } else if (tlv.getTag().equals(EMVTags.APPLICATION_LABEL)) {
                                            String label = Util.getSafePrintChars(tlv.getValueBytes()); //Use only safe print chars, just in case
                                            app.setLabel(label);
                                        } else if (tlv.getTag().equals(EMVTags.APPLICATION_PRIORITY_INDICATOR)) {
                                            ApplicationPriorityIndicator api = new ApplicationPriorityIndicator(tlv.getValueBytes()[0]);
                                            app.setApplicationPriorityIndicator(api);
                                        } else {
                                            //TODO call ddf instead of card?
                                            card.addUnhandledRecord(tlv);
                                        }
                                    }
                                } else {
                                    //TODO call ddf instead of card?
                                    card.addUnhandledRecord(tlv);
                                }
                            }
                        } else {
                            //TODO call ddf instead of card?
                            card.addUnhandledRecord(tlv);
                        }
                    }
                } else {
                    //TODO call ddf instead of card?
                    card.addUnhandledRecord(tlv);
                }
            }
        } else {
            //TODO call ddf instead of card?
            card.addUnhandledRecord(tlv);
        }

        return ddf;
    }

    public static List<EMVApplication> parsePSERecord(byte[] data, EMVCard card) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);

        List<EMVApplication> apps = new ArrayList<EMVApplication>();
        while (bis.available() >= 2) {
            BERTLV tlv = EMVUtil.getNextTLV(bis);
            if (tlv.getTag().equals(EMVTags.RECORD_TEMPLATE)) {
                ByteArrayInputStream valueBytesBis = new ByteArrayInputStream(tlv.getValueBytes());
                while (valueBytesBis.available() >= 2) {
                    tlv = EMVUtil.getNextTLV(valueBytesBis);
                    if (tlv.getTag().equals(EMVTags.APPLICATION_TEMPLATE)) { //Application Template
                        ByteArrayInputStream bis2 = new ByteArrayInputStream(tlv.getValueBytes());
                        int totalLen = bis2.available();
                        int templateLen = tlv.getLength();
                        EMVApplication app = new EMVApplication();
                        while (bis2.available() > (totalLen - templateLen)) {

                            tlv = EMVUtil.getNextTLV(bis2);

                            if (tlv.getTag().equals(EMVTags.AID_CARD)) {
                                app.setAID(new AID(tlv.getValueBytes()));
                            } else if (tlv.getTag().equals(EMVTags.APPLICATION_LABEL)) {
                                String label = Util.getSafePrintChars(tlv.getValueBytes()); //Use only safe print chars, just in case
                                app.setLabel(label);
                            } else if (tlv.getTag().equals(EMVTags.APP_PREFERRED_NAME)) {
                                String preferredName = Util.getSafePrintChars(tlv.getValueBytes()); //Use only safe print chars, just in case
                                app.setPreferredName(preferredName);
                            } else if (tlv.getTag().equals(EMVTags.APPLICATION_PRIORITY_INDICATOR)) {
                                ApplicationPriorityIndicator api = new ApplicationPriorityIndicator(tlv.getValueBytes()[0]);
                                app.setApplicationPriorityIndicator(api);
                            } else if (tlv.getTag().equals(EMVTags.ISSUER_CODE_TABLE_INDEX)) {
                                int index = Util.byteArrayToInt(tlv.getValueBytes());
                                app.setIssuerCodeTableIndex(index);
                            } else {
                                app.addUnhandledRecord(tlv);
                            }
                        }
                        Log.debug("Adding application: " + app.getAID());
                        apps.add(app);
                        card.addApplication(app);
                    } else {
                        card.addUnhandledRecord(tlv);
                    }
                }

            } else if (tlv.getTag().equals(EMVTags.RESPONSE_MESSAGE_TEMPLATE_2)) {
                card.addUnhandledRecord(tlv);
            }
        }
        return apps;
    }

    public static ApplicationDefinitionFile parseFCIADF(byte[] data, EMVApplication app) {
        ApplicationDefinitionFile adf = new ApplicationDefinitionFile(); //TODO: actually _use_ ADF (add to app?)

        BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(data));

        if (tlv.getTag().equals(EMVTags.FCI_TEMPLATE)) {
            ByteArrayInputStream templateStream = tlv.getValueStream();
            while (templateStream.available() >= 2) {


                tlv = EMVUtil.getNextTLV(templateStream);
                if (tlv.getTag().equals(EMVTags.DEDICATED_FILE_NAME)) {
                    adf.setName(tlv.getValueBytes()); //AID
                    app.setAID(new AID(tlv.getValueBytes()));
                } else if (tlv.getTag().equals(EMVTags.FCI_PROPRIETARY_TEMPLATE)) { //Proprietary Information Template
                    ByteArrayInputStream bis2 = tlv.getValueStream();
                    int totalLen = bis2.available();
                    int templateLen = tlv.getLength();
                    while (bis2.available() > (totalLen - templateLen)) {
                        tlv = EMVUtil.getNextTLV(bis2);

                        if (tlv.getTag().equals(EMVTags.APPLICATION_LABEL)) {
                            app.setLabel(Util.getSafePrintChars(tlv.getValueBytes()));
                        } else if (tlv.getTag().equals(EMVTags.PDOL)) {
                            app.setPDOL(new DOL(DOL.Type.PDOL, tlv.getValueBytes()));
                        } else if (tlv.getTag().equals(EMVTags.LANGUAGE_PREFERENCE)) {
                            LanguagePreference languagePreference = new LanguagePreference(tlv.getValueBytes());
                            app.setLanguagePreference(languagePreference);
                        } else if (tlv.getTag().equals(EMVTags.APP_PREFERRED_NAME)) {
                            //TODO: "Use Issuer Code Table Index"
                            String preferredName = Util.getSafePrintChars(tlv.getValueBytes()); //Use only safe print chars, just in case
                            app.setPreferredName(preferredName);
                        } else if (tlv.getTag().equals(EMVTags.ISSUER_CODE_TABLE_INDEX)) {
                            int index = Util.byteArrayToInt(tlv.getValueBytes());
                            app.setIssuerCodeTableIndex(index);
                        } else if (tlv.getTag().equals(EMVTags.APPLICATION_PRIORITY_INDICATOR)) {
                            ApplicationPriorityIndicator api = new ApplicationPriorityIndicator(tlv.getValueBytes()[0]);
                            app.setApplicationPriorityIndicator(api);
                        } else if (tlv.getTag().equals(EMVTags.FCI_ISSUER_DISCRETIONARY_DATA)) { // File Control Information (FCI) Issuer Discretionary Data
                            ByteArrayInputStream bis3 = tlv.getValueStream();
                            int totalLenFCIDiscretionary = bis3.available();
                            int tlvLen = tlv.getLength();
                            while (bis3.available() > (totalLenFCIDiscretionary - tlvLen)) {
                                tlv = EMVUtil.getNextTLV(bis3);
                                if (tlv.getTag().equals(EMVTags.LOG_ENTRY)) {
                                    app.setLogEntry(new LogEntry(tlv.getValueBytes()[0], tlv.getValueBytes()[1]));
                                } else {
                                    app.addUnhandledRecord(tlv);
                                }
                            }
                        } else {
                            app.addUnhandledRecord(tlv);
                        }

                    }
                }
            }

        } else {
            app.addUnhandledRecord(tlv);
            throw new SmartCardException("Error parsing ADF. Data: " + Util.byteArrayToHexString(data));
        }
        return adf;
    }

    public static void parseProcessingOpts(byte[] data, EMVApplication app) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);

        if (bis.available() < 2) {
            throw new SmartCardException("Error parsing Processing Options. Invalid TLV Length. Data: " + Util.byteArrayToHexString(data));
        }
        BERTLV tlv = EMVUtil.getNextTLV(bis);

        ByteArrayInputStream valueBytesBis = tlv.getValueStream();

        if (valueBytesBis.available() < 2) {
            throw new SmartCardException("Error parsing Processing Options: Invalid ValueBytes length: " + valueBytesBis.available());
        }

        if (tlv.getTag().equals(EMVTags.RESPONSE_MESSAGE_TEMPLATE_1)) {
            //AIP & AFL concatenated without delimiters (that is, excluding tag and length)
            ApplicationInterchangeProfile aip = new ApplicationInterchangeProfile((byte) valueBytesBis.read(), (byte) valueBytesBis.read());
            app.setApplicationInterchangeProfile(aip);

            if (valueBytesBis.available() % 4 != 0) {
                throw new SmartCardException("Error parsing Processing Options: Invalid AFL length: " + valueBytesBis.available());
            }

            byte[] aflBytes = new byte[valueBytesBis.available()];
            valueBytesBis.read(aflBytes, 0, aflBytes.length);

            ApplicationFileLocator afl = new ApplicationFileLocator(aflBytes);
            app.setApplicationFileLocator(afl);
        } else if (tlv.getTag().equals(EMVTags.RESPONSE_MESSAGE_TEMPLATE_2)) {
            //AIP & AFL WITH delimiters (that is, including, including tag and length) and possibly other BER TLV tags (that might be proprietary)
            while (valueBytesBis.available() >= 2) {
                tlv = EMVUtil.getNextTLV(valueBytesBis);
                if (tlv.getTag().equals(EMVTags.APPLICATION_INTERCHANGE_PROFILE)) {
                    byte[] aipBytes = tlv.getValueBytes();
                    ApplicationInterchangeProfile aip = new ApplicationInterchangeProfile(aipBytes[0], aipBytes[1]);
                    app.setApplicationInterchangeProfile(aip);
                } else if (tlv.getTag().equals(EMVTags.APPLICATION_FILE_LOCATOR)) {
                    byte[] aflBytes = tlv.getValueBytes();
                    ApplicationFileLocator afl = new ApplicationFileLocator(aflBytes);
                    app.setApplicationFileLocator(afl);
                } else {
                    app.addUnhandledRecord(tlv);
                }
            }
        } else {
            app.addUnhandledRecord(tlv);
        }




    }

    //TODO convert this into "parseAppData", and make it generic for reading all application data (records + GPO + additional data)?
    //
    public static void parseAppRecord(byte[] data, EMVApplication app) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);

        if (bis.available() < 2) {
            throw new SmartCardException("Error parsing Application Record. Data: " + Util.byteArrayToHexString(data));
        }
        BERTLV tlv = EMVUtil.getNextTLV(bis);

        if (!tlv.getTag().equals(EMVTags.RECORD_TEMPLATE)) {
            throw new SmartCardException("Error parsing Application Record: No Response Template found. Data=" + Util.byteArrayToHexString(tlv.getValueBytes()));
        }

        bis = new ByteArrayInputStream(tlv.getValueBytes());

        while (bis.available() >= 2) {
            tlv = EMVUtil.getNextTLV(bis);
            if (tlv.getTag().equals(EMVTags.CARDHOLDER_NAME)) {
                app.setCardholderName(Util.getSafePrintChars(tlv.getValueBytes()));
            } else if (tlv.getTag().equals(EMVTags.TRACK1_DISCRETIONARY_DATA)) {
                app.setTrack1DiscretionaryData(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.TRACK2_DISCRETIONARY_DATA)) {
                app.setTrack2DiscretionaryData(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.TRACK_2_EQV_DATA)) {
                Track2EquivalentData t2Data = new Track2EquivalentData(tlv.getValueBytes());
                app.setTrack2EquivalentData(t2Data);
            } else if (tlv.getTag().equals(EMVTags.APP_EXPIRATION_DATE)) {
                app.setExpirationDate(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.APP_EFFECTIVE_DATE)) {
                app.setEffectiveDate(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.PAN)) {
                PAN pan = new PAN(tlv.getValueBytes());
                app.setPAN(pan);
            } else if (tlv.getTag().equals(EMVTags.PAN_SEQUENCE_NUMBER)) {
                app.setPANSequenceNumber(tlv.getValueBytes()[0]);
            } else if (tlv.getTag().equals(EMVTags.APP_USAGE_CONTROL)) {
                ApplicationUsageControl auc = new ApplicationUsageControl(tlv.getValueBytes()[0], tlv.getValueBytes()[1]);
                app.setApplicationUsageControl(auc);
            } else if (tlv.getTag().equals(EMVTags.CVM_LIST)) {
                CVMList cvmList = new CVMList(tlv.getValueBytes());
                app.setCVMList(cvmList);
            } else if (tlv.getTag().equals(EMVTags.LANGUAGE_PREFERENCE)) {
                LanguagePreference languagePreference = new LanguagePreference(tlv.getValueBytes());
                app.setLanguagePreference(languagePreference);
            } else if (tlv.getTag().equals(EMVTags.ISSUER_ACTION_CODE_DEFAULT)) {
                app.setIssuerActionCodeDefault(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ISSUER_ACTION_CODE_DENIAL)) {
                app.setIssuerActionCodeDenial(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ISSUER_ACTION_CODE_ONLINE)) {
                app.setIssuerActionCodeOnline(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ISSUER_COUNTRY_CODE)) {
                int issuerCountryCode = Util.numericHexToInt(Util.byteArrayToHexString(tlv.getValueBytes()));
                app.setIssuerCountryCode(issuerCountryCode);
            } else if (tlv.getTag().equals(EMVTags.APPLICATION_CURRENCY_CODE)) {
                int currencyCode = Util.numericHexToInt(Util.byteArrayToHexString(tlv.getValueBytes()));
                app.setApplicationCurrencyCode(currencyCode);
            } else if (tlv.getTag().equals(EMVTags.APP_CURRENCY_EXPONENT)) {
                int applicationCurrencyExponent = Util.numericHexToInt(Util.byteArrayToHexString(tlv.getValueBytes()));
                app.setApplicationCurrencyExponent(applicationCurrencyExponent);
            } else if (tlv.getTag().equals(EMVTags.APP_VERSION_NUMBER_CARD)) {
                app.setApplicationVersionNumber(Util.byteArrayToInt(tlv.getValueBytes()));
            } else if (tlv.getTag().equals(EMVTags.CDOL1)) {
                DOL cdol1 = new DOL(DOL.Type.CDOL1, tlv.getValueBytes());
                app.setCDOL1(cdol1);
            } else if (tlv.getTag().equals(EMVTags.CDOL2)) {
                DOL cdol2 = new DOL(DOL.Type.CDOL2, tlv.getValueBytes());
                app.setCDOL2(cdol2);
            } else if (tlv.getTag().equals(EMVTags.SERVICE_CODE)) {
                int serviceCode = Util.numericHexToInt(Util.byteArrayToHexString(tlv.getValueBytes()));
                app.setServiceCode(serviceCode);
            } else if (tlv.getTag().equals(EMVTags.SDA_TAG_LIST)) {
                StaticDataAuthenticationTagList staticDataAuthTagList = new StaticDataAuthenticationTagList(tlv.getValueBytes());
                app.setStaticDataAuthenticationTagList(staticDataAuthTagList);
            } else if (tlv.getTag().equals(EMVTags.CA_PUBLIC_KEY_INDEX_CARD)) {
                IssuerPublicKeyCertificate issuerCert = app.getIssuerPublicKeyCertificate();
                if (issuerCert == null) {
                    CA ca = CA.getCA(app.getAID());

                    if (ca == null) {
                        //ca == null is permitted (we might not have the CA public keys for every exotic CA)
                        Log.info("No CA configured for AID: " + app.getAID().toString());
//                        throw new SmartCardException("No CA configured for AID: "+app.getAID().toString());
                    }
                    issuerCert = new IssuerPublicKeyCertificate(ca);
                    app.setIssuerPublicKeyCertificate(issuerCert);
                }
                issuerCert.setCAPublicKeyIndex(Util.byteArrayToInt(tlv.getValueBytes()));
            } else if (tlv.getTag().equals(EMVTags.ISSUER_PUBLIC_KEY_CERT)) {
                IssuerPublicKeyCertificate issuerCert = app.getIssuerPublicKeyCertificate();
                if (issuerCert == null) {
                    issuerCert = new IssuerPublicKeyCertificate(CA.getCA(app.getAID()));
                    app.setIssuerPublicKeyCertificate(issuerCert);
                }
                issuerCert.setSignedBytes(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ISSUER_PUBLIC_KEY_EXP)) {
                IssuerPublicKeyCertificate issuerCert = app.getIssuerPublicKeyCertificate();
                if (issuerCert == null) {
                    issuerCert = new IssuerPublicKeyCertificate(CA.getCA(app.getAID()));
                    app.setIssuerPublicKeyCertificate(issuerCert);
                }
                issuerCert.getIssuerPublicKey().setExponent(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ISSUER_PUBLIC_KEY_REMAINDER)) {
                IssuerPublicKeyCertificate issuerCert = app.getIssuerPublicKeyCertificate();
                if (issuerCert == null) {
                    issuerCert = new IssuerPublicKeyCertificate(CA.getCA(app.getAID()));
                    app.setIssuerPublicKeyCertificate(issuerCert);
                }
                issuerCert.getIssuerPublicKey().setRemainder(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.SIGNED_STATIC_APP_DATA)) {
                SignedStaticApplicationData ssad = app.getSignedStaticApplicationData();
                if (ssad == null) {
                    ssad = new SignedStaticApplicationData(app);
                    app.setSignedStaticApplicationData(ssad);
                }
                ssad.setSignedBytes(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ICC_PUBLIC_KEY_CERT)) {
                ICCPublicKeyCertificate iccCert = app.getICCPublicKeyCertificate();
                if (iccCert == null) {
                    iccCert = new ICCPublicKeyCertificate(app, app.getIssuerPublicKeyCertificate());
                    app.setICCPublicKeyCertificate(iccCert);
                }
                iccCert.setSignedBytes(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ICC_PUBLIC_KEY_EXP)) {
                ICCPublicKeyCertificate iccCert = app.getICCPublicKeyCertificate();
                if (iccCert == null) {
                    iccCert = new ICCPublicKeyCertificate(app, app.getIssuerPublicKeyCertificate());
                    app.setICCPublicKeyCertificate(iccCert);
                }
                iccCert.getICCPublicKey().setExponent(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.ICC_PUBLIC_KEY_REMAINDER)) {
                ICCPublicKeyCertificate iccCert = app.getICCPublicKeyCertificate();
                if (iccCert == null) {
                    iccCert = new ICCPublicKeyCertificate(app, app.getIssuerPublicKeyCertificate());
                    app.setICCPublicKeyCertificate(iccCert);
                }
                iccCert.getICCPublicKey().setRemainder(tlv.getValueBytes());
            } else if (tlv.getTag().equals(EMVTags.DDOL)) {
                DOL ddol = new DOL(DOL.Type.DDOL, tlv.getValueBytes());
                app.setDDOL(ddol);
            } else {
                app.addUnhandledRecord(tlv);
            }

        }
    }

    public static void parseInternalAuthResponse(byte[] data, byte[] authenticationRelatedData, EMVApplication app) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);

        if (bis.available() < 2) {
            throw new SmartCardException("Error parsing Internal Auth Response. Invalid TLV Length. Data: " + Util.byteArrayToHexString(data));
        }
        BERTLV tlv = EMVUtil.getNextTLV(bis);

        ByteArrayInputStream valueBytesBis = tlv.getValueStream();

        if (valueBytesBis.available() < 2) {
            throw new SmartCardException("Error parsing Internal Auth Response: Invalid ValueBytes length: " + valueBytesBis.available());
        }

        if (tlv.getTag().equals(EMVTags.RESPONSE_MESSAGE_TEMPLATE_1)) {
            app.getICCPublicKeyCertificate().validate();
            if (!app.getICCPublicKeyCertificate().isValid()) {
                //TODO
            }
            try {
                SignedDynamicApplicationData sdad = SignedDynamicApplicationData.parseSignedData(tlv.getValueBytes(), app.getICCPublicKeyCertificate().getICCPublicKey(), authenticationRelatedData);
                app.setSignedDynamicApplicationData(sdad);
                app.getTransactionStatusInformation().setOfflineDataAuthenticationWasPerformed(true); //TODO
            } catch (SignedDataException ex) {
                Log.debug(ex.getMessage());
                EMVTerminalProfile.getTerminalVerificationResults().setDDAFailed(true);
            }

        } else if (tlv.getTag().equals(EMVTags.RESPONSE_MESSAGE_TEMPLATE_2)) {
            //AIP & AFL WITH delimiters (that is, including, including tag and length) and possibly other BER TLV tags (that might be proprietary)
            while (valueBytesBis.available() >= 2) {
                tlv = EMVUtil.getNextTLV(valueBytesBis);
                //TODO
            }
        } else {
            app.addUnhandledRecord(tlv);
        }

    }

    public static String prettyPrintAPDUResponse(byte[] data) {
        return prettyPrintAPDUResponse(data, 0);
    }

    public static String prettyPrintAPDUResponse(byte[] data, int indentLength) {
        StringBuilder buf = new StringBuilder();

        ByteArrayInputStream stream = new ByteArrayInputStream(data);

        while (stream.available() > 0) {
            buf.append("\n");

            buf.append(Util.getSpaces(indentLength));

            BERTLV tlv = EMVUtil.getNextTLV(stream);

            Log.debug(tlv.toString());

            byte[] tagBytes = tlv.getTagBytes();
            byte[] lengthBytes = tlv.getRawEncodedLengthBytes();
            byte[] valueBytes = tlv.getValueBytes();

            Tag tag = tlv.getTag();

            buf.append(Util.prettyPrintHex(tagBytes));
            buf.append(" ");
            buf.append(Util.prettyPrintHex(lengthBytes));
            buf.append(" -- ");
            buf.append(tag.getName());

            int extraIndent = (lengthBytes.length * 3) + (tagBytes.length * 3);

            if (tag.isConstructed()) {
                //indentLength += extraIndent; //TODO check this
                //Recursion
                buf.append(prettyPrintAPDUResponse(valueBytes, indentLength + extraIndent));
            } else {
                buf.append("\n");
                if (tag.getTagValueType() == TagValueType.DOL) {
                    buf.append(getFormattedTagAndLength(valueBytes, indentLength + extraIndent));
                } else {
                    buf.append(Util.getSpaces(indentLength + extraIndent));
                    buf.append(Util.prettyPrintHex(Util.byteArrayToHexString(valueBytes), indentLength + extraIndent));
                    buf.append(" (");
                    buf.append(getTagValueAsString(tag, valueBytes));
                    buf.append(")");
                }
            }
        }
        return buf.toString();
    }

    //This is just a list of Tag And Lengths (eg DOLs)
    private static String getFormattedTagAndLength(byte[] data, int indentLength) {
        StringBuilder buf = new StringBuilder();
        String indent = Util.getSpaces(indentLength);
        ByteArrayInputStream stream = new ByteArrayInputStream(data);

        boolean firstLine = true;
        while (stream.available() > 0) {
            if (firstLine) {
                firstLine = false;
            } else {
                buf.append("\n");
            }
            buf.append(indent);

            Tag tag = EMVTags.getNotNull(EMVUtil.readTagIdBytes(stream));
            int length = EMVUtil.readTagLength(stream);

            buf.append(Util.prettyPrintHex(tag.getTagBytes()));
            buf.append(" ");
            buf.append(Util.byteArrayToHexString(Util.intToByteArray(length)));
            buf.append(" -- ");
            buf.append(tag.getName());
        }
        return buf.toString();
    }

    public static byte[] readTagIdBytes(ByteArrayInputStream stream) {
        ByteArrayOutputStream tagBAOS = new ByteArrayOutputStream();
        byte tagFirstOctet = (byte) stream.read();
        tagBAOS.write(tagFirstOctet);

        //Find TAG bytes
        byte MASK = (byte) 0x1F;
        if ((tagFirstOctet & MASK) == MASK) { // EMV book 3, Page 178 or Annex B1 (EMV4.3)
            //Tag field is longer than 1 byte
            do {
                byte tlvIdNextOctet = (byte) stream.read();

                tagBAOS.write(tlvIdNextOctet);

                if (!Util.isBitSet(tlvIdNextOctet, 8)) {
                    break;
                }
            } while (true);
        }
        return tagBAOS.toByteArray();
    }

    public static int readTagLength(ByteArrayInputStream stream) {
        //Find LENGTH bytes
        int length;
        int tmpLength = stream.read();

        if (tmpLength <= 127) { // 0111 1111
            // short length form
            length = tmpLength;
        } else if (tmpLength == 128) { // 1000 0000
            // length identifies indefinite form, will be set later
            length = tmpLength;
        } else {
            // long length form
            int numberOfLengthOctets = tmpLength & 127; // turn off 8th bit
            tmpLength = 0;
            for (int i = 0; i < numberOfLengthOctets; i++) {
                int nextLengthOctet = stream.read();
                tmpLength <<= 8;
                tmpLength |= nextLengthOctet;
            }
            length = tmpLength;
        }
        return length;
    }

    //http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_annex-d.aspx#AnnexD_1
    public static BERTLV getNextTLV(ByteArrayInputStream stream) {
        if (stream.available() < 2) {
            throw new SmartCardException("Error parsing data. Available bytes < 2 . Length=" + stream.available());
        }


        //ISO/IEC 7816 uses neither '00' nor 'FF' as tag value.
        //Before, between, or after TLV-coded data objects,
        //'00' or 'FF' bytes without any meaning may occur
        //(for example, due to erased or modified TLV-coded data objects).

        stream.mark(0);
        int peekInt = stream.read();
        byte peekByte = (byte) peekInt;
        //peekInt == 0xffffffff indicates EOS
        while (peekInt != -1 && (peekByte == (byte) 0xFF || peekByte == (byte) 0x00)) {
            stream.mark(0); //Current position
            peekInt = stream.read();
            peekByte = (byte) peekInt;
        }
        stream.reset(); //Reset back to the last known position without 0x00 or 0xFF

        if (stream.available() < 2) {
            throw new SmartCardException("Error parsing data. Available bytes < 2 . Length=" + stream.available());
        }

        byte[] tagIdBytes = EMVUtil.readTagIdBytes(stream);

        //We need to get the raw length bytes.
        //Use quick and dirty workaround
        stream.mark(0);
        int posBefore = stream.available();
        //Now parse the lengthbyte(s)
        //This method will read all length bytes. We can then find out how many bytes was read.
        int length = EMVUtil.readTagLength(stream);
        //Now find the raw length bytes
        int posAfter = stream.available();
        stream.reset();
        byte[] lengthBytes = new byte[posBefore - posAfter];
        stream.read(lengthBytes, 0, lengthBytes.length);

        int rawLength = Util.byteArrayToInt(lengthBytes);

        byte[] valueBytes = null;

        Tag tag = EMVTags.getNotNull(tagIdBytes);

        // Find VALUE bytes
        if (rawLength == 128) { // 1000 0000
            // indefinite form
            stream.mark(0);
            int prevOctet = 1;
            int curOctet = 0;
            int len = 0;
            while (true) {
                len++;
                curOctet = stream.read();
                if (curOctet < 0) {
                    throw new SmartCardException("Error parsing data. TLV "
                            + "length byte indicated indefinite length, but EOS "
                            + "was reached before 0x0000 was found" + stream.available());
                }
                if (prevOctet == 0 && curOctet == 0) {
                    break;
                }
                prevOctet = curOctet;
            }
            len -= 2;
            valueBytes = new byte[len];
            stream.reset();
            stream.read(valueBytes, 0, len);
            length = len;
        } else {
            // definite form
            valueBytes = new byte[length];
            stream.read(valueBytes, 0, length);
        }

        //Remove any trailing 0x00 and 0xFF
        stream.mark(0);
        peekInt = stream.read();
        peekByte = (byte) peekInt;
        while (peekInt != -1 && (peekByte == (byte) 0xFF || peekByte == (byte) 0x00)) {
            stream.mark(0);
            peekInt = stream.read();
            peekByte = (byte) peekInt;
        }
        stream.reset(); //Reset back to the last known position without 0x00 or 0xFF


        BERTLV tlv = new BERTLV(tag, length, lengthBytes, valueBytes);
        return tlv;
    }

    private static String getTagValueAsString(Tag tag, byte[] value) {
        StringBuilder buf = new StringBuilder();

        switch (tag.getTagValueType()) {
            case TEXT:
                buf.append("=");
                buf.append(new String(value));
                break;
            case NUMERIC:
                buf.append("NUMERIC");
                break;
            case BINARY:
                buf.append("BINARY");
                break;

            case MIXED:
                buf.append("=");
                buf.append(Util.getSafePrintChars(value));
                break;

            case DOL:
                buf.append("");
                break;
        }

        return buf.toString();
    }

    public static List<TagAndLength> parseTagAndLength(byte[] data) {
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        List<TagAndLength> tagAndLengthList = new ArrayList<TagAndLength>();

        while (stream.available() > 0) {
            if (stream.available() < 2) {
                throw new SmartCardException("Data length < 2 : " + stream.available());
            }
            byte[] tagIdBytes = EMVUtil.readTagIdBytes(stream);
            int tagValueLength = EMVUtil.readTagLength(stream);

            Tag tag = EMVTags.getNotNull(tagIdBytes);

            tagAndLengthList.add(new TagAndLength(tag, tagValueLength));
        }
        return tagAndLengthList;
    }

    public static String getSWDescription(String swStr) {
        for (SW sw : SW.values()) {
            if (sw.getSWCodeAsString().equalsIgnoreCase(swStr)) {
                return sw.getDescription();
            }
        }

        return "";
    }

    public static void main(String[] args) {

        EMVApplication app = new EMVApplication();

        String gpoResponse = "77 0e 82 02 38 00 94 08 08 01 03 01 10 01 01 00";

        parseProcessingOpts(Util.fromHexString(gpoResponse), app);

        System.out.println(app.getApplicationFileLocator().toString());
        System.out.println(app.getApplicationInterchangeProfile().toString());

        System.out.println(EMVUtil.prettyPrintAPDUResponse(Util.fromHexString("6F388407 A0000000 031010A5 2D500B56 69736144 616E6B6F 72748701 015F2D08 6461656E 6E6F7376 9F110101 9F120B56 69736144 616E6B6F 7274")));
    }
}
