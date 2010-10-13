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
package sasc.terminal;

import java.util.LinkedHashMap;
import java.util.Map;
import sasc.emv.*;

/**
 * Part of list maintained by "Terminal"
 * TODO split into KnownAIDs and SupportedAIDs?
 * 
 * @author sasc
 */
public class SupportedAID {
    private ApplicationSelectionIndicator asi;
    private AID aid;
    private String name;
    private String description;
    private static final Map<AID, SupportedAID> supportedAIDsMap = new LinkedHashMap<AID, SupportedAID>();

    private SupportedAID(String name, AID aid, ApplicationSelectionIndicator asi, String description){
        this.name = name;
        this.aid = aid;
        this.asi = asi;
        this.description = description;
    }


    /*
     * For an application in the ICC to be supported by an application in the terminal,
     * the Application Selection Indicator indicates whether the associated AID
     * in the terminal must match the AID in the card exactly, including the length of the AID,
     * or only up to the length of the AID in the terminal.
     *
     * There is only one Application Selection Indicator per AID supported by the terminal
     *
     * Format: At the discretion of the terminal. The data is not sent across the interface
     */
    public static enum ApplicationSelectionIndicator{
        EXACT_MATCH, PARTIAL_MATCH;
    }

    public AID getAID(){
        return aid;
    }

    public boolean partialMatchAllowed(){
        return ApplicationSelectionIndicator.PARTIAL_MATCH.equals(asi);
    }

    public boolean isSupported(AID terminalAID, ApplicationSelectionIndicator asi){
        switch(asi){
            case EXACT_MATCH:
                return supportedAIDsMap.containsKey(terminalAID);
            case PARTIAL_MATCH:

        }
        return false;
    }

    static{
        //TODO use RID as key?
        supportedAIDsMap.put(new AID("a0 00 00 00 03"), new SupportedAID("VISA", null, ApplicationSelectionIndicator.PARTIAL_MATCH, ""));

//        supportedAIDsList.add(new SupportedAID("VISA",                       new AID("a0 00 00 00 03"),          ApplicationSelectionIndicator.PARTIAL_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Debit/Credit (Classic)",     new AID("a0 00 00 00 03 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "Standard VISA credit card"));
//        supportedAIDsList.add(new SupportedAID("VISA Credit",                     new AID("a0 00 00 00 03 10 10 01"), ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Debit",                      new AID("a0 00 00 00 03 10 10 02"), ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Electron",                   new AID("a0 00 00 00 03 20 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "VISA Electron (Debit)"));
//        supportedAIDsList.add(new SupportedAID("VISA Interlink",                  new AID("a0 00 00 00 03 30 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Specific",                   new AID("a0 00 00 00 03 40 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Specific",                   new AID("a0 00 00 00 03 50 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA Plus",                       new AID("a0 00 00 00 03 80 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("VISA ATM",                        new AID("a0 00 00 00 03 99 99 10"), ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("MasterCard Credit",               new AID("a0 00 00 00 04 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "Standard MasterCard"));
//        supportedAIDsList.add(new SupportedAID("MasterCard Specific",             new AID("a0 00 00 00 04 20 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("MasterCard Specific",             new AID("a0 00 00 00 04 30 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Maestro (Debit)",                 new AID("a0 00 00 00 04 30 60"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("MasterCard Specific",             new AID("a0 00 00 00 04 40 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("MasterCard Specific",             new AID("a0 00 00 00 04 50 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Cirrus",                          new AID("a0 00 00 00 04 60 00"),    ApplicationSelectionIndicator.EXACT_MATCH, "MasterCard Cirrus"));
//        supportedAIDsList.add(new SupportedAID("Maestro UK",                      new AID("a0 00 00 00 05 00 01"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Maestro TEST",                    new AID("b0 12 34 56 78"),          ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Self Service",                    new AID("a0 00 00 00 24 01"),       ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("American Express",                new AID("a0 00 00 00 25"),          ApplicationSelectionIndicator.PARTIAL_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("American Express",                new AID("a0 00 00 00 25 00 00"),    ApplicationSelectionIndicator.EXACT_MATCH, "American Express (Credit/Debit)"));
//        supportedAIDsList.add(new SupportedAID("ExpressPay",                      new AID("a0 00 00 00 25 01 07 01"), ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Link",                            new AID("a0 00 00 00 29 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Alias AID",                       new AID("a0 00 00 00 29 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("French CB ?",                     new AID("a0 00 00 00 42 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("?",                               new AID("a0 00 00 00 42 20 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("?",                               new AID("a0 00 00 00 42 30 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("?",                               new AID("a0 00 00 00 42 40 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("?",                               new AID("a0 00 00 00 42 50 10"),    ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Monéo",                           new AID("a0 00 00 00 69 00"),       ApplicationSelectionIndicator.EXACT_MATCH, ""));
//        supportedAIDsList.add(new SupportedAID("Pagobancomat",                    new AID("a0 00 00 01 41 00 01"),    ApplicationSelectionIndicator.EXACT_MATCH, "Italian Domestic debit card"));
//        supportedAIDsList.add(new SupportedAID("SAMA",                            new AID("a0 00 00 02 28 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "Saudi Arabia domestic credit/debit card (Saudi Arabia Monetary Agency)"));
//        supportedAIDsList.add(new SupportedAID("INTERAC",                         new AID("a0 00 00 02 77 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "Canadian domestic credit/debit card"));
//        supportedAIDsList.add(new SupportedAID("BankAxept",                       new AID("d5 78 00 00 02 10 10"),    ApplicationSelectionIndicator.EXACT_MATCH, "Norwegian domestic debit card"));

        //TODO
//A0000000031010 VISA Credit Standard VISA credit card
//A0000000032010 VISA Electron VISA Electron (Debit)
//A0000000033010 VISA Interlink VISA Interlink
//A0000000034010 Visa Specific Visa Specific
//A0000000035010 Visa Specific Visa Specific
//A0000000038010 VISA plus VISA plus
//A0000000041010 MasterCard Credit Standard MasterCard
//A0000000042010 MasterCard Specific MasterCard Specific
//A0000000043010 MasterCard Specific MasterCard Specific
//A0000000043060 Maestro (Debit) Maestro (Debit) Card
//A0000000044010 MasterCard Specific MasterCard Specific
//A0000000045010 MasterCard Specific MasterCard Specific
//A0000000046000 Cirrus Mastercard Cirrus
//A0000000250000 America Express American Express credit/debit
//A0000001410001 Pagobancomat Italian domestic debit card
//A0000002281010 SAMA Saudi Arabia domestic credit/debit card (Saudi Arabia Monetary Agency)
//A0000002771010 INTERAC Canadian domestic credit/debit card

//a0 00 00 03 15 10 10 05 28                    PIN
//a0 00 00 03 15 60 20                          Chipknip
//a0 00 00 00 04 80 02                          SecureCode Auth

    }

}
