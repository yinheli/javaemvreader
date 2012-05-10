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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import sasc.iso7816.TagAndLength;
import sasc.util.Log;
import sasc.util.Util;

/**
 * TODO move values to external (properties) file
 * @author sasc
 */
public class EMVTerminalProfile {

    private final static byte[] DEFAULT_TERMINAL_COUNTRY_CODE = new byte[]{(byte) 0x08, (byte) 0x26};
    private final static byte[] DEFAULT_TRANSACTION_CURRENCY_CODE = new byte[]{(byte) 0x08, (byte) 0x26};
    private final static byte[] DEFAULT_AMOUNT_AUTHORISED_NUMERIC = new byte[]{(byte) 0x00, (byte) 0x00,(byte) 0x00, (byte) 0x00,(byte) 0x10, (byte) 0x00};
    private final static byte[] DEFAULT_AMOUNT_OTHER_NUMERIC = new byte[]{(byte) 0x00, (byte) 0x00,(byte) 0x00, (byte) 0x00,(byte) 0x10, (byte) 0x00};
    private final static Properties terminalProperties = new Properties();
    private final static TerminalVerificationResults tvr = new TerminalVerificationResults();

    static {
        String terminalPropertiesFile = System.getProperty("terminal.properties");
        if (terminalPropertiesFile != null) {
            try {
                terminalProperties.load(new FileInputStream(terminalPropertiesFile));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    //TODO
    //Default Dynamic Data Authentication Data Object List (DDOL)
    //DDOL to be used for constructing the INTERNAL AUTHENTICATE command if the DDOL in the card is not present
    //Default Transaction Certificate Data Object List (TDOL)
    //TDOL to be used for generating the TC Hash Value if the TDOL in the card is not present
    public static byte[] getTerminalResidentData(TagAndLength tal) {
        //Is the value specified in an optional properties file?
        String propertyValue = terminalProperties.getProperty(Util.byteArrayToHexString(tal.getTag().getTagBytes()).toLowerCase());
        if (propertyValue != null && propertyValue.length() == tal.getLength()) {
            return Util.fromHexString(propertyValue);
        }
        if (tal.getTag().equals(EMVTags.TERMINAL_COUNTRY_CODE) && tal.getLength() == 2) {
            return DEFAULT_TERMINAL_COUNTRY_CODE;
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_CURRENCY_CODE) && tal.getLength() == 2) {
            return DEFAULT_TRANSACTION_CURRENCY_CODE;
        } else if (tal.getTag().equals(EMVTags.UNPREDICTABLE_NUMBER)) {
            return Util.generateRandomBytes(tal.getLength());
        } else if (tal.getTag().equals(EMVTags.TERMINAL_TRANSACTION_QUALIFIERS) && tal.getLength() == 4) {
            TerminalTransactionQualifiers ttq = new TerminalTransactionQualifiers();
            ttq.setContactlessEMVmodeSupported(true);
            ttq.setReaderIsOfflineOnly(true);
            return ttq.getBytes();
        } else if (tal.getTag().equals(EMVTags.AMOUNT_AUTHORISED_NUMERIC) && tal.getLength() == 6) {
            return DEFAULT_AMOUNT_AUTHORISED_NUMERIC;
        } else if (tal.getTag().equals(EMVTags.AMOUNT_OTHER_NUMERIC) && tal.getLength() == 6) {
            return DEFAULT_AMOUNT_OTHER_NUMERIC;
        } else if (tal.getTag().equals(EMVTags.TERMINAL_VERIFICATION_RESULTS) && tal.getLength() == 5) {
            //All bits set to '0'
            return tvr.toByteArray();
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_DATE) && tal.getLength() == 3) {
            return Util.getCurrentDateAsNumericEncodedByteArray();
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_TYPE) && tal.getLength() == 1) {
            //transactionTypes = {     0:  "Payment",     1:  "Withdrawal", } 
            //TODO not supported at the moment
            //http://www.codeproject.com/Articles/100084/Introduction-to-ISO-8583
            return new byte[tal.getLength()];
        } else {
            Log.debug("Terminal Resident Data not found for " + tal);
        }
        byte[] defaultResponse = new byte[tal.getLength()];
        Arrays.fill(defaultResponse, (byte) 0x00);
        return defaultResponse;
    }

    public static TerminalVerificationResults getTerminalVerificationResults() {
        return tvr;
    }

    //TODO move somewhere else
    public static byte[] constructDOLResponse(DOL dol) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (TagAndLength tagAndLength : dol.getTagAndLengthList()) {
            byte[] data = getTerminalResidentData(tagAndLength);
            stream.write(data, 0, data.length);
        }
        return stream.toByteArray();
    }

    //The ICC may contain the DDOL, but there shall be a default DDOL in the terminal, 
    //specified by the payment system, for use in case the DDOL is not present in the ICC.
    public static byte[] getDefaultDDOLResponse() {
        //It is mandatory that the DDOL contains the Unpredictable Number generated by the terminal (tag '9F37', 4 bytes binary).
        byte[] unpredictableNumber = Util.generateRandomBytes(4);
        //TODO add other DDOL data?
        return unpredictableNumber;
    }

    public static void main(String[] args) {
        TagAndLength tagAndLength = new TagAndLength(EMVTags.TERMINAL_COUNTRY_CODE, 2);
        DOL dol = new DOL(DOL.Type.PDOL, tagAndLength.getBytes());
        System.out.println(Util.prettyPrintHexNoWrap(constructDOLResponse(dol)));
    }
}
