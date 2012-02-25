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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import sasc.terminal.CardConnection;
import sasc.terminal.Terminal;
import sasc.terminal.TerminalException;
import sasc.terminal.TerminalProvider;

/**
 *
 * WARNING: This class must not be instantiated, referenced or loaded in any way
 * except via reflection.
 *
 * @author sasc
 */
public class SmartcardioTerminalProviderImpl implements TerminalProvider {

    private String providerInfo = "";
    CardTerminals terminals = null;

    SmartcardioTerminalProviderImpl() {
        TerminalFactory factory = TerminalFactory.getDefault();
        providerInfo = "SmartcardIO[" + factory.getProvider() + "]";
        terminals = factory.terminals();
    }

    @Override
    public List<Terminal> listTerminals() throws TerminalException {
        List<Terminal> list = new ArrayList<Terminal>();
        try {
            for (CardTerminal terminal : terminals.list()) {
                list.add(new TerminalImpl(terminal));
            }
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public CardConnection connectAnyTerminal() throws TerminalException {
        try {
//            //First check if a card is present in any terminal
//            for (CardTerminal terminal : terminals.list(javax.smartcardio.CardTerminals.State.CARD_PRESENT)) {
//                Card _card = terminal.connect("*");
//                return new SmartcardioCardConnection(_card);
//            }
            //Do not use State.CARD_PRESENT. Some systems might have a card present at all times in a specific termial 
            //(for example a 3G mobile card with SIM slot that is listed as a PC/SC reader on the host system)
            //wait for a card to be inserted
            while (true) {
                terminals.waitForChange();
                for (CardTerminal smartCardIOTerminal : terminals.list(javax.smartcardio.CardTerminals.State.CARD_INSERTION)) {
                    Card _card = smartCardIOTerminal.connect("*");
                    return new SmartcardioCardConnection(_card, smartCardIOTerminal);
                }
            }
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }
    }

    @Override
    public CardConnection connectTerminal(String name) throws TerminalException {
        try {
            CardTerminal smartCardIOTerminal = terminals.getTerminal(name);
            Card _card = smartCardIOTerminal.connect("*");
            return new SmartcardioCardConnection(_card, smartCardIOTerminal);
        } catch (CardException ex) {
            throw new TerminalException(ex);
        } 
    }

    @Override
    public CardConnection connectTerminal(int index) throws TerminalException {
        try {
            CardTerminal smartCardIOTerminal = terminals.list().get(index);
            Card _card = smartCardIOTerminal.connect("*");
            return new SmartcardioCardConnection(_card, smartCardIOTerminal);
        } catch (CardException ex) {
            throw new TerminalException(ex);
        } catch (IndexOutOfBoundsException ex){
            throw new TerminalException(ex);
        }
    }

    @Override
    public String getProviderInfo() {
        return providerInfo;
    }

    private class TerminalImpl implements Terminal {

        CardTerminal smartCardIOTerminal;
        Card card = null;

        public TerminalImpl(CardTerminal smartCardIOTerminal) {
            this.smartCardIOTerminal = smartCardIOTerminal;
        }

        @Override
        public CardConnection connect() throws TerminalException {
            try {
                card = smartCardIOTerminal.connect("*");
                return new SmartcardioCardConnection(card, smartCardIOTerminal);
            } catch (CardException ex) {
                throw new TerminalException(ex);
            }
        }

        @Override
        public String getTerminalInfo() {
            String cardPresent = null;
            try{
                cardPresent = smartCardIOTerminal.isCardPresent()?"Card Present":"No card present";
            }catch(CardException ex){
                //Ignore
            }
            return "Name: "+smartCardIOTerminal.getName() + " (Description: "+smartCardIOTerminal.toString()+") "+cardPresent;
        }
    }
}
