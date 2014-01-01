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
package sasc.common;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import sasc.emv.EMVAPDUCommands;
import sasc.emv.EMVApplication;
import sasc.emv.EMVUtil;
import sasc.emv.SW;
import sasc.iso7816.AID;
import sasc.iso7816.BERTLV;
import sasc.iso7816.Iso7816Commands;
import sasc.iso7816.MasterFile;
import sasc.iso7816.RID;
import sasc.iso7816.SmartCardException;
import sasc.lookup.RID_DB;
import sasc.pcsc.PCSC;
import sasc.terminal.CardConnection;
import sasc.terminal.CardResponse;
import sasc.terminal.KnownAIDList;
import sasc.terminal.TerminalException;
import sasc.util.Log;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class CardScanner {
    
    private SmartCard smartCard;
    private CardConnection terminal;
    private SessionProcessingEnv sessionEnv;
    
    public CardScanner(SmartCard smartCard, CardConnection terminal, SessionProcessingEnv sessionEnv) {
        this.smartCard = smartCard;
        this.terminal = terminal;
        this.sessionEnv = sessionEnv;
    }
    
    public SmartCard getCard(){
        return smartCard;
    }
    
    public void start() throws TerminalException {
        
        byte[] atr = terminal.getATR();
        
        int SW1;
        int SW2;
        String command;
        
        if(sessionEnv.getDiscoverTerminalFeatures()){
            try{
                Log.commandHeader("Transmit Control Command to discover the terminal features");
                byte[] ccResponse = terminal.transmitControlCommand(PCSC.CM_IOCTL_GET_FEATURE_REQUEST, new byte[0]);
                if(ccResponse != null){
                    Log.info("controlCommandResponse: "+Util.prettyPrintHexNoWrap(ccResponse));
                }
            }catch(Exception e){
                Log.debug(e.toString());
            }
        }
        
        
        //Try to GET DATA from the default selected application
        

        //We ALWAYS try to see if there is any GP App(s) present (unless a card (identified by ATR) is registered to be handled exclusively)
        {
            //GP 2.1.1 allows ISD selection using a zero length aid
            //SELECT
            //00 A4 04 00 00
            //GP cards return:
            // 6F File Control Information (FCI) Template 
            //    84 Dedicated File (DF) Name 
            //       A0000001510000 
            //    A5 File Control Information (FCI) Proprietary Template 
            //       9F65 Maximum length of data field 
            //            FF 
            Log.commandHeader("SELECT ISD using zero length AID");
            command = "00 A4 04 00 00";

            CardResponse response = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) response.getSW1();
            SW2 = (byte) response.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //TODO add to GP AIDs
            }
        }
        
        //Master file is not present on all cards
        if (sessionEnv.getReadMasterFile()) {
            //TODO implement MF data parsing (according to 7816-4:2005)
            Log.commandHeader("SELECT FILE Master File (if available)");

            command = Iso7816Commands.selectMasterFile();

            CardResponse selectMFResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectMFResponse.getSW1();
            SW2 = (byte) selectMFResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //Example response TODO
                //6f 09 
                //      84 07 //DF Name
                //            f0 00 00 00 01 3f 00
                //
                //Another example
                //6f 20 
                //      81 02 
                //            00 00 
                //      82 01 
                //            38 //0 - 1 1 1 0 0 0 = DF
                //      83 02 
                //            3f 00 
                //      84 06 
                //            00 00 00 00 00 00 
                //      85 01 
                //            00 
                //      8c 08 
                //            1f a1 a1 a1 a1 a1 88 a1

                //3rd example (ATR: 3b 88 80 01 43 44 31 69 a9 00 00 00 ff)
                //6f 20 
                //      81 02 
                //            00 1a //Number of data bytes in the file
                //      82 01 
                //            38 //38=DF
                //      83 02 
                //            3f 00 
                //      84 06 
                //            00 00 00 00 00 00 
                //      85 01 
                //            00 
                //      8c 08 
                //            1f a1 a1 a1 a1 a1 88 a1

                MasterFile mf = new MasterFile(selectMFResponse.getData());
                getCard().setMasterFile(mf);
            }
