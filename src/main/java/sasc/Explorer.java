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
package sasc;

import sasc.util.BuildProperties;
import sasc.emv.EMVApplication;
import sasc.iso7816.AID;
import sasc.emv.EMVCard;
import sasc.iso7816.SmartCardException;
import sasc.emv.EMVSession;
import sasc.util.Log;
import sasc.emv.SessionProcessingEnv;
import sasc.common.UnsupportedCardException;
import sasc.terminal.CardConnection;
import sasc.terminal.TerminalAPIManager;
import sasc.terminal.TerminalException;
import sasc.terminal.TerminalProvider;
import sasc.util.Util;

/**
 * TODO fix this. This is just a temporary class to make it work with both cmd line and GUI version
 * 
 * @author sasc
 */
public class Explorer {

    EMVCard emvCard = null;
    
    public EMVCard getEMVCard(){
        return emvCard;
    }
    
    public void start() {
        CardConnection cardConnection = null;
        try {
            TerminalProvider terminalProvider = TerminalAPIManager.getProvider(TerminalAPIManager.SelectionPolicy.ANY_PROVIDER);
            Log.info(BuildProperties.getProperty("APP_NAME", "JER") + " built on " + BuildProperties.getProperty("BUILD_TIMESTAMP", "N/A"));
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
            Log.info("Please insert an EMV card into any attached reader.");
            cardConnection = terminalProvider.connectAnyTerminal(); //Waits for card present
            Log.info("OK, card found");

            //TODO check prefs to see if we should
            // - initCard normally
            // - what app to select (by name or all) (or use list of known AIDs), or brute force AIDs
            // - brute force SFIs and Records
            // - check if cardmanager A0000000000300000 (or other CM AID?) is present?
            //----> add to SessionProcessingEnv

            //Ideally the top level functions should be 'explore' (read as much data as possible, see above), or 'perform transaction' (production level function)

            SessionProcessingEnv env = new SessionProcessingEnv();
            env.setReadMasterFile(true);


            EMVSession session = EMVSession.startSession(env, cardConnection);


            AID visaClassicAID = new AID("a0 00 00 00 03 10 10");

            emvCard = session.initCard();
            for (EMVApplication app : emvCard.getApplications()) {
                session.selectApplication(app);
                session.initiateApplicationProcessing(); //GET PROCESSING OPTIONS + READ RECORD(s)
                //The Read EMVApplication Data function is performed immediately following the Initiate EMVApplication Processing function

                if (!app.isInitializedOnICC()) {
                    //Skip if GPO failed
                    continue;
                }
//                session.readApplicationData(); //
                session.readAdditionalData(); //ATC, Last Online ATC, PIN Try Counter, Log Format

                //verifyPIN works with plaintext PIN for VISA Classic
                //Be VERY CAREFUL when running this, as it WILL block the application if the PIN Try Counter reaches 0
//                        if (visaClassicAID.equals(app.getAID())) {
//                            session.verifyPIN(1234, true);
//                        }

                session.getChallenge();

                //session.verifySSAD();

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

                //session.generateAC();

                //next steps: Book 3 page 113
                //-terminal shall perform offline data authentication
                //-terminal action analysis
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
            if (emvCard != null) {
                try {
                    emvCard.dump(Log.getPrintWriter(), 0);
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
