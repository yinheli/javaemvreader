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

import sasc.common.SessionProcessingEnv;
import sasc.common.SmartCard;
import sasc.iso7816.MasterFile;
import sasc.util.Log;
import sasc.common.UnsupportedCardException;
import sasc.iso7816.SmartCardException;
import sasc.iso7816.BERTLV;
import sasc.iso7816.AID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import sasc.common.CardScanner;
import sasc.iso7816.Iso7816Commands;
import sasc.iso7816.TLVException;
import sasc.pcsc.PCSC;
import sasc.terminal.KnownAIDList;
import sasc.terminal.KnownAIDList.KnownAID;
import sasc.terminal.CardResponse;
import sasc.terminal.TerminalException;
import sasc.terminal.CardConnection;
import sasc.util.Util;

/**
 * Holds EMV session related information
 *
 * @author sasc
 */
public class EMVSession {

    private SmartCard card = null;
//    private SessionProcessingEnv sessionEnv;
    private CardConnection terminal;
    //TODO find a better way to verify correct invocation of methods (flow)? EMVSessionState?
    private boolean contextInitialized = false;

    public static EMVSession startSession(SmartCard card, CardConnection terminal) {
        if (card == null || terminal == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        return new EMVSession(card, terminal);
    }

    private EMVSession(SmartCard card, CardConnection terminal) {
        this.card = card;
        this.terminal = terminal;
    }

    public SmartCard getCard() {
        return card;
    }

    /**
     * Initializes the card by reading all Global data and FCI/DDF
     * "1PAY.SYS.DDF01" (and some other data outside of the EMV spec)
     */
    public void initContext() throws TerminalException {

        if (contextInitialized) {
            throw new SmartCardException("EMV context already initalized.");
        }

        String command;
        int SW1;
        int SW2;

        /*
         * The terminal has a list containing the EMVApplication Identifier
         * (AID) of every EMV application that it is configured to support, and
         * the terminal must generate a candidate list of applications that are
         * supported by both the terminal and card. The terminal may attempt to
         * obtain a directory listing of all card applications from the card's
         * PSE. If this is not supported or fails to find a match, the terminal
         * must iterate through its list asking the card whether it supports
         * each individual AID.
         *
         * If there are multiple applications in the completed candidate list,
         * or the application requires it, then the cardholder will be asked to
         * choose an application; otherwise it may be automatically selected
         */


        //First, try the "Payment System Directory selection method".
        //if that fails, try direct selection by using a terminal resident list of supported AIDs

        Log.commandHeader("SELECT FILE 1PAY.SYS.DDF01 to get the PSE directory");

        command = EMVAPDUCommands.selectPSE();

        CardResponse selectPSEdirResponse = EMVUtil.sendCmd(terminal, command);

        //Example result from the command above:

        //6f 20 //FCI Template
        //      84 0e //DF Name
        //            31 50 41 59 2e 53 59 53 2e 44 44 46 30 31
        //      a5 0e //FCI Proprietary Template
        //            88 01 //SFI of the Directory Elementary File
        //                  02
        //            5f 2d 04 //Language Preference
        //                     6e 6f 65 6e
        //            9f 11 01 //Issuer Code Table Index
        //                     01 (=ISO 8859-1)

        SW1 = (byte) selectPSEdirResponse.getSW1();
        SW2 = (byte) selectPSEdirResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            //PSE is available

            try{
                DDF pseDDF = EMVUtil.parseFCIDDF(selectPSEdirResponse.getData(), card);
                card.setType(SmartCard.Type.CONTACTED);
                getCard().setPSE(pseDDF);
            }catch(TLVException tlvex){
                Log.debug(Util.getStackTrace(tlvex));
            }
        } else { // SW1SW2 = 6a81

            //try to select the  PPSE (Proximity Payment System Environment) 2PAY.SYS.DDF01

            Log.commandHeader("SELECT FILE 2PAY.SYS.DDF01 to get the PPSE directory");

            command = EMVAPDUCommands.selectPPSE();

            CardResponse selectPPSEdirResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectPPSEdirResponse.getSW1();
            SW2 = (byte) selectPPSEdirResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //PPSE is available
                try{
                    DDF ppseDDF = EMVUtil.parseFCIDDF(selectPPSEdirResponse.getData(), card);
                    card.setType(SmartCard.Type.CONTACTLESS);
                    card.setPSE(ppseDDF);
                }catch(TLVException tlvex){
                    Log.debug(Util.getStackTrace(tlvex));
                }

            }
        }

