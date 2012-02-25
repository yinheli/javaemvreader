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

import sasc.iso7816.SmartCardException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import sasc.util.Util;

/**
 * Track 2 Equivalent Data
 * A representation of the data that can be found on Track 2 on magnetic stripe cards
 * Contains the data elements of track 2 according to ISO/IEC 7813,
 * excluding start sentinel, end sentinel, and Longitudinal Redundancy Check (LRC)
 * 
 * @author sasc
 */
public class Track2EquivalentData {

    public static class ServiceCode {

        private char[] serviceCode;

        private ServiceCode(char[] serviceCode) {
            if (serviceCode.length != 3) {
                throw new IllegalArgumentException("ServiceCode must have 3 digits");
            }
            for (int i = 0; i < serviceCode.length; i++) {
                if (!Character.isDigit(serviceCode[i])) {
                    throw new IllegalArgumentException("Only digits allowed in ServiceCode: " + serviceCode[i]);
                }
            }
            this.serviceCode = serviceCode;
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            dump(new PrintWriter(sw), 0);
            return sw.toString();
        }

        public void dump(PrintWriter pw, int indent) {
            pw.println(Util.getSpaces(indent) + "Service Code - "+new String(serviceCode)+":");
            String indentStr = Util.getSpaces(indent + 3);
            pw.println(indentStr + serviceCode[0] + " : Interchange Rule - " + getInterchangeRulesDescription());
            pw.println(indentStr + serviceCode[1] + " : Authorisation Processing - " + getAuthorisationProcessingDescription());
            pw.println(indentStr + serviceCode[2] + " : Range of Services - " + getRangeOfServicesDescription());
        }
        
        
        //Service code values common in financial cards:
        //The first digit specifies the interchange rules, the second specifies authorisation processing and the third specifies the range of services
        
        public String getInterchangeRulesDescription(){
            switch(serviceCode[0]){
                case '1':
                    return "International interchange OK";
                case '2':
                    return "International interchange, use IC (chip) where feasible";
                case '5': 
                    return "National interchange only except under bilateral agreement";
                case '6': 
                    return "National interchange only except under bilateral agreement, use IC (chip) where feasible";
                case '7': 
                    return "No interchange except under bilateral agreement (closed loop)";
                case '9': 
                    return "Test";
                default:
                    return "RFU";
            }
        }
        
        public String getAuthorisationProcessingDescription(){
            switch(serviceCode[1]){
                case '0':
                    return "Normal";
                case '2': 
                    return "Contact issuer via online means";
                case '4': 
                    return "Contact issuer via online means except under bilateral agreement";
                default:
                    return "RFU";
            }
        }
        
        public String getRangeOfServicesDescription(){
            switch(serviceCode[2]){
                case '0': 
                    return "No restrictions, PIN required";
                case '1': 
                    return "No restrictions"; 
                case '2': 
                    return "Goods and services only (no cash)";
                case '3': 
                    return "ATM only, PIN required";
                case '4': 
                    return "Cash only";
                case '5': 
                    return "Goods and services only (no cash), PIN required";
                case '6': 
                    return "No restrictions, use PIN where feasible";
                case '7': 
                    return "Goods and services only (no cash), use PIN where feasible";
                default:
                    return "RFU";
            }
        }
    }
    
    private PAN pan;
    private Date expirationDate; //numeric 4
    private ServiceCode serviceCode;
    private String discretionaryData; //(defined by individual payment systems)

    public Track2EquivalentData(byte[] data) {
        if (data.length > 19) {
            throw new SmartCardException("Invalid Track2EquivalentData length: " + data.length);
        }
        String str = Util.byteArrayToHexString(data).toUpperCase();
        //Field Separator (Hex 'D')
        int fieldSepIndex = str.indexOf('D');
        pan = new PAN(str.substring(0, fieldSepIndex));
        //Skip Field Separator
        int YY = Util.numericHexToInt(str.substring(fieldSepIndex + 1, fieldSepIndex + 3));
        int MM = Util.numericHexToInt(str.substring(fieldSepIndex + 3, fieldSepIndex + 5));
        Calendar cal = Calendar.getInstance();
        cal.set(2000 + YY, MM - 1, 0, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        this.expirationDate = cal.getTime();
        serviceCode = new ServiceCode(str.substring(fieldSepIndex + 5, fieldSepIndex + 8).toCharArray());
        int padIndex = str.indexOf('F', fieldSepIndex + 8);
        if (padIndex != -1) {
            //Padded with one Hex 'F' if needed to ensure whole bytes
            discretionaryData = str.substring(fieldSepIndex + 8, padIndex);
        } else {
            discretionaryData = str.substring(fieldSepIndex + 8);
        }
    }

    public Date getExpirationDate() {
        return (Date) expirationDate.clone();
    }

    public ServiceCode getServiceCode() {
        return serviceCode;
    }
    
    public PAN getPAN() {
        return pan;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getSpaces(indent) + "Track 2 Equivalent Data:");
        String indentStr = Util.getSpaces(indent + 3);
        pan.dump(pw, indent + 3);
        pw.println(indentStr + "Expiration Date: " + expirationDate);
        serviceCode.dump(pw, indent+3);
        pw.println(indentStr + "Discretionary Data: " + discretionaryData +" (may include Pin Verification Key Indicator (PVKI, 1 character), PIN Verification Value (PVV, 4 characters), Card Verification Value or Card Verification Code (CVV or CVC, 3 characters))");
    }

    public static void main(String[] args) {
        Track2EquivalentData t2 = new Track2EquivalentData(Util.fromHexString("957852641234567890d120360112345678900f"));
        System.out.println(t2);
    }
}