//            else {

            Log.commandHeader("SELECT FILE Master File by identifier (if available)");

            command = Iso7816Commands.selectMasterFileByIdentifier();

            CardResponse selectMFByIdResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectMFByIdResponse.getSW1();
            SW2 = (byte) selectMFByIdResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //Example response (ATR: 3b 95 95 40 ff d0 00 54 01 32)
                //6f 17 
                //      82 01 
                //            38 
                //      84 06 
                //            a0 00 00 00 18 00 
                //      8a 01 
                //            05
                MasterFile mf = new MasterFile(selectMFByIdResponse.getData());
                getCard().setMasterFile(mf);
            }
//            }

            //OK, master file is available. Try to read some known files
            if (getCard().getMasterFile() != null) {

                //EF.DIR
                //DIR file (path='3F002F00'). contains a set of BER-TLV data objects
                Log.commandHeader("SELECT FILE EF.DIR (if available)");

                command = "00 A4 08 0C 02 2F 00";
//                command = "00 A4 00 00 02 2F 00";

                CardResponse selectDIRFileResponse = EMVUtil.sendCmd(terminal, command);

                SW1 = (byte) selectDIRFileResponse.getSW1();
                SW2 = (byte) selectDIRFileResponse.getSW2();

                if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                    //Example response (ATR: 3b 95 95 40 ff d0 00 54 01 32)
                    //6f 12 
                    //      82 01 
                    //            01 
                    //      83 02 
                    //            2f 00 
                    //      80 02 
                    //            00 3e 
                    //      8a 01 
                    //            05


                    int sfi = 0; //TODO what sfi to read? from historical bytes??

                    byte recordNum = 1;
                    do {

                        Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                        command = EMVAPDUCommands.readRecord((int) recordNum, sfi);

                        CardResponse readRecordResponse = EMVUtil.sendCmd(terminal, command);

                        SW1 = (byte) readRecordResponse.getSW1();
                        SW2 = (byte) readRecordResponse.getSW2();

                        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(readRecordResponse.getData()));
                            getCard().getMasterFile().addUnhandledRecord(tlv);
                        }

                        recordNum++;

                    } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83

                    //Issue READ RECORDs
                    //-> 00 b2 01 04 02
                    //
                    //<- 61 13 (re-read with length 13)
                    //
                    //   90 00
                    //
                    //   READ RECORD
                    //-> 00 b2 01 04 15
                    //
                    //#02
                    //-> 00 b2 02 04 02
                    //...



                } else {
                    //EF.DIR
                    //DIR file (path='3F002F00'). contains a set of BER-TLV data objects
                    Log.commandHeader("SELECT FILE DIR File (if available)");

                    command = "00 A4 00 00 04 3F 00 2F 00";

                    CardResponse selectDIRFileAbsPathResponse = EMVUtil.sendCmd(terminal, command);

                    SW1 = (byte) selectDIRFileAbsPathResponse.getSW1();
                    SW2 = (byte) selectDIRFileAbsPathResponse.getSW2();

                    if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                        int sfi = 0; //TODO what sfi to read? from historical bytes??

                        byte recordNum = 1;
                        do {

                            Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                            command = EMVAPDUCommands.readRecord((int) recordNum, sfi);

                            CardResponse readRecordResponse = EMVUtil.sendCmd(terminal, command);

                            SW1 = (byte) readRecordResponse.getSW1();
                            SW2 = (byte) readRecordResponse.getSW2();

                            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                                BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(readRecordResponse.getData()));
                                getCard().getMasterFile().addUnhandledRecord(tlv);
                            }

                            recordNum++;

                        } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83
                    }
                }

                //ATR file (path='3F002F01'). contains a set of BER-TLV data objects
                //When the card provides indications in several places, 
                //the indication valid for a given EF is the closest one to that 
                //EF within the path from the MF to that EF.

                Log.commandHeader("SELECT FILE ATR File (if available)");

                command = "00 A4 08 0C 02 2F 01";
