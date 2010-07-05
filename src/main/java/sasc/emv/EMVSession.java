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

import java.io.ByteArrayInputStream;
import sasc.terminal.CardResponse;
import sasc.terminal.TerminalException;
import sasc.terminal.CardConnection;
import sasc.terminal.TerminalProfile;
import sasc.util.Util;

/**
 * Holds session related information
 * 
 * @author sasc
 */
public class EMVSession {

    private EMVCard currentCard = null;
    private SessionProcessingEnv sessionEnv;
    private CardConnection terminal;
    //TODO find a better way to verify correct invocation of methods (flow)?
    boolean cardInitalized = false;
    boolean appProcessingInitialized = false;
    boolean appDataRead = false;

    public static EMVSession startSession(SessionProcessingEnv env, CardConnection terminal) {
        if (env == null || terminal == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        return new EMVSession(env, terminal);
    }

    private EMVSession(SessionProcessingEnv env, CardConnection terminal) {
        this.sessionEnv = env;
        this.terminal = terminal;
    }

    public EMVCard getCurrentCard() {
        return currentCard;
    }

    /**
     * Initializes the card by reading all Global data and FCI/DDF "1PAY.SYS.DDF01"
     * (and some other data outside of the EMV spec)
     */
    public EMVCard initCard() throws TerminalException {

        if (cardInitalized) {
            throw new EMVException("Card already initalized. Create new Session to init new card.");
        }

        currentCard = new EMVCard(new sasc.terminal.ATR(terminal.getATR()));
        Log.debug("terminal: " + terminal);

        String command;
        int SW1;
        int SW2;

        if (sessionEnv.readMasterFile()) {
            //TODO implement MF reading
            Log.commandHeader("SELECT FILE Master File (if available)");

            command = EMVCommands.selectMasterFile();

            CardResponse selectMFResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectMFResponse.getSW1();
            SW2 = (byte) selectMFResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //What kind of data is returned by the select MF command?
                MasterFile mf = new MasterFile(selectMFResponse.getData());
                getCurrentCard().setMasterFile(mf);
            }

            Log.commandHeader("SELECT FILE Master File by identifier (if available)");

            command = EMVCommands.selectMasterFileByIdentifier();

            CardResponse selectMFByIdResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectMFByIdResponse.getSW1();
            SW2 = (byte) selectMFByIdResponse.getSW2();

            if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                //What kind of data is returned by the select MF command?
                MasterFile mf = new MasterFile(selectMFByIdResponse.getData());
                getCurrentCard().setMasterFile(mf);
            }
        }

        Log.commandHeader("SELECT FILE 1PAY.SYS.DDF01 to get the PSE directory");

        command = EMVCommands.selectPSE();

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

            DDF pseDDF = EMVUtil.parseFCIDDF(selectPSEdirResponse.getData(), currentCard);

            getCurrentCard().setPSE(pseDDF);

            int sfi = pseDDF.getSFI().getValue();

            byte recordNum = 1;
            do {

                Log.commandHeader("Send READ RECORD to read all records in SFI " + sfi);

                command = EMVCommands.readRecord((int) recordNum, sfi);

                CardResponse selectReadRecordResponse = EMVUtil.sendCmd(terminal, command);

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


                SW1 = (byte) selectReadRecordResponse.getSW1();
                SW2 = (byte) selectReadRecordResponse.getSW2();

                if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
                    EMVUtil.parsePSERecord(selectReadRecordResponse.getData(), currentCard);
                }