        if(card.getPSE() != null && card.getPSE().getSFI() != null){
            try {
                int sfi = card.getPSE().getSFI().getValue();

                byte recordNum = 1;
                do {

                    Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                    command = EMVAPDUCommands.readRecord((int) recordNum, sfi);

                    CardResponse readRecordResponse = EMVUtil.sendCmd(terminal, command);

                    //Example Response from the command above:

                    //70 23
                    //      61 21
                    //            4f 07 //AID
                    //                  a0 00 00 00 03 10 10
                    //            50 04 //Application Label
                    //                  56 49 53 41 (=VISA)
                    //            9f 12 0c //Application Preferred Name
                    //                     56 49 53 41 20 43 6c 61 73 73 69 63 (=VISA Classic)
                    //            87 01 //Application priority indicator
                    //                  02


                    SW1 = (byte) readRecordResponse.getSW1();
                    SW2 = (byte) readRecordResponse.getSW2();

                    if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                        EMVUtil.parsePSERecord(readRecordResponse.getData(), card);
                    }

                    recordNum++;

                } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83

            } catch(TLVException tlvex) {
                Log.debug(Util.getStackTrace(tlvex));
            }
        }

        if (card.getApplications().isEmpty()) { //Never null

            //An ICC need not contain PSE, or the PSE might not contain any applications.
            //Direct selection of EMVApplication might be used on some cards.
            //PSE alternatives (EMV book 1 page 161)

            if(!card.allKnownAidsProbed()){
                Log.info("No PSE found. Using direct selection by AID to generate candidate list");
                //TODO only probe for known EMV AIDs
                SessionProcessingEnv sessionEnv = new SessionProcessingEnv();
                sessionEnv.setProbeAllKnownAIDs(true);
                CardScanner scanner = new CardScanner(getCard(), terminal, sessionEnv);
                scanner.probeAllKnownAIDs();
            }
        }

        //Still no applications?
