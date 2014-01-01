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
package sasc;

import sasc.common.SessionProcessingEnv;
import sasc.common.Context;
import java.io.PrintWriter;
import java.io.StringWriter;
import sasc.common.CardSession;
import sasc.iso7816.AID;
import sasc.emv.EMVApplication;
import sasc.emv.CA;
import sasc.common.SmartCard;
import sasc.emv.*;
import sasc.iso7816.SmartCardException;
import sasc.util.Log;
import sasc.lookup.ATR_DB;
import sasc.lookup.IIN_DB;
import sasc.lookup.RID_DB;
import sasc.terminal.CardConnection;
import sasc.terminal.TerminalException;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class CardEmulatorMain {

    public static void main(String[] args) throws TerminalException {
        SmartCard smartCard = null;
        try {
            Context.init();
            CA.initFromFile("/certificationauthorities_test.xml");
            CardConnection conn = new CardEmulator("/sdacardtransaction.xml");
            CardSession cardSession = CardSession.createSession(conn, new SessionProcessingEnv());
            smartCard = cardSession.initCard();
            EMVSession session = EMVSession.startSession(smartCard, conn);

            AID targetAID = new AID("a1 23 45 67 89 10 10"); //Our TEST AID

            session.initContext();
            for (EMVApplication app : smartCard.getApplications()) {
                session.selectApplication(app);
                session.initiateApplicationProcessing(); //Also reads application data

                if (!app.isInitializedOnICC()) {
                    //Skip if GPO failed
                    continue;
                }
                
                //Internal auth here
                
                session.readAdditionalData();
                if (targetAID.equals(app.getAID())) {
                    session.verifyPIN(new char[]{'1','2','3','4'}, true);
                }
//            session.getChallenge(app);
            }
            Log.info("Finished Processing card.");
            Log.info("Now dumping card data in a more readable form:");
            Log.info("\n");
        } catch (TerminalException ex) {
            throw ex;
        } catch (SmartCardException ex) {
            throw ex;
        } finally {
            if (smartCard != null) {
                StringWriter dumpWriter = new StringWriter();
                PrintWriter pw = new PrintWriter(dumpWriter);
                pw.println("======================================");
                pw.println("             [Smart Card]             ");
                pw.println("======================================");
                smartCard.dump(new PrintWriter(dumpWriter), 0);
                pw.println("---------------------------------------");
                pw.println("                FINISHED               ");
                pw.println("---------------------------------------");
                pw.flush();
                System.out.println(dumpWriter.toString());
            }
        }
    }
}