                recordNum++;

            } while (SW1 == (byte) 0x90 && SW2 == (byte) 0x00); //while SW1SW2 != 6a83

        } else {
            //TODO
            //An ICC need not contain PSE. Direct selection of Application might be used on some cards.
            //For now we only support the PSE method
            //PSE alternatives (EMV book 1 page 161)
            throw new UnsupportedCardException("Unsupported card. PSE '1PAY.SYS.DDF01' not found");
        }
        cardInitalized = true;
        return currentCard;
    }

    public void selectApplication(Application app) throws TerminalException {

        if (app == null) {
            throw new IllegalArgumentException("Parameter 'app' cannot be null");
        }
        if (!cardInitalized) {
            throw new EMVException("Card not initialized. Call initCard() first");
        }
        Application currentSelectedApp = currentCard.getSelectedApplication();
        if (currentSelectedApp != null && app.getAID().equals(currentSelectedApp.getAID())) {
            throw new EMVException("Application already selected. AID: " + app.getAID());
        }

        AID aid = app.getAID();
        String command;

        Log.commandHeader("Select application by AID");
        command = EMVCommands.select(aid.getAIDBytes());

        CardResponse selectAppResponse = EMVUtil.sendCmd(terminal, command);

        if (selectAppResponse.getSW() == SW.SELECTED_FILE_INVALIDATED.getSW()) {
            //App blocked
            throw new EMVException("Application " + Util.byteArrayToHexString(aid.getAIDBytes()) + " blocked");
        }

        EMVUtil.parseFCIADF(selectAppResponse.getData(), app);

        //TODO (EMV book 1 : 11.3.5 page 150)
        //if AID == PARTIAL then set P2 = 02 (next occurence)
        //Repeat until SW1SW2 = 6a82 (file not found)
        //Where should we put such code?




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

        getCurrentCard().setSelectedApplication(app);

    }

    public void initiateApplicationProcessing() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        if (app == null) {
            throw new EMVException("No application selected. Call selectApplication(Application) first");
        }
        if (app.isInitializedOnICC()) {
            throw new EMVException("Application already initialized for processing. AID=" + app.getAID());
        }


        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send GET PROCESSING OPTIONS command");



        //If the PDOL does not exist, the GET PROCESSING OPTIONS command uses a command data field of '8300',
        //indicating that the length of the value field in the command data is zero.

        DOL pdol = app.getPDOL();

        if (pdol != null && pdol.getTagAndLengthList().size() > 0) {
            byte[] pdolResponseData = TerminalProfile.constructDOLResponse(pdol);
            command = "80 A8 00 00";
            command += " " + Util.int2Hex(pdolResponseData.length + 2) + " 83 " + Util.int2Hex(pdolResponseData.length);
            command += " " + Util.prettyPrintHexNoWrap(pdolResponseData);
        } else {
            command = "80 A8 00 00 02 83 00";
        }

        CardResponse getProcessingOptsResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getProcessingOptsResponse.getSW1();
        SW2 = (byte) getProcessingOptsResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {

            EMVUtil.parseProcessingOpts(getProcessingOptsResponse.getData(), app);
            app.setInitializedOnICC();
        }
    }

    //TODO:
    //The Read Application Data function is performed immediately following the Initiate Application Processing function
    //...so is there any point in having 2 methods? merge this into initializeAppProcessing() ?
    public void readApplicationData() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        int SW1;
        int SW2;

        //read all the records indicated in the AFL
        for (ApplicationElementaryFile aef : app.getApplicationFileLocator().getApplicationElementaryFiles()) {
            int startRecordNumber = aef.getStartRecordNumber();
            int endRecordNumber = aef.getEndRecordNumber();

            for (int recordNum = startRecordNumber; recordNum <= endRecordNumber; recordNum++) {
                Log.commandHeader("Send READ RECORD to read SFI " + aef.getSFI().getValue() + " record " + recordNum);

                command = EMVCommands.readRecord(recordNum, aef.getSFI().getValue());

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
                    throw new EMVException("Reading application data failed for SFI " + aef.getSFI().getValue() + " Record Number: " + recordNum);
                }
            }

        }

        //After recieving the GET PROCESSING OPTIONS C-APDU, the card application checks whether the flow conditions
        //needed to process this command are fulfilled.
        //-First, it checks that there is currently an application selected in the card
        //-Second, the card checks that this is the first time in the current card session that the terminal issues
        // the GET PROCESSING OPTIONS command
        //
        //If any of these conditions are not respected, the card responds with SW1SW2=6985 ("Command not allowed; conditions of use not satisfied")

    }

    //TODO this method is just for testing. Must be merged into some AUTHENTICATE/TRANSACTION method
    public void getChallenge() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