//        if (card.getApplications().isEmpty()) { //Never null
//            throw new UnsupportedCardException("No PSE '1PAY.SYS.DDF01' or application(s) found. Might not be an EMV card");
//        }

        contextInitialized = true;
    }

    public void selectApplication(EMVApplication app) throws TerminalException {

        if (app == null) {
            throw new IllegalArgumentException("Parameter 'app' cannot be null");
        }
        if (!contextInitialized) {
            throw new SmartCardException("Card not initialized. Call initCard() first");
        }
        EMVApplication currentSelectedApp = card.getSelectedApplication();
        if (currentSelectedApp != null && app.getAID().equals(currentSelectedApp.getAID())) {
            throw new SmartCardException("Application already selected. AID: " + app.getAID());
        }

        AID aid = app.getAID();
        String command;

        Log.commandHeader("Select application by AID");
        command = EMVAPDUCommands.selectByDFName(aid.getAIDBytes());

        CardResponse selectAppResponse = EMVUtil.sendCmd(terminal, command);

        if (selectAppResponse.getSW() == SW.SELECTED_FILE_INVALIDATED.getSW()) {
            //App blocked
            Log.info("Application BLOCKED");
            //TODO abort execution if app blocked?
            //throw new SmartCardException("EMVApplication " + Util.byteArrayToHexString(aid.getAIDBytes()) + " blocked");
        }

        EMVUtil.parseFCIADF(selectAppResponse.getData(), app);

        //Example Response from previous command:
        //      6f 37
        //      84 07 //AID
        //            a0 00 00 00 03 10 10
        //      a5 2c
        //            50 04 //Application Label (= VISA)
        //                  56 49 53 41
        //            87 01 //Application priority indicator
        //                  02
        //            9f 38 06 //PDOL
        //                     9f 1a
        //                           02
        //                     5f 2a
        //                           02
        //            5f 2d 04 //Language Preference
        //                     6e 6f 65 6e (= no en)
        //            9f 11 01 //Issuer code table index
        //                     01
        //            9f 12 0c //Application Preferred name
        //                     56 49 53 41 20 43 6c 61 73 73 69 63 (= VISA Classic)



        //The card supplies the PDOL (if present) to the terminal as part of the FCI
        //provided in response to the SELECT FILE (Application Definition File) command

        //If PDOL present, the ICC requires parameters from the Terminal.
        //In this specific example:
        //9F1A = Indicates the country code of the terminal, represented according to ISO 3166
        //578 Norway = 0x0578
        //5F2A = Transaction Currency Code: Indicates the currency code of the transaction according to ISO 4217 (numeric 3)
        //NORWAY  Norwegian Krone  NOK  578  == 0x0578
        //PDOL response data (used in the GET PROCESSING OPTS command) = 05 78 05 78

        getCard().setSelectedApplication(app);

    }

    public void initiateApplicationProcessing() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        if (app == null) {
            throw new SmartCardException("No application selected. Call selectApplication(Application) first");
        }
        if (app.isInitializedOnICC()) {
            throw new SmartCardException("Application already initialized for processing. AID=" + app.getAID());
        }


        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send GET PROCESSING OPTIONS command");



        //If the PDOL does not exist, the GET PROCESSING OPTIONS command uses a command data field of '8300',
        //indicating that the length of the value field in the command data is zero.

        DOL pdol = app.getPDOL();

        command = EMVAPDUCommands.getProcessingOpts(pdol);

        CardResponse getProcessingOptsResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getProcessingOptsResponse.getSW1();
        SW2 = (byte) getProcessingOptsResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {

            EMVUtil.parseProcessingOpts(getProcessingOptsResponse.getData(), app);
            app.setInitializedOnICC();

            //read all the records indicated in the AFL
            for (ApplicationElementaryFile aef : app.getApplicationFileLocator().getApplicationElementaryFiles()) {
                int startRecordNumber = aef.getStartRecordNumber();
                int endRecordNumber = aef.getEndRecordNumber();

                for (int recordNum = startRecordNumber; recordNum <= endRecordNumber; recordNum++) {
                    Log.commandHeader("Send READ RECORD to read SFI " + aef.getSFI().getValue() + " record " + recordNum);

                    command = EMVAPDUCommands.readRecord(recordNum, aef.getSFI().getValue());

                    CardResponse readAppDataResponse = EMVUtil.sendCmd(terminal, command);

                    SW1 = (byte) readAppDataResponse.getSW1();
                    SW2 = (byte) readAppDataResponse.getSW2();

                    if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {

                        EMVUtil.parseAppRecord(readAppDataResponse.getData(), app);
                        boolean isInvolvedInOfflineDataAuthentication = (recordNum - startRecordNumber + 1) <= aef.getNumRecordsInvolvedInOfflineDataAuthentication();
                        Record record = new Record(readAppDataResponse.getData(), recordNum, isInvolvedInOfflineDataAuthentication);
                        aef.setRecord(recordNum, record);
                    } else {
                        //Any SW1 SW2 other than '9000' passed to the application layer as a result
                        //of reading any record shall cause the transaction to be terminated [spec]
                        throw new SmartCardException("Reading application data failed for SFI " + aef.getSFI().getValue() + " Record Number: " + recordNum);
                    }
                }

            }
            app.setAllAppRecordsInAFLRead();


            //After receiving the GET PROCESSING OPTIONS C-APDU, the card application checks whether the flow conditions
            //needed to process this command are fulfilled.
            //-First, it checks that there is currently an application selected in the card
            //-Second, the card checks that this is the first time in the current card session that the terminal issues
            // the GET PROCESSING OPTIONS command
            //
            //If any of these conditions are not respected, the card responds with SW1SW2=6985 ("Command not allowed; conditions of use not satisfied")


            //(page 98 book 3):
            //After all records identified by the AFL have been processed,
            //the Static Data Authentication Tag List is processed, if it exists.
            //If the Static Data Authentication Tag List exists, it shall contain
            //only the tag for the EMVApplication Interchange Profile.
        }
    }

    //TODO this method is just for testing. Must be merged into some AUTHENTICATE/TRANSACTION method
    //make "private"
    public void getChallenge() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
