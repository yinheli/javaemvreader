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
package sasc.terminal.smartcardio;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import sasc.terminal.CardResponse;
import sasc.terminal.Terminal;
import sasc.terminal.TerminalException;
import sasc.terminal.CardConnection;
import sasc.util.Util;

/**
 * Any handling of procedure bytes and GET REPONSE/GET DATA in javax.smartcardio should be disabled, because of improper handling of the CLS byte in some cases.
 * 
 * Thus, the "procedure byte handling" of the the TAL (Terminal Abstraction Layer), is moved to a higher layer.
 * 
 * @author sasc
 */
public class SmartcardioCardConnection implements CardConnection {

    private Card card;
    private CardTerminal smartCardIOTerminal;
    private CardChannel channel;

    public SmartcardioCardConnection(Card card, CardTerminal smartCardIOTerminal) {
        this.card = card;
        this.smartCardIOTerminal = smartCardIOTerminal;
        channel = card.getBasicChannel();
    }

    @Override
    public CardResponse transmit(byte[] cmd) throws TerminalException {
        CardResponseImpl response = null;
        try {
            ResponseAPDU apdu = channel.transmit(new CommandAPDU(cmd));
            byte sw1 = (byte) apdu.getSW1();
            byte sw2 = (byte) apdu.getSW2();
            byte[] data = apdu.getData(); //Copy
            response = new CardResponseImpl(data, sw1, sw2, (short) apdu.getSW());
        } catch (CardException ce) {
            throw new TerminalException("Error occured while transmitting command: " + Util.byteArrayToHexString(cmd), ce);
        }
        return response;
    }

    @Override
    public byte[] getATR() {
        return this.card.getATR().getBytes();
    }

    @Override
    public String toString() {
        return getConnectionInfo();
    }

    @Override
    public Terminal getTerminal() {
        throw new UnsupportedOperationException("Not implemented yet");
//        return new SmartCardIOTerminal(smartCardIOTerminal);
    }

    @Override
    public String getConnectionInfo() {
        return channel.toString();
    }
    
    @Override
    public String getProtocol(){
        return this.card.getProtocol();
    }

    @Override
    public boolean disconnect(boolean attemptReset) throws TerminalException {
        try {
            card.disconnect(attemptReset);
            return true;
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }
    }

    /**
     * Perform warm reset
     * 
     */
    @Override
    public void resetCard() throws TerminalException {
        try {
            //From scuba:
            // WARNING: Woj: the meaning of the reset flag is actually
            // reversed w.r.t. to the official documentation, false means
            // that the card is going to be reset, true means do not reset
            // This is a bug in the smartcardio implementation from SUN
            // Moreover, Linux PCSC implementation goes numb if you try to
            // disconnect a card that is not there anymore.

            // From sun/security/smartcardio/CardImpl.java:
            // SCardDisconnect(cardId, (reset ? SCARD_LEAVE_CARD : SCARD_RESET_CARD));
            // (http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/sun/security/smartcardio/CardImpl.java?av=f)
            // The BUG: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7047033
            if (smartCardIOTerminal.isCardPresent()) {
                card.disconnect(false);
            }
            card = smartCardIOTerminal.connect("*");
            channel = card.getBasicChannel();
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }
    }

    private class CardResponseImpl implements CardResponse {

        private byte[] data;
        private byte sw1;
        private byte sw2;
        private short sw;

        CardResponseImpl(byte[] data, byte sw1, byte sw2, short sw) {
            this.data = data;
            this.sw1 = sw1;
            this.sw2 = sw2;
            this.sw = sw;
        }

        @Override
        public byte[] getData() {
            return data;
        }

        @Override
        public byte getSW1() {
            return sw1;
        }

        @Override
        public byte getSW2() {
            return sw2;
        }

        @Override
        public short getSW() {
            return sw;
        }
    }
}
