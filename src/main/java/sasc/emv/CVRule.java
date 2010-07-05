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

import java.io.PrintWriter;
import java.io.StringWriter;
import sasc.util.Util;

/**
 * Describes Cardholder Verification Method (CVM) Codes
 * EMV book 3 page 184
 * 
 * @author sasc
 */
public class CVRule {

    byte firstByte;
    byte secondByte;
    byte[] amountFieldXBytes;
    String amountFieldXStr;
    byte[] secondAmountFieldYBytes;
    String secondAmountFieldYStr;

    //TODO send in Application Currency Code
    public CVRule(byte firstByte, byte secondByte, byte[] amountFieldX, byte[] secondAmountFieldY) {
        this.firstByte = firstByte;
        this.secondByte = secondByte;
        this.amountFieldXBytes = amountFieldX;
        this.amountFieldXStr = CVRule.formatAmountField(amountFieldX);
        this.secondAmountFieldYBytes = secondAmountFieldY;
        this.secondAmountFieldYStr = CVRule.formatAmountField(secondAmountFieldYBytes);
    }

    //Most significant bit (0x80) of the first byte is RFU
    public boolean failCardholderVerificationIfThisCVMIsUnsuccessful() {
        return (firstByte & (byte) 0x40) == 0;
    }

    public boolean applySucceedingCVRuleIfThisCVMIsUnsuccessful() {
        return (firstByte & (byte) 0x40) == (byte) 0x40;
    }

    public boolean failCVMProcessing() {
        return (firstByte & (byte) 0x3F) == 0;
    }

    public boolean plaintextPINVerificationPerformedByICC() {
        return (firstByte & (byte) 0x3F) == (byte) 0x01;
    }

    public boolean encipheredPINVerifiedOnline() {
        return (firstByte & (byte) 0x3F) == (byte) 0x02;
    }

    public boolean plaintextPINVerificationPerformedByICCAndSignature_paper_() {
        return (firstByte & (byte) 0x3F) == (byte) 0x03;
    }

    public boolean encipheredPINVerificationPerformedByICC() {
        return (firstByte & (byte) 0x3F) == (byte) 0x04;
    }

    public boolean encipheredPINVerificationPerformedByICCAndSignature_paper_() {
        return (firstByte & (byte) 0x3F) == (byte) 0x05;
    }

    //Values in the range 000110 (0x06) - 011101 (0x1D) reserved for future use

    public boolean signature_paper_() {
        return (firstByte & (byte) 0x3F) == (byte) 0x1E;
    }

    public boolean noCVMRequired() {
        return (firstByte & (byte) 0x3F) == (byte) 0x1F;
    }

    public String getCVMUnsuccessfulRuleString() {
        if (failCardholderVerificationIfThisCVMIsUnsuccessful()) {
            return "Fail cardholder verification if this CVM is unsuccessful";
        } else {
            return "Apply succeeding CV Rule if this CVM is unsuccessful";
        }
    }

    public String getRuleString(){
        switch (firstByte & (byte)0x3F) {
            case 0x00:
                return "Fail CVM processing";
            case 0x01:
                return "Plaintext PIN verification performed by ICC";
            case 0x02:
                return "Enciphered PIN verified online";
            case 0x03:
                return "Plaintext PIN verification performed by ICC and signature (paper)";
            case 0x04:
                return "Enciphered PIN verification performed by ICC";
            case 0x05:
                return "Enciphered PIN verification performed by ICC and signature (paper)";
            //0x06-0x1D: reserved for future use
            case 0x1E:
                return "If transaction is in the application currency and is under "+amountFieldXStr+" value";
            case 0x1F:
                return "If transaction is in the application currency and is over "+amountFieldXStr+" value";
            //Values in the range 100000 (0x20) - 101111 (0x2F) reserved for use by the individual payment systems
            //Values in the range 110000 (0x30) - 111110 (0x3E) reserved for use by the issuer
            //111111 (0x3F): This value is not available for use
            default:
                if (firstByte <= 0x1D) { //0x06 - 0x1D
                    return "Reserved for future use";
                } else if(firstByte <= 0x2F){ //0x80 - 0xFF
                    return "Reserved for use by individual payment systems";
                } else if(firstByte <= 0x3E){
                    return "Reserved for use by the issuer";
                } else{ //0x3F
                    //This value is not available for use
                    return "";
                }
        }
    }

    private static String formatAmountField(byte[] amount){ //TODO Application Currency Code
        StringBuilder sb = new StringBuilder(String.valueOf(Util.byteArrayToInt(amount)));
        while(sb.length() < 3){
            sb.insert(0, '0');
        }
        sb.insert(sb.length()-2, ".");
        return sb.toString();
    }


    //Second byte (Condition Codes)

    //EMV book3 Table 40 (page 185)

    //CV Rule Byte 2 (Rightmost): Cardholder Verification Method (CVM) Condition Codes
    //Value Meaning
    //'00' Always
    //'01' If unattended cash
    //'02' If not unattended cash and not manual cash and not purchase with cashback
    //'03' If terminal supports the CVM 21
    //'04' If manual cash
    //'05' If purchase with cashback
    //'06' If transaction is in the application currency 22 and is under X value (see section 10.5 for a discussion of ―X)
    //'07' If transaction is in the application currency and is over X value
    //'08' If transaction is in the application currency and is under Y value (see section 10.5 for a discussion of ―Y)
    //'09' If transaction is in the application currency and is over Y value
    //'0A' - '7F' RFU
    //'80' - 'FF' Reserved for use by individual payment systems

    public String getConditionCode() {
        switch (secondByte) {
            case 0x00:
                return "Always";
            case 0x01:
                return "If unattended cash";
            case 0x02:
                return "If not unattended cash and not manual cash and not purchase with cashback";
            case 0x03:
                return "If terminal supports the CVM";
            case 0x04:
                return "If manual cash";
            case 0x05:
                return "If purchase with cashback";
            case 0x06:
                return "If transaction is in the application currency 22 and is under "+amountFieldXStr+" value";
            case 0x07:
                return "If transaction is in the application currency and is over "+amountFieldXStr+" value";
            case 0x08:
                return "If transaction is in the application currency and is under "+secondAmountFieldYStr+" value";
            case 0x09:
                return "If transaction is in the application currency and is over "+secondAmountFieldYStr+" value";
            default:
                if (secondByte <= 0x7F) { //0x0A - 0x7F
                    return "RFU";
                } else { //0x80 - 0xFF
                    return "Reserved for use by individual payment systems";
                }

        }
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getEmptyString(indent) + "Cardholder Verification Rule");
        String indentStr = Util.getEmptyString(indent + 3);

        pw.println(indentStr + "Rule: "+getRuleString());
        pw.println(indentStr + "Condition Code: "+getConditionCode());

        pw.println(indentStr + getCVMUnsuccessfulRuleString());

    }
}