//        int SW1;
//        int SW2;

        Log.commandHeader("GET CHALLENGE");
        command = EMVAPDUCommands.getChallenge();

        //Parse raw bytes only, no BERTLV
        CardResponse getChallengeResponse = EMVUtil.sendCmdNoParse(terminal, command);
    }

    public void verifyPIN(char[] pin, boolean transmitInPlaintext) throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        command = EMVAPDUCommands.verifyPIN(pin, transmitInPlaintext);
        Log.commandHeader("VERIFY (PIN)");

        CardResponse verifyResponse = EMVUtil.sendCmd(terminal, command);

        //TODO handle correctly
        if (verifyResponse.getSW() != SW.SUCCESS.getSW()) {
            if (verifyResponse.getSW() == SW.COMMAND_NOT_ALLOWED_AUTHENTICATION_METHOD_BLOCKED.getSW()) {
                throw new SmartCardException("No more retries left. CVM blocked");
            }
            if (verifyResponse.getSW1() == (byte) 0x63 && (verifyResponse.getSW2() & 0xF0) == (byte) 0xC0) {
                int numRetriesLeft = (verifyResponse.getSW2() & 0x0F);
                Log.debug("Wrong PIN. Retries left: "+numRetriesLeft);
                //TODO
            }
        }

    }

    private void verifyAppInitialized(EMVApplication app) {
        if (app == null) {
            throw new SmartCardException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
        }
        if (!app.isInitializedOnICC()) {
            throw new SmartCardException("Application " + Util.prettyPrintHexNoWrap(app.getAID().getAIDBytes()) + " not initialized on ICC initializeApplicationProcessing() first");
        }
    }

    private void verifyAllAppRecordsInAFLRead(EMVApplication app) {
        if (app == null) {
            throw new SmartCardException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
        }
        if (!app.isAllAppRecordsInAFLRead()) {
            throw new SmartCardException("Records indicated in the Application File Locator have not been read.");
        }
    }

    private void verifyDDASupported(EMVApplication app) {
        if (app == null) {
            throw new SmartCardException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
        }
        if (!app.getApplicationInterchangeProfile().isDDASupported()) {
            throw new SmartCardException("DDA not supported. Cannot perform INTERNAL AUTHENTICATE");
        }
        if (app.getICCPublicKeyCertificate() == null) {
            throw new SmartCardException("DDA supported, but no ICC Public Key Certificate found");
        }
        //Do not validate ICCPK Certificate here, as we might want to test INTERNAL AUTHENTICATE regardless of they ICCPK's validity
        //(for a production ready EMV terminal, we would want to validate the cert though)

    }

    //TODO not implemented yet
    //TODO call this method at the appropriate stage (when Terminal/ICC indicates DDA should be performed)
    public void internalAuthenticate() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);
        verifyAllAppRecordsInAFLRead(app);
        verifyDDASupported(app);

        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send INTERNAL AUTHENTICATE command");

        byte[] authenticationRelatedData = null; //data according to DDOL

        DOL ddol = app.getDDOL();
        if (ddol != null) {
            authenticationRelatedData = EMVTerminalProfile.constructDOLResponse(ddol);
        }
        if (authenticationRelatedData == null) {
            authenticationRelatedData = EMVTerminalProfile.getDefaultDDOLResponse();
        }

        command = EMVAPDUCommands.internalAuthenticate(authenticationRelatedData);
        //The data field of the command message contains the authentication-related data proprietary to an application. It is coded according to the DDOL as defined in Book 2.

        //The response contains the "Signed Dynamic EMVApplication Data"
        //See Table 15, book 2 (page 79)
        CardResponse internalAuthenticateResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) internalAuthenticateResponse.getSW1();
        SW2 = (byte) internalAuthenticateResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            if (app.getSignedDynamicApplicationData() != null) {
                throw new SmartCardException("Signed Dynamic Application Data exists. DDA already performed?");
            }
            EMVUtil.parseInternalAuthResponse(internalAuthenticateResponse.getData(), authenticationRelatedData, app);

        }
    }

    /**
     *
     * @param cryptogram mandatory 8 bytes from issuer
     * @param proprietaryData optional 1-8 bytes (proprietary)
     * @throws TerminalException
     */
    public void externalAuthenticate(byte[] cryptogram, byte[] proprietaryData) throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;

        Log.commandHeader("Send EXTERNAL AUTHENTICATE command");

        command = EMVAPDUCommands.externalAuthenticate(cryptogram, proprietaryData);
        CardResponse externalAuthenticateResponse = EMVUtil.sendCmd(terminal, command);
        //No data field is returned in the response message
        //'9000' indicates a successful execution of the command.
        //'6300' indicates "Issuer authentication failed".

        if (externalAuthenticateResponse.getSW() != SW.SUCCESS.getSW()) {
            if (externalAuthenticateResponse.getSW() == SW.AUTHENTICATION_FAILED.getSW()) {
                throw new SmartCardException("Issuer authentication failed");
            }
            throw new SmartCardException("Unexpected response: " + Util.short2Hex(externalAuthenticateResponse.getSW()));
        }
    }

