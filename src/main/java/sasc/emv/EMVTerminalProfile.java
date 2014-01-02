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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import sasc.iso7816.Tag;
import sasc.iso7816.TagAndLength;
import sasc.iso7816.TagImpl;
import sasc.iso7816.TagValueType;
import sasc.util.ISO4217_Numeric;
import sasc.util.Log;
import sasc.util.Util;

/**
 * 
 * @author sasc
 */
public class EMVTerminalProfile {

    private final static Properties defaultTerminalProperties = new Properties();
    private final static Properties runtimeTerminalProperties = new Properties();
    private final static TerminalVerificationResults terminalVerificationResults = new TerminalVerificationResults();

    static {
        
        try {
            //Default properties
            defaultTerminalProperties.load(EMVTerminalProfile.class.getResourceAsStream("/terminal.properties"));
            for (String key : defaultTerminalProperties.stringPropertyNames()) {
                //Sanitize
                String sanitizedKey = Util.byteArrayToHexString(Util.fromHexString(key)).toLowerCase();
                String sanitizedValue = Util.byteArrayToHexString(Util.fromHexString(key)).toLowerCase();
                defaultTerminalProperties.setProperty(sanitizedKey, sanitizedValue);
            }
            //Overridden properties
            String runtimeTerminalPropertiesFile = System.getProperty("terminal.properties");
            if (runtimeTerminalPropertiesFile != null) {
                runtimeTerminalProperties.load(new FileInputStream(runtimeTerminalPropertiesFile));
                for(String key : runtimeTerminalProperties.stringPropertyNames()) {
                    //Sanitize
                    String sanitizedKey   = Util.byteArrayToHexString(Util.fromHexString(key)).toLowerCase();
                    String sanitizedValue = Util.byteArrayToHexString(Util.fromHexString(key)).toLowerCase();
                    if(defaultTerminalProperties.contains(sanitizedKey) && sanitizedValue.length() != defaultTerminalProperties.getProperty(key).length()) {
                        //Attempt to set different length for a default value
                        throw new RuntimeException("Attempted to set a value with unsupported length for key: "+sanitizedKey + " (value: "+sanitizedValue+")");
                    }
                    runtimeTerminalProperties.setProperty(sanitizedKey, sanitizedValue);
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

    }

    //PDOL (Processing options Data Object List)
    //DDOL (*Default* Dynamic Data Authentication Data Object List)
    //     (Default to be used for constructing the INTERNAL AUTHENTICATE command if the DDOL in the card is not present)
    //TDOL (*Default* Transaction Certificate Data Object List)
    //     (Default to be used for generating the TC Hash Value if the TDOL in the card is not present)
    public static byte[] getTerminalResidentData(TagAndLength tal, EMVApplication app) {
        //Check if the value is specified in the runtime properties file
        String propertyValueStr = runtimeTerminalProperties.getProperty(Util.byteArrayToHexString(tal.getTag().getTagBytes()).toLowerCase());

        if(propertyValueStr != null) {
            byte[] propertyValue = Util.fromHexString(propertyValueStr);

            if (propertyValue.length == tal.getLength()) {
                return propertyValue;
            }
        }
        
        if (tal.getTag().equals(EMVTags.TERMINAL_COUNTRY_CODE) && tal.getLength() == 2) {
            return findCountryCode(app);
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_CURRENCY_CODE) && tal.getLength() == 2) {
            return findCurrencyCode(app);
        }
        
        //Now check for default values
        propertyValueStr = defaultTerminalProperties.getProperty(Util.byteArrayToHexString(tal.getTag().getTagBytes()).toLowerCase());

        if(propertyValueStr != null) {
            byte[] propertyValue = Util.fromHexString(propertyValueStr);

            if (propertyValue.length == tal.getLength()) {
                return propertyValue;
            }
        }

        if (tal.getTag().equals(EMVTags.UNPREDICTABLE_NUMBER)) {
            return Util.generateRandomBytes(tal.getLength());
        } else if (tal.getTag().equals(EMVTags.TERMINAL_TRANSACTION_QUALIFIERS) && tal.getLength() == 4) {
            TerminalTransactionQualifiers ttq = new TerminalTransactionQualifiers();
            ttq.setContactlessEMVmodeSupported(true);
            ttq.setReaderIsOfflineOnly(true);
            return ttq.getBytes();
        } else if (tal.getTag().equals(EMVTags.TERMINAL_VERIFICATION_RESULTS) && tal.getLength() == 5) {
            //All bits set to '0'
            return terminalVerificationResults.toByteArray();
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_DATE) && tal.getLength() == 3) {
            return Util.getCurrentDateAsNumericEncodedByteArray();
        } else if (tal.getTag().equals(EMVTags.TRANSACTION_TYPE) && tal.getLength() == 1) {
            //transactionTypes = {     0:  "Payment",     1:  "Withdrawal", } 
            //http://www.codeproject.com/Articles/100084/Introduction-to-ISO-8583
            return new byte[]{0x00};
        } else {
            Log.debug("Terminal Resident Data not found for " + tal);
        }
        byte[] defaultResponse = new byte[tal.getLength()];
        Arrays.fill(defaultResponse, (byte) 0x00);
        return defaultResponse;
    }

    public static TerminalVerificationResults getTerminalVerificationResults() {
        return terminalVerificationResults;
    }
    
    public static void setProperty(String tagHex, String valueHex) {
        setProperty(new TagImpl(tagHex, TagValueType.BINARY, "", ""), Util.fromHexString(valueHex));
    }
    
    public static void setProperty(Tag tag, byte[] value){
        runtimeTerminalProperties.setProperty(Util.byteArrayToHexString(tag.getTagBytes()).toLowerCase(Locale.US), Util.byteArrayToHexString(value));
    }

    //TODO move somewhere else
    public static byte[] constructDOLResponse(DOL dol, EMVApplication app) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (TagAndLength tagAndLength : dol.getTagAndLengthList()) {
            byte[] data = getTerminalResidentData(tagAndLength, app);
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

    //Ex Banco BRADESCO (f0 00 00 00 03 00 01) failes GPO with wrong COUNTRY_CODE !
    private static byte[] findCountryCode(EMVApplication app) {
        if(app != null){
            if(app.getIssuerCountryCode() != -1){
                byte[] countryCode = Util.intToBinaryEncodedDecimalByteArray(app.getIssuerCountryCode());
                return Util.resizeArray(countryCode, 2);
            }
        }

        Log.debug("Not able to map any find any Terminal Country Code. Using default");

        String countryCode = defaultTerminalProperties.getProperty(Util.byteArrayToHexString(EMVTags.TERMINAL_COUNTRY_CODE.getTagBytes()));
        if(countryCode != null){
            return Util.fromHexString(countryCode);
        }
        
        return new byte[]{0x08, 0x26};
    }
    
    private static byte[] findCurrencyCode(EMVApplication app){
        if(app != null){
            if(app.getApplicationCurrencyCode() != -1){
                byte[] currencyCode = Util.intToBinaryEncodedDecimalByteArray(app.getApplicationCurrencyCode());
                return Util.resizeArray(currencyCode, 2);
            }
            Locale preferredLocale = null;
            if(app.getLanguagePreference() != null){
                preferredLocale = app.getLanguagePreference().getPreferredLocale();
            }
            if(preferredLocale == null 
                    && app.getCard() != null 
                    && app.getCard().getPSE() != null
                    && app.getCard().getPSE().getLanguagePreference() != null){
                preferredLocale = app.getCard().getPSE().getLanguagePreference().getPreferredLocale();
            }
            if(preferredLocale != null){
                if(preferredLocale.getLanguage().equals(Locale.getDefault().getLanguage())) {
                    //Guesstimate; we presume default locale is the preferred
                    preferredLocale = Locale.getDefault();
                }
                List<Integer> numericCodes = ISO4217_Numeric.getNumericCodeForLocale(preferredLocale);
                if (numericCodes != null && numericCodes.size() > 0) {
                    //Just use the first found. It might not be correct, eg Brazil (BRZ) vs Portugal (EUR)
                    return Util.resizeArray(Util.intToBinaryEncodedDecimalByteArray(numericCodes.get(0)), 2); 
                }
            }
            
        }
        String currencyCode = defaultTerminalProperties.getProperty(Util.byteArrayToHexString(EMVTags.TRANSACTION_CURRENCY_CODE.getTagBytes()));
        if(currencyCode != null){
            return Util.fromHexString(currencyCode);
        }
        return new byte[]{0x08, 0x26};
    }

    public static void main(String[] args) {
        {
            TagAndLength tagAndLength = new TagAndLength(EMVTags.TERMINAL_COUNTRY_CODE, 2);
            DOL dol = new DOL(DOL.Type.PDOL, tagAndLength.getBytes());
            System.out.println(Util.prettyPrintHexNoWrap(constructDOLResponse(dol, null)));
        }

        {
            //Test country code 076 (Brazil)
            TagAndLength tagAndLength = new TagAndLength(EMVTags.TERMINAL_COUNTRY_CODE, 2);
            DOL dol = new DOL(DOL.Type.PDOL, tagAndLength.getBytes());
            EMVApplication app = new EMVApplication();
            app.setIssuerCountryCode(76); //Brazil
            byte[] dolResponse = constructDOLResponse(dol, app);
            System.out.println(Util.prettyPrintHexNoWrap(dolResponse));
            if (!Arrays.equals(new byte[]{0x00, (byte) 0x76}, dolResponse)) {
                throw new AssertionError("Country code was wrong");
            }
        }

        {
            //Test currency code 986 (Brazilian Real)
            TagAndLength tagAndLength = new TagAndLength(EMVTags.TRANSACTION_CURRENCY_CODE, 2);
            DOL dol = new DOL(DOL.Type.PDOL, tagAndLength.getBytes());
            EMVApplication app = new EMVApplication();
            app.setApplicationCurrencyCode(986);
            byte[] dolResponse = constructDOLResponse(dol, app);
            System.out.println(Util.prettyPrintHexNoWrap(dolResponse));
            if (!Arrays.equals(new byte[]{0x09, (byte) 0x86}, dolResponse)) {
                throw new AssertionError("Currency code was wrong");
            }
        }

        {
            //Test currency code 986 (Brazilian Real) from Locale
            TagAndLength tagAndLength = new TagAndLength(EMVTags.TRANSACTION_CURRENCY_CODE, 2);
            DOL dol = new DOL(DOL.Type.PDOL, tagAndLength.getBytes());
            EMVApplication app = new EMVApplication();
            app.setLanguagePreference(new LanguagePreference(Util.fromHexString("70 74 65 6e 65 73 69 74"))); // (=ptenesit)
            byte[] dolResponse = constructDOLResponse(dol, app);
            System.out.println(Util.prettyPrintHexNoWrap(dolResponse));
//            if(!Arrays.equals(new byte[]{0x09, (byte)0x86}, dolResponse)){
//                throw new AssertionError("Currency code was wrong");
//            }
        }
    }
}
