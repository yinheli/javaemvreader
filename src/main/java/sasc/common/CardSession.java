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

import sasc.emv.EMVAPDUCommands;
import sasc.emv.EMVUtil;
import sasc.iso7816.SmartCardException;
import sasc.terminal.CardConnection;
import sasc.terminal.CardResponse;
import sasc.terminal.TerminalException;
import sasc.util.Log;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class CardSession {
    
    private boolean cardInitialized = false;
    private SmartCard card;
    private CardConnection terminal;
    private SessionProcessingEnv sessionEnv;
    
    private CardSession(CardConnection terminal, SessionProcessingEnv sessionEnv){
        this.terminal = terminal;
        this.sessionEnv = sessionEnv;
    }
    
    public static CardSession createSession(CardConnection terminal, SessionProcessingEnv sessionEnv) {
        return new CardSession(terminal, sessionEnv);
    }
    
    public SmartCard initCard() throws TerminalException {

        if (cardInitialized) {
            throw new SmartCardException("Card already initalized. Create new Session to init new card.");
        }

        card = new SmartCard(new sasc.iso7816.ATR(terminal.getATR()));
        Log.debug("terminal: " + terminal);
        Log.debug("ATR: " + Util.prettyPrintHexNoWrap(terminal.getATR()));

        String command;
        int SW1;
        int SW2;

        try {
            Thread.sleep(sessionEnv.getInitialPauseMillis());
        } catch (InterruptedException ex) {
            ex.printStackTrace(System.err);
            Thread.currentThread().interrupt();
            throw new TerminalException(ex);
        }

        //TODO special access data in ATR?? 
        //Example of how a PC/SC compliant IFD announces that it translates communication to a memory card
        //Samsung Galaxy S3 Secure Element:
        //4F = AID (or possibly just RID of authority for the data??)
        //0C = Length of AID
        //A0 00 00 03 06 = RID of PC/SC
        //00 = No information given
        //00 00 = Name (registered ID in PC/SC doc)
        //00 00 00 00 = RFU
        //http://smartcard-atr.appspot.com/parse?ATR=3b8f8001804f0ca0000003060000000000000068
        //See isotype.py
        //And http://www.pcscworkgroup.com/specifications/files/pcsc3_v2.01.08_sup.pdf

        if (sessionEnv.getWarmUpCard()) {
            //Some cards/readers seem to misbehave on the first command 
            //(maybe due to premature activation after insertion?)
            //eg if the first command is "select PSE", then the card might
            //return 6985 (Command not allowed; conditions of use not satisfied)
            //We try to work around this by sending a "warm up" command to the card

            Log.commandHeader("SELECT FILE NONEXISTINGFILE to warm up the card/terminal/connection");

            command = EMVAPDUCommands.selectByDFName(Util.fromHexString("4E 4F 4E 45 58 49 53 54 49 4E 47 46 49 4C 45"));

            CardResponse selectNEFResponse = EMVUtil.sendCmd(terminal, command);

            SW1 = (byte) selectNEFResponse.getSW1();
            SW2 = (byte) selectNEFResponse.getSW2();

            //NO-OP
        }
        
        
        CardScanner scanner = new CardScanner(card, terminal, sessionEnv);
        scanner.start();

        return card;
    }
}