//
//    80 12
//      80 Cryptogram Information Data
//      01 15
//      AB EA C7 B0 31 10 CE 74
//      06 10 0A 03 A0 00 00 Issuer Application Data (Contains proprietary application data for transmission to the issuer in an online transaction.(
//
//   80 12
//      40 Cryptogram Information Data
//      01 15
//      53 41 D1 18 4D EF 41 A2
//      06 10 0A 03 60 00 00

    public void generateAC(byte[] iccDynamicNumber) throws TerminalException {
        /**
         * p1 &= 0b00111111 = AAC = reject transaction (EMVApplication Authentication Cryptogram)
         * p1 &= 0b10111111 = TC = proceed offline (Transaction Certificate)
         * p1 &= 0b01111111 = ARQC = go online (Authorization Request Cryptogram                                                                            ) +
         * 0x00 = CDA signature not requested
         * 0x10 = CDA signature requested
         */

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;

        Log.commandHeader("Send GENERATE APPLICATION CRYPTOGRAM command");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] authorizedAmount = Util.fromHexString("00 00 00 00 00 01");
        byte[] secondaryAmount = Util.fromHexString("00 00 00 00 00 00");
        byte[] tvr = Util.fromHexString("00 00 00 00 00");
        byte[] transactionCurrencyCode = Util.fromHexString("09 78");
        byte[] transactionDate = Util.fromHexString("09 07 30");
        byte[] transactionType = Util.fromHexString("21");
        byte[] terminalUnpredictableNumber = Util.generateRandomBytes(4);
        //iccDynamicNumber
        byte[] dataAuthCode = app.getSignedStaticApplicationData().getDataAuthenticationCode();

        buf.write(authorizedAmount, 0, authorizedAmount.length);
        buf.write(secondaryAmount, 0, secondaryAmount.length);
        buf.write(tvr, 0, tvr.length);
        buf.write(transactionCurrencyCode, 0, transactionCurrencyCode.length);
        buf.write(transactionDate, 0, transactionDate.length);
        buf.write(transactionType, 0, transactionType.length);
        buf.write(terminalUnpredictableNumber, 0, terminalUnpredictableNumber.length);
        buf.write(iccDynamicNumber, 0, iccDynamicNumber.length);
        buf.write(dataAuthCode, 0, dataAuthCode.length);

        //0x40 = TC
        //0x80 = ARQC
        command = EMVAPDUCommands.generateAC((byte) 0x40, buf.toByteArray());
        CardResponse generateACResponse = EMVUtil.sendCmd(terminal, command);
        //'9000' indicates a successful execution of the command.

        if (generateACResponse.getSW() != SW.SUCCESS.getSW()) {
            throw new SmartCardException("Unexpected response: " + Util.short2Hex(generateACResponse.getSW()));
        } else {
            //TODO
            Log.info("TODO GenerateAC success");

            //80 response message template 1 contatenated values
            //77 BER-TLV encoded
        }
    }

    public void readPINTryCounter() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send GET DATA command to find the PIN Try Counter");
        command = EMVAPDUCommands.getPINTryConter();
        CardResponse getDataPINTryCounterResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataPINTryCounterResponse.getSW1();
        SW2 = (byte) getDataPINTryCounterResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataPINTryCounterResponse.getData()));
            app.setPINTryCounter(tlv.getValueBytes()[0]);
        }
    }

    public void readAdditionalData() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send GET DATA command to find the Application Transaction Counter (ATC)");
        command = EMVAPDUCommands.getApplicationTransactionCounter();
        CardResponse getDataATCResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataATCResponse.getSW1();
        SW2 = (byte) getDataATCResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataATCResponse.getData()));
            app.setATC(Util.byteToInt(tlv.getValueBytes()[0], tlv.getValueBytes()[1]));
        }

        Log.commandHeader("Send GET DATA command to find the Last Online ATC Register");
        command = EMVAPDUCommands.getLastOnlineATCRegister();
        CardResponse getDataLastOnlineATCRegisterResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataLastOnlineATCRegisterResponse.getSW1();
        SW2 = (byte) getDataLastOnlineATCRegisterResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataLastOnlineATCRegisterResponse.getData()));
            app.setLastOnlineATCRecord(Util.byteToInt(tlv.getValueBytes()[0],
                    tlv.getValueBytes()[1]));
        }

        //If the Log Entry data element is present in the FCI Issuer Discretionary Data,
        //then get the Log Format (and proceed to read the log records...)
        Log.commandHeader("Send GET DATA command to find the Log Format");
        command = EMVAPDUCommands.getLogFormat();
        CardResponse getDataLogFormatResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataLogFormatResponse.getSW1();
        SW2 = (byte) getDataLogFormatResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataLogFormatResponse.getData()));
            app.setLogFormat(new LogFormat(tlv.getValueBytes()));

            //Log Entry data element should be located in the FCI Issuer Discretionary Data
            //If it is not, then the app does not support transaction logging.
            //But we try to read the Log Entry with GET DATA if not present in FCI
            if (app.getLogEntry() != null) {
                readTransactionLog(app);
            } else {
				Log.commandHeader("Send GET DATA command to find the Log Entry SFI");
				command = EMVAPDUCommands.getData((byte)0x9f, (byte)0x4d);
				CardResponse getDataLogEntryResponse = EMVUtil.sendCmd(terminal, command);

				SW1 = (byte) getDataLogEntryResponse.getSW1();
				SW2 = (byte) getDataLogEntryResponse.getSW2();

        		if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
					app.setLogEntry(new LogEntry(getDataLogEntryResponse.getData()[0], getDataLogEntryResponse.getData()[1]));
					readTransactionLog(app);
				}
			}

        }
    }

    /**
     * Based on a patch by Thomas Souvignet -FR-
     */
    private void readTransactionLog(EMVApplication app) throws TerminalException {
        //read all the log records
        LogEntry logEntry = app.getLogEntry();
        int sfi = logEntry.getSFI().getValue();
        for (int recordNum = 1; recordNum <= logEntry.getNumberOfRecords(); recordNum++) {
            Log.commandHeader("Send READ RECORD to read LOG ENTRY SFI " + sfi + " record " + recordNum);

            String command = EMVAPDUCommands.readRecord(recordNum, logEntry.getSFI().getValue());

            CardResponse readAppDataResponse = EMVUtil.sendCmdNoParse(terminal, command);

            byte SW1 = (byte) readAppDataResponse.getSW1();
            byte SW2 = (byte) readAppDataResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                app.addTransactionLogRecord(readAppDataResponse.getData());
            } else if (SW1 == (byte) 0x6a && SW2 == (byte) 0x83) {
                return;
            } else {
                //Any SW1 SW2 other than '9000' passed to the application layer as a result
                //of reading any record shall cause the transaction (not entry log) to be terminated [spec]
                throw new SmartCardException("Reading application data failed for SFI " + sfi + " Record Number: " + recordNum);
            }
        }
    }

    public void bruteForceRecords() throws TerminalException {

        EMVApplication app = card.getSelectedApplication();

        if (app == null) {
            throw new SmartCardException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
        }

        String command;
        byte SW1;
        byte SW2;

        Log.commandHeader("Brute force SFI & Record numbers (Send READ RECORD)");

        //Valid SFI: 1 to 30
        //Valid Record numbers: 1 to 255

        int numRecordsFound = 0;

        for (int sfi = 1; sfi <= 30; sfi++) {

            for (int recordNum = 1; recordNum <= 255; recordNum++) {

                command = EMVAPDUCommands.readRecord(recordNum, sfi);

                CardResponse readRecordsResponse = EMVUtil.sendCmd(terminal, command);

                SW1 = (byte) readRecordsResponse.getSW1();
                SW2 = (byte) readRecordsResponse.getSW2();

                if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
//                if (SW1 != (byte) 0x6a && (SW2 != (byte) 0x83 || SW2 != (byte) 0x82)) { //This is used to see if we can get any other responses
                    System.out.println("***** BRUTE FORCE FOUND SOMETHING ***** SFI=" + Util.byte2Hex((byte) sfi) + " File=" + recordNum + " SW=" + Util.short2Hex(readRecordsResponse.getSW()));
                    System.out.println(Util.prettyPrintHex(readRecordsResponse.getData()));
                    EMVUtil.parseAppRecord(readRecordsResponse.getData(), app);
                    numRecordsFound++;
                }
            }
        }
        System.out.println("Number of Records found: " + numRecordsFound);
    }
    //TODO after reading apprecords ++ :
    //10.6.3 Velocity Checking (book 3)


    public static void main(String[] args){

    }
}