//        int SW1;
//        int SW2;

        Log.commandHeader("GET CHALLENGE");
        command = EMVCommands.getChallenge();

        CardResponse getChallengeResponse = EMVUtil.sendCmd(terminal, command);
    }

    public void verifyPIN(int pin, boolean transmitInPlaintext) throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        command = EMVCommands.verifyPIN(pin, transmitInPlaintext);
        Log.commandHeader("VERIFY (PIN)");

        CardResponse verifyResponse = EMVUtil.sendCmd(terminal, command);

        //TODO handle correctly
        if (verifyResponse.getSW() != SW.SUCCESS.getSW()) {
            if (verifyResponse.getSW() == SW.COMMAND_NOT_ALLOWED_AUTHENTICATION_METHOD_BLOCKED.getSW()) {
                throw new EMVException("No more retries left. CVM blocked");
            }
            if (verifyResponse.getSW1() == (byte) 0x63 && (verifyResponse.getSW2() & 0xF0) == (byte) 0xC0) {
                int numRetriesLeft = (verifyResponse.getSW2() & 0x0F);
                //TODO
            }
        }

    }

    private void verifyAppInitialized(Application app) {
        if (app == null) {
            throw new EMVException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
        }
        if (!app.isInitializedOnICC()) {
            throw new EMVException("Application not initialized on ICC initializeApplicationProcessing() first");
        }
    }

    //TODO (page 98 book 3):
    //After all records identified by the AFL have been processed,
    //the Static Data Authentication Tag List is processed, if it exists.
    //If the Static Data Authentication Tag List exists, it shall contain
    //only the tag for the Application Interchange Profile.
    //TODO not implemented yet
    public void internalAuthenticate() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send INTERNAL AUTHENTICATE command");

        String challenge = Util.prettyPrintHexNoWrap(Util.generateRandomBytes(4));

        //TODO
        byte[] authenticationRelatedData = null; //data according to DDOL

        command = EMVCommands.internalAuthenticate(authenticationRelatedData);
        //The data field of the command message contains the authentication-related data proprietary to an application. It is coded according to the DDOL as defined in Book 2.

        //The response contains the "Signed Dynamic Application Data"
        //See Table 15, book 2 (page 79)
        CardResponse internalAuthenticateResponse = EMVUtil.sendCmd(terminal, command);

        //TODO parse "Signed Dynamic Application Data"

    }

    public void externalAuthenticate() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;

        Log.commandHeader("Send EXTERNAL AUTHENTICATE command");

        command = EMVCommands.externalAuthenticate(null, null);
        CardResponse externalAuthenticateResponse = EMVUtil.sendCmd(terminal, command);
        //No data field is returned in the response message
        //'9000' indicates a successful execution of the command.
        //'6300' indicates "Issuer authentication failed".

        if (externalAuthenticateResponse.getSW() != SW.SUCCESS.getSW()) {
            if (externalAuthenticateResponse.getSW() == SW.AUTHENTICATION_FAILED.getSW()) {
                throw new EMVException("Issuer authentication failed");
            }
            throw new EMVException("Unexpected response: " + Util.short2Hex(externalAuthenticateResponse.getSW()));
        }
    }

    public void generateAC() throws TerminalException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void readAdditionalData() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        verifyAppInitialized(app);

        String command;
        int SW1;
        int SW2;

        Log.commandHeader("Send GET DATA command to find the Application Transaction Counter (ATC)");
        command = "80 CA 9F 36 00";
        CardResponse getDataATCResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataATCResponse.getSW1();
        SW2 = (byte) getDataATCResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataATCResponse.getData()));
            app.setATC(Util.byteToInt(tlv.getValueBytes()[0], tlv.getValueBytes()[1]));
        }

        Log.commandHeader("Send GET DATA command to find the Last Online ATC Register");
        command = "80 CA 9F 13 00";
        CardResponse getDataLastOnlineATCRegisterResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataLastOnlineATCRegisterResponse.getSW1();
        SW2 = (byte) getDataLastOnlineATCRegisterResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataLastOnlineATCRegisterResponse.getData()));
            app.setLastOnlineATCRecord(Util.byteToInt(tlv.getValueBytes()[0],
                    tlv.getValueBytes()[1]));
        }

        Log.commandHeader("Send GET DATA command to find the PIN Try Counter");
        command = "80 CA 9F 17 00";
        CardResponse getDataPINTryCounterResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataPINTryCounterResponse.getSW1();
        SW2 = (byte) getDataPINTryCounterResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataPINTryCounterResponse.getData()));
            app.setPINTryCounter(tlv.getValueBytes()[0]);
        }

        //If the Log Entry data element is present in the FCI Issuer Discretionary Data,
        //then get the Log Format (and continue on reading the log records...)
        Log.commandHeader("Send GET DATA command to find the Log Format");
        command = "80 CA 9F 4F 00";
        CardResponse getDataLogFormatResponse = EMVUtil.sendCmd(terminal, command);

        SW1 = (byte) getDataLogFormatResponse.getSW1();
        SW2 = (byte) getDataLogFormatResponse.getSW2();

        if (SW1 == (byte) 0x90 && SW2 == (byte) 0x00) {
            BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(getDataLogFormatResponse.getData()));
            app.setLogFormat(new LogFormat(tlv.getValueBytes()));
        }
    }

    public void bruteForceRecords() throws TerminalException {

        Application app = currentCard.getSelectedApplication();

        if (app == null) {
            throw new EMVException("No application selected. Call selectApplication(Application) and initializeApplicationProcessing() first");
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

                command = EMVCommands.readRecord(recordNum, sfi);

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
        System.out.println("Number of Records found: "+numRecordsFound);
    }
}