//                command = "00 A4 00 00 02 2F 01";
                
                CardResponse selectATRFileResponse = EMVUtil.sendCmd(terminal, command);

                SW1 = (byte) selectATRFileResponse.getSW1();
                SW2 = (byte) selectATRFileResponse.getSW2();

                if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                    //Do the select ATR File command ever return any data?


                    int sfi = 0; //TODO what sfi to read? from historical bytes?

                    byte recordNum = 1;
                    do {

                        Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                        command = EMVAPDUCommands.readRecord((int) recordNum, sfi);

                        CardResponse readRecordResponse = EMVUtil.sendCmd(terminal, command);

                        SW1 = (byte) readRecordResponse.getSW1();
                        SW2 = (byte) readRecordResponse.getSW2();

                        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(readRecordResponse.getData()));
                            getCard().getMasterFile().addUnhandledRecord(tlv);
                        }

                        recordNum++;

                    } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83

                } else {
                    Log.commandHeader("SELECT FILE ATR File (if available)");

                    command = "00 A4 00 00 04 3F 00 2F 01";

                    CardResponse selectATRFileAbsPathResponse = EMVUtil.sendCmd(terminal, command);

                    SW1 = (byte) selectATRFileAbsPathResponse.getSW1();
                    SW2 = (byte) selectATRFileAbsPathResponse.getSW2();

                    if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                        //Do the select ATR File command ever return any data?


                        int sfi = 0; //TODO what sfi to read? from historical bytes?

                        byte recordNum = 1;
                        do {

                            Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                            command = EMVAPDUCommands.readRecord((int) recordNum, sfi);

                            CardResponse readRecordResponse = EMVUtil.sendCmd(terminal, command);

                            SW1 = (byte) readRecordResponse.getSW1();
                            SW2 = (byte) readRecordResponse.getSW2();

                            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                                BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(readRecordResponse.getData()));
                                getCard().getMasterFile().addUnhandledRecord(tlv);
                            }

                            recordNum++;

                        } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83

                    }

                }


                //SIM/UICC
                //            DF Telecom (7F10)
                //            DF GSM (7F20)
                //            DF ICCID (ICC Serial Number) (2fe2)
                //            00A40000027F10
                //
                //            Select Cyclic File (6E00)
                //            00A40000026E00
                
                // When the physical interface does not allow a card to answer to reset, e.g., a universal serial bus or an
                // access by radio frequency, a GET DATA command (see 7.4.2) may retrieve historical bytes (tag '5F52').
            }

        }
        
        //Select all known AIDs
        
        if(sessionEnv.getProbeAllKnownAIDs()){
            probeAllKnownAIDs();
        }
                        
        //Terminal (PC/SC) commands (cls 0xFF)
        //6a 81 = Function not supported
        //90 00 = Success 
        
        if(smartCard.getType() == SmartCard.Type.CONTACTLESS) {
            Log.commandHeader("GET DATA: UID (Command handled by terminal when card is contactless)");

            command = "ff ca 00 00 00"; //PC/SC 2.01 part 3 GetData: UID
            CardResponse getUIDResponse = EMVUtil.sendCmdNoParse(terminal, command);
            SW1 = (byte) getUIDResponse.getSW1();
            SW2 = (byte) getUIDResponse.getSW2();

            Log.commandHeader("GET DATA: Historical bytes (Command handled by terminal when card is contactless)");

            command = "ff ca 01 00 00"; //PC/SC 2.01 part 3 GetData: historical bytes from the ATS of a ISO 14443 A card without CRC
            CardResponse getHistBytes = EMVUtil.sendCmdNoParse(terminal, command);
            SW1 = (byte) getHistBytes.getSW1();
            SW2 = (byte) getHistBytes.getSW2();
        }
        
                //Still nothing found??
        //Select by 5 byte RID
        if(sessionEnv.getSelectAllRIDs()){ //TODO: if (nothing found && selectAllRIDs())
            Map<String, RID> ridMap = RID_DB.getAll();
            for(String ridString : ridMap.keySet()){
                RID rid = ridMap.get(ridString);
                
                Log.commandHeader("Send SELECT RID " + rid.getApplicant() + " ("+rid.getCountry()+")");

                command = Iso7816Commands.selectByDFName(rid.getRIDBytes(), true, (byte)0);

                CardResponse response = EMVUtil.sendCmdNoParse(terminal, command);

                SW1 = (byte) response.getSW1();
                SW2 = (byte) response.getSW2();

                if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                    smartCard.addAID(new AID(rid.getRIDBytes()));
                }
            }
        }
        
        //If absolutely nothing is found, then try to select:
        //a0 00 00 00
        //a0 00 00
        //a0 00
        //a0
        //[empty]
    }
    
    
    public void probeAllKnownAIDs() throws TerminalException {

        smartCard.setAllKnownAidsProbed();
        
        String command;
        
        Collection<KnownAIDList.KnownAID> terminalCandidateList = KnownAIDList.getAIDs();

        for (KnownAIDList.KnownAID terminalAIDCandidate : terminalCandidateList) {

            //ICC support for the selection of a DF file using only a 
            //partial DF name is not mandatory. However, if the ICC does 
            //support partial name selection, it shall comply with the following:
            //If, after a DF file has been successfully selected, the terminal 
            //repeats the SELECT command having P2 set to the Next Occurrence 
            //option (see Table 42) and with the same partial DF name, the card 
            //shall select a different DF file matching the partial name, 
            //if such other DF file exists.
            //Repeated issuing of the same command with no intervening application 
            //level commands shall retrieve all such files, but shall retrieve 
            //no file twice.
            //After all matching DF files have been selected, repeating the same 
            //command again shall result in no file being selected, and the card 
            //shall respond with SW1 SW2 = '6A82' (file not found).


            Log.commandHeader("Direct selection of Application to generate candidate list - "+terminalAIDCandidate.getName());
            command = EMVAPDUCommands.selectByDFName(terminalAIDCandidate.getAID().getAIDBytes());
            CardResponse selectAppResponse = EMVUtil.sendCmd(terminal, command);

            //TODO merge data if AID already found (to prevent PARTIAL AID being listed as app in EMV card dump)

            if (selectAppResponse.getSW() == SW.FUNCTION_NOT_SUPPORTED.getSW()) { //6a81
                Log.info("'SELECT File using DF name = AID' not supported");
            } else if (selectAppResponse.getSW() == SW.FILE_OR_APPLICATION_NOT_FOUND.getSW()){
                if(Arrays.equals(terminalAIDCandidate.getAID().getAIDBytes(), Util.fromHexString("a0 00 00 01 67 41 30 00 ff"))
                        && selectAppResponse.getData() != null
                        && selectAppResponse.getData().length > 0){
                    //The JCOP identify applet is not selectable (gives SW = 6a82), but if present, it returns data
                    smartCard.addAID(terminalAIDCandidate.getAID());
                    if(selectAppResponse.getData().length == 19){
                        //TODO Parse JCOP data
                    }
                    
                }
            } else if (selectAppResponse.getSW() == SW.SELECTED_FILE_INVALIDATED.getSW()) {
                //App blocked
                Log.info("Application BLOCKED");
            } else if (selectAppResponse.getSW() == SW.SUCCESS.getSW()) {
                smartCard.addAID(terminalAIDCandidate.getAID());

                if (terminalAIDCandidate.partialMatchAllowed()) {
                    Log.debug("Partial match allowed. Selecting next occurrence");
                    //TODO save record data from partial selection (eg 9f65)

                    EMVApplication appTemplate = new EMVApplication();
                    try {
                        EMVUtil.parseFCIADF(selectAppResponse.getData(), appTemplate); //Check if FCI can be parsed (if the app is a valid EMV app)
                        if (appTemplate.getAID() != null) {
                            smartCard.addAID(appTemplate.getAID());
                        } else {
                            //No AID found in ADF.

                        }
                    } catch (SmartCardException parseEx) {
                        //The application is not a valid EMV app
                        Log.debug(Util.getStackTrace(parseEx));
                        Log.info("Unable to parse FCI ADF for AID=" + terminalAIDCandidate.getAID() + ". Skipping");
                    }

                    byte[] previousResponse = selectAppResponse.getData();
                    
                    boolean hasNextOccurrence = true;
                    while (hasNextOccurrence) {
                        command = EMVAPDUCommands.selectByDFNameNextOccurrence(terminalAIDCandidate.getAID().getAIDBytes());
                        selectAppResponse = EMVUtil.sendCmd(terminal, command);
                        
                        //Workaround: Some cards seem to misbehave. 
                        //Abort if current response == previous response
                        if(Arrays.equals(previousResponse, selectAppResponse.getData())){
                            Log.debug("Current response was equal to the previous response. Aborting 'select next occurrence'");
                            break;
                        }
                        
                        Log.debug("Select next occurrence SW: " + Util.short2Hex(selectAppResponse.getSW()) + " (Stop if SW=" + Util.short2Hex(SW.FILE_OR_APPLICATION_NOT_FOUND.getSW())+")");
                        if (selectAppResponse.getSW() == SW.FUNCTION_NOT_SUPPORTED.getSW()) { //6a81
                            Log.info("'SELECT File using DF name = AID' not supported");
                        } else if (selectAppResponse.getSW() == SW.SELECTED_FILE_INVALIDATED.getSW()) {
                            //App blocked
                            Log.info("Application BLOCKED");
                        } else if (selectAppResponse.getSW() == SW.FILE_OR_APPLICATION_NOT_FOUND.getSW()) {
                            hasNextOccurrence = false;
                            Log.debug("No more occurrences");
                        } else if (selectAppResponse.getSW() == SW.SUCCESS.getSW()) {

                            EMVApplication appCandidate = new EMVApplication();
                            try {
                                EMVUtil.parseFCIADF(selectAppResponse.getData(), appCandidate); //Check if FCI can be parsed (if the app is a valid EMV app)
                                if (appTemplate.getAID() != null) {
                                    smartCard.addAID(appTemplate.getAID());
                                } else {
                                    //No AID found in ADF.

                                }
                            } catch (SmartCardException parseEx) {
                                //The application is not a valid EMV app
                                Log.debug(Util.getStackTrace(parseEx));
                                Log.info("Unable to parse FCI ADF for AID=" + terminalAIDCandidate.getAID() + ". Skipping");
                            }
                        }

                    }
                } else {

                    EMVApplication appTemplate = new EMVApplication();
//                    appTemplate.setAID(terminalAIDCandidate.getAID());
                    try {
                        EMVUtil.parseFCIADF(selectAppResponse.getData(), appTemplate); //Check if FCI can be parsed (if the app is a valid EMV app)
                        if (appTemplate.getAID() != null) {
                            smartCard.addAID(appTemplate.getAID());
                        } else {
                            //No AID found in ADF. 
                        }
                    } catch (SmartCardException parseEx) {
                        //The application is not a valid EMV app
                        Log.debug(Util.getStackTrace(parseEx));
                        Log.info("Unable to parse FCI ADF for AID=" + terminalAIDCandidate.getAID() + ". Skipping");
                    }
                }
            }
        }

        
    }
    
    //Try SELECT next occurrence for all AIDs? (Especially when selecting partial AIDs)
    
    //Smart Card Discovery Process
    //-ATR (match with regex)
    //-Select MF
    //-Select 1PAY.SYS.DDF01
    //-Select 2PAY.SYS.DDF01


        //-If MS Plug&Play AID [A000000397 4349445F0100] (->GET DATA command to locate the Windows proprietary tag 0x7F68 (ASN.1 DER encoded).
        //If the smart card supports the GET DATA command, the Windows smart card framework expects the card to return a DER-TLV encoded byte array that is formatted in the following ASN.1 Structure.
        //CardID ::= SEQUENCE {
        //  version Version DEFAULT v1,
        //  vendor VENDOR,
        //                   guids GUIDS }
        //
        //Version ::= INTEGER {v1(0), v2(1), v3(2)}
        //VENDOR ::= IA5STRING(SIZE(0..8))
        //GUID ::= OCTET STRING(SIZE(16))
        //GUIDS ::= SEQUENCE OF GUID


    
    //-Also check if card has different cold and warm reset ATRs
    //-Check for presence of known card managers
    public enum CardType{
        EMV, PIV, IDMP, SIM, MIXED, UNKNOWN, GLOBAL_PLATFORM, VISA_OPENPLATFORM, CONAX //Conax cards are not known to contain other applications
    }
    
    public CardType getPreferredDiscoveredCardType(){
        //TODO what if multiple cards/terminals/readers?
        //Return CardConnection Handle?
        return null;
    }
    
    public List<CardType> getAllDiscoveredCardTypes(){
        return null;
    }
}
