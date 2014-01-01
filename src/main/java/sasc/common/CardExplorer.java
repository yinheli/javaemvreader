/*
 *  Copyright 2010 sasc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package sasc.common;

import sasc.emv.EMVApplication;
import sasc.emv.EMVSession;
import sasc.iso7816.SmartCardException;
import sasc.lookup.ATR_DB;
import sasc.terminal.CardConnection;
import sasc.terminal.NoTerminalsAvailableException;
import sasc.terminal.TerminalAPIManager;
import sasc.terminal.TerminalException;
import sasc.terminal.TerminalProvider;
import sasc.util.BuildProperties;
import sasc.util.Log;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class CardExplorer {

    //Declare SmartCard here, so in case some exception is thrown, we can still try to dump all the information we found
    SmartCard smartCard = null;

    public SmartCard getEMVCard(){
        return smartCard;
    }

    public void start() {

        CardConnection cardConnection = null;
        try {
            Context.init();
            TerminalProvider terminalProvider = TerminalAPIManager.getProvider(TerminalAPIManager.SelectionPolicy.ANY_PROVIDER);
            Log.info(BuildProperties.getProperty("APP_NAME", "JER") + " built on " + BuildProperties.getProperty("BUILD_TIMESTAMP", "N/A"));
            
            while(true) {
                if (terminalProvider.listTerminals().isEmpty()) {
                    Log.info("No smart card readers found. Please attach readers(s)");

                }
                while (terminalProvider.listTerminals().isEmpty()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Log.debug(ex.toString());
                    }
                }
                Log.info("Please insert a Smart Card into any attached reader.");
                try{
                    cardConnection = terminalProvider.connectAnyTerminal(); //Waits for card present
                    break;
                }catch(NoTerminalsAvailableException ntex){
                    Log.debug(Util.getStackTrace(ntex));
                    //All Terminals were removed while waiting for card
                    //go back and try again
                }
            }
            Log.info("OK, card found");
			Log.info("Using terminal: "+cardConnection.getTerminal().getName());

            //TODO
            //If the ATR received following a cold reset as described in EMV Book 1 section 6.1.3.1 does not
            //conform to the specification in EMV Book 1 section 8, the terminal shall initiate a warm reset
            //and obtain an ATR from the ICC as follows (see Figure 8):

            //TODO check prefs to see if we should
            // - initCard normally
            // - what app to select (by name or all) (or use list of known AIDs), or brute force AIDs
            // - brute force SFIs and Records
            // - check if cardmanager A0000000000300000 (or other CM AID?) is present?
            //----> add to SessionProcessingEnv

            //Ideally the top level functions should be 'explore' (read as much data as possible, see above), or 'perform transaction' (production level function)

            SessionProcessingEnv env = new SessionProcessingEnv();
            env.setReadMasterFile(true);
//            env.setSelectAllRIDs(true);
            env.setProbeAllKnownAIDs(false);

            CardSession cardSession = CardSession.createSession(cardConnection, env);

            smartCard = cardSession.initCard();

            EMVSession session = EMVSession.startSession(smartCard, cardConnection);


//            AID visaClassicAID = new AID("a0 00 00 00 03 10 10");

            session.initContext();
            for (EMVApplication app : smartCard.getApplications()) {
                try{ //If processing an app fails, just skip it
                    session.selectApplication(app);
                    session.initiateApplicationProcessing(); //GET PROCESSING OPTIONS + READ RECORD(s)
                    //The Read EMVApplication Data function is performed immediately following the Initiate EMVApplication Processing function

                    if (!app.isInitializedOnICC()) {
                        //Skip if GPO failed (might not be a EMV card)
                        continue;
                    }

                    if (app.getApplicationInterchangeProfile() != null) {
                        if (app.getApplicationInterchangeProfile().isCDASupported()) {
                            //TODO
    //                        session.xxxx()
                        }
                        //else //TODO
                        if (app.getApplicationInterchangeProfile().isDDASupported()) {
                            session.internalAuthenticate();
                        }
                    }

                    session.readPINTryCounter();

                    //Only plaintext PIN verification has been implemented
                    //check CVM if app supports plaintext PIN verified by ICC
                    //Be VERY CAREFUL when running this, as it WILL block the application if the PIN Try Counter reaches 0
                    if(app.getPINTryCounter() > 0){
                    //  if (myAID.equals(app.getAID())) {
                    //      char[] pin = {'1', '2', '3', '4'};
                    //      session.verifyPIN(pin, true);
                    //      Arrays.fill(pin, ' '); //Clear pin from memory
                    //  }
                    }

                    session.readAdditionalData(); //ATC, Last Online ATC, Log Format

                    //getChallenge -> only if DDA/CDA?
                    session.getChallenge();

                    //session.verifySSAD();

                    //session.generateAC();

                    //next steps: Book 3 page 113
                    //-terminal shall perform offline data authentication
                    //-terminal action analysis

                } catch(Exception e) {
                    e.printStackTrace(System.err);
                    Log.info(String.format("Error processing app: %s. Skipping app: %s", e.getMessage(), app.toString()));
                    continue;
                }
            }



            Log.info("\n");
            Log.info("Finished Processing card.");
            Log.info("Now dumping card data in a more readable form:");
            Log.info("\n");
            //See the finally clause
        } catch (TerminalException ex) {
            ex.printStackTrace(System.err);
            Log.info(ex.toString());
        } catch (UnsupportedCardException ex) {
            System.err.println("Unsupported card: " + ex.getMessage());
            Log.info(ex.toString());
            if (cardConnection != null) {
                System.err.println("ATR: " + Util.prettyPrintHexNoWrap(cardConnection.getATR()));
                System.err.println(ATR_DB.searchATR(cardConnection.getATR()));
            }
        } catch (SmartCardException ex) {
            ex.printStackTrace(System.err);
            Log.info(ex.toString());
        } finally {
            if (cardConnection != null){
                try{
                    cardConnection.disconnect(true);
                }catch(TerminalException ex){
                    ex.printStackTrace(System.err);
                }
            }
            if (smartCard != null) {
                try {
                    int indent = 0;
                    Log.getPrintWriter().println("======================================");
                    Log.getPrintWriter().println("             [Smart Card]             ");
                    Log.getPrintWriter().println("======================================");
                    smartCard.dump(Log.getPrintWriter(), indent);
                    Log.getPrintWriter().println("---------------------------------------");
                    Log.getPrintWriter().println("                FINISHED               ");
                    Log.getPrintWriter().println("---------------------------------------");
                    Log.getPrintWriter().flush();
                } catch (RuntimeException ex) {
                    ex.printStackTrace(System.err);
                }
                Log.info("");
            } else if (cardConnection != null) {
                Log.info(new sasc.iso7816.ATR(cardConnection.getATR()).toString());
            }
        }
    }
}
