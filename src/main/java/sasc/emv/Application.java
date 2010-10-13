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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import sasc.util.ISO3166_1;
import sasc.util.ISO4217_Numeric;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class Application implements File {

    private boolean isInitializedOnICC = false;
    private ApplicationUsageControl auc = null;
    private ApplicationInterchangeProfile aip = null;
    private ApplicationPriorityIndicator api = null;
    private ApplicationFileLocator afl = new ApplicationFileLocator(new byte[]{}); //Default
    private String label = "";
    private String preferredName = "";
    private AID aid = null;
    private IssuerPublicKeyCertificate issuerCert = null;
    private ICCPublicKeyCertificate iccCert = null;
    private int applicationCurrencyCode = -1;
    private int applicationCurrencyExponent = -1;
    private int issuerCountryCode = -1;
    private int applicationTransactionCounter = -1;
    private int lastOnlineATCRegister = -1;
    private int pinTryCounter = -1;
    private LogFormat logFormat = null;
    private DOL pdol = null;
    private DOL ddol = null;
    private Date applicationExpirationDate = null;
    private Date applicationEffectiveDate = null;
    private int applicationVersionNumber = -1;
    private String cardholderName = null;
    private DOL cdol1 = null;
    private DOL cdol2 = null;
    private SignedStaticApplicationData signedStaticAppData = null;
    private PAN pan = null;
    private int panSequenceNumber = -1; //Identifies and differentiates cards with the same PAN
    private CVMList cvmList = null;
    private StaticDataAuthenticationTagList staticDataAuthTagList = null;
    private byte[] track1DiscretionaryData = null; //Optional data encoded by the issuer.
    private byte[] track2DiscretionaryData = null; //Optional data encoded by the issuer.
    private Track2EquivalentData track2EquivalentData = null;
    private int serviceCode = -1;
    private LanguagePreference languagePreference = null;
    private int issuerCodeTableIndex = -1;
    private byte[] issuerActionCodeDefault = null;
    private byte[] issuerActionCodeDenial = null;
    private byte[] issuerActionCodeOnline = null;
    //Transaction related data elements
    private TransactionStatusInformation transactionStatusInformation = new TransactionStatusInformation();
    private List<BERTLV> unhandledRecords = new ArrayList<BERTLV>();

    public Application() {
    }

    public void setAID(AID aid) {
        this.aid = aid;
    }

    public ApplicationUsageControl getApplicationUsageControl() {
        return auc;
    }

    public void setApplicationUsageControl(ApplicationUsageControl auc) {
        this.auc = auc;
    }

    public ApplicationPriorityIndicator getApplicationPriorityIndicator() {
        return api;
    }

    public void setApplicationPriorityIndicator(ApplicationPriorityIndicator api) {
        this.api = api;
    }

    public void setApplicationInterchangeProfile(ApplicationInterchangeProfile aip) {
        this.aip = aip;
    }

    public ApplicationInterchangeProfile getApplicationInterchangeProfile() {
        return aip;
    }

    public void setApplicationFileLocator(ApplicationFileLocator afl) {
        this.afl = afl;
    }

    public ApplicationFileLocator getApplicationFileLocator() {
        return afl;
    }

    public AID getAID() {
        return aid;
    }

    public void setPAN(PAN pan) {
        this.pan = pan;
    }

    public PAN getPAN() {
        return pan;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void addUnhandledRecord(BERTLV bertlv) {
        unhandledRecords.add(bertlv);
    }

    public List<BERTLV> getUnhandledRecords() {
        return Collections.unmodifiableList(unhandledRecords);
    }

    public void setATC(int atc) {
        this.applicationTransactionCounter = atc;
    }

    public void setApplicationCurrencyCode(int applicationCurrencyCode) {
        this.applicationCurrencyCode = applicationCurrencyCode;
    }

    public int getApplicationCurrencyCode() {
        return applicationCurrencyCode;
    }

    public void setApplicationCurrencyExponent(int applicationCurrencyExponent) {
        this.applicationCurrencyExponent = applicationCurrencyExponent;
    }

    public int getApplicationCurrencyExponent() {
        return applicationCurrencyExponent;
    }

    public void setIssuerCountryCode(int issuerCountryCode) {
        this.issuerCountryCode = issuerCountryCode;
    }

    public int getIssuerCountryCode() {
        return issuerCountryCode;
    }

    public void setLastOnlineATCRecord(int lastOnlineATCRecord) {
        this.lastOnlineATCRegister = lastOnlineATCRecord;
    }

    public void setPINTryCounter(int counter) {
        this.pinTryCounter = counter;
    }

    public void setLogFormat(LogFormat logFormat) {
        this.logFormat = logFormat;
    }

    public DOL getPDOL() {
        return pdol;
    }

    public void setPDOL(DOL pdol) {
        this.pdol = pdol;
    }

    public DOL getDDOL() {
        return ddol;
    }

    public void setDDOL(DOL ddol) {
        this.ddol = ddol;
    }

    public void setCardholderName(String name) {
        this.cardholderName = name;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public IssuerPublicKeyCertificate getIssuerPublicKeyCertificate() {
        return issuerCert;
    }

    public void setIssuerPublicKeyCertificate(IssuerPublicKeyCertificate issuerCert) {
        this.issuerCert = issuerCert;
    }

    public ICCPublicKeyCertificate getICCPublicKeyCertificate() {
        return iccCert;
    }

    public void setICCPublicKeyCertificate(ICCPublicKeyCertificate iccCert) {
        this.iccCert = iccCert;
    }

    public SignedStaticApplicationData getSignedStaticApplicationData() {
        return signedStaticAppData;
    }

    public void setSignedStaticApplicationData(SignedStaticApplicationData signedStaticAppData) {
        this.signedStaticAppData = signedStaticAppData;
    }

    public void setCDOL1(DOL cdol1) {
        this.cdol1 = cdol1;
    }

    public void setCDOL2(DOL cdol2) {
        this.cdol2 = cdol2;
    }

    public void setExpirationDate(byte[] dateBytes) {
        if (dateBytes.length != 3) {
            throw new EMVException("Byte array length must be 3. Length=" + dateBytes.length);
        }
        int YY = Util.numericByteToInt(dateBytes[0]);
        int MM = Util.numericByteToInt(dateBytes[1]);
        int DD = Util.numericByteToInt(dateBytes[2]);
        Calendar cal = Calendar.getInstance();
        cal.set(2000 + YY, MM - 1, DD, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        this.applicationExpirationDate = cal.getTime();
    }

    public Date getExpirationDate() {
        return (Date) applicationExpirationDate.clone();
    }

    public void setEffectiveDate(byte[] dateBytes) {
        if (dateBytes.length != 3) {
            throw new EMVException("Byte array length must be 3. Length=" + dateBytes.length);
        }
        int YY = Util.numericByteToInt(dateBytes[0]);
        int MM = Util.numericByteToInt(dateBytes[1]);
        int DD = Util.numericByteToInt(dateBytes[2]);
        Calendar cal = Calendar.getInstance();
        cal.set(2000 + YY, MM - 1, DD, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        this.applicationEffectiveDate = cal.getTime();
    }

    public Date getEffectiveDate() {
        return (Date) applicationEffectiveDate.clone();
    }

    public void setApplicationVersionNumber(int version) {
        this.applicationVersionNumber = version;
    }

    public int getApplicationVersionNumber() {
        return applicationVersionNumber;
    }

    void setTrack1DiscretionaryData(byte[] valueBytes) {
        this.track1DiscretionaryData = valueBytes;
    }

    public byte[] getTrack1DiscretionaryData() {
        return Arrays.copyOf(track1DiscretionaryData, track1DiscretionaryData.length);
    }

    void setTrack2DiscretionaryData(byte[] valueBytes) {
        this.track2DiscretionaryData = valueBytes;
    }

    public byte[] getTrack2DiscretionaryData() {
        return Arrays.copyOf(track2DiscretionaryData, track2DiscretionaryData.length);
    }

    void setTrack2EquivalentData(Track2EquivalentData track2EquivalentData) {
        this.track2EquivalentData = track2EquivalentData;
    }

    public Track2EquivalentData getTrack2EquivalentData() {
        return track2EquivalentData;
    }

    void setServiceCode(int serviceCode) {
        this.serviceCode = serviceCode;
    }

    public int getServiceCode() {
        return serviceCode;
    }

    void setCVMList(CVMList cvmList) {
        this.cvmList = cvmList;
    }

    public CVMList getCVMList() {
        return cvmList;
    }

    void setStaticDataAuthenticationTagList(StaticDataAuthenticationTagList staticDataAuthTagList) {
        this.staticDataAuthTagList = staticDataAuthTagList;
    }

    public StaticDataAuthenticationTagList getStaticDataAuthenticationTagList() {
        return staticDataAuthTagList;
    }

    void setPANSequenceNumber(byte value) {
        this.panSequenceNumber = Util.numericByteToInt(value);
    }

    public int getPANSequenceNumber() {
        return this.panSequenceNumber;
    }

    void setLanguagePreference(LanguagePreference languagePreference) {
        if (this.languagePreference != null) {
            throw new RuntimeException("Must create method 'ADD LanguagePreference', not SET");
        }
        this.languagePreference = languagePreference;
    }

    public LanguagePreference getLanguagePreference() {
        return this.languagePreference;
    }

    public void setIssuerCodeTableIndex(int index) {
        issuerCodeTableIndex = index;
    }

    public int getIssuerCodeTableIndex() {
        return issuerCodeTableIndex;
    }

    public String getIssuerCodeTable() {
        return "ISO-8859-" + issuerCodeTableIndex;
    }

    public void setIssuerActionCodeDefault(byte[] data) {
        issuerActionCodeDefault = data;
    }

    public void setIssuerActionCodeDenial(byte[] data) {
        issuerActionCodeDenial = data;
    }

    public void setIssuerActionCodeOnline(byte[] data) {
        issuerActionCodeOnline = data;
    }

    public TransactionStatusInformation getTransactionStatusInformation() {
        return transactionStatusInformation;
    }

    public byte[] getOfflineDataAuthenticationRecords() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (ApplicationElementaryFile aef : this.getApplicationFileLocator().getApplicationElementaryFiles()) {

            //Only those records identified in the AFL as participating in offline data authentication are to be processed.
            if (aef.getNumRecordsInvolvedInOfflineDataAuthentication() == 0) {
                continue;
            }

            for (Record record : aef.getRecords()) {

                if (!record.isInvolvedInOfflineDataAuthentication()) {
                    continue;
                }

                byte[] fileRawData = record.getRawData();
                if (fileRawData == null || fileRawData.length < 2) {
                    //The records read for offline data authentication shall be TLV-coded with tag equal to '70'
                    throw new SignedDataException("File Raw Data was null or invalid length (less than 2): " + fileRawData == null ? "null" : String.valueOf(fileRawData.length));
                }
                //The records read for offline data authentication shall be TLV-coded with tag equal to '70'
                if (fileRawData[0] != (byte) 0x70) {
                    //If the records read for offline data authentication are not TLV-coded with tag equal to '70'
                    //then offline data authentication shall be considered to have been performed and to have failed;
                    //that is, the terminal shall set the 'Offline data authentication was performed' bit in the TSI to 1,
                    //and shall set the appropriate 'SDA failed' or 'DDA failed' or 'CDA failed' bit in the TVR.
                    //TODO
                }

                //The data from each record to be included in the offline data authentication input
                //depends upon the SFI of the file from which the record was read.
                int sfi = aef.getSFI().getValue();
                if (sfi >= 1 && sfi <= 10) {
                    //For files with SFI in the range 1 to 10, the record tag ('70') and the record length
                    //are excluded from the offline data authentication process. All other data in the
                    //data field of the response to the READ RECORD command (excluding SW1 SW2) is included.

                    //Get the 'valueBytes'
                    BERTLV tlv = EMVUtil.getNextTLV(new ByteArrayInputStream(fileRawData));
                    stream.write(tlv.getValueBytes(), 0, tlv.getValueBytes().length);
                } else {
                    //For files with SFI in the range 11 to 30, the record tag ('70') and the record length
                    //are not excluded from the offline data authentication process. Thus all data in the
                    //data field of the response to the READ RECORD command (excluding SW1 SW2) is included
                    stream.write(fileRawData, 0, fileRawData.length);
                }
            }


        }

        //After all records identified by the AFL have been processed, the Static Data Authentication Tag List is processed,
        //if it exists. If the Static Data Authentication Tag List exists, it shall contain only the tag for the
        //Application Interchange Profile. The tag must represent the AIP available in the current application.
        //The value field of the AIP is to be concatenated to the current end of the input string.
        //The tag and length of the AIP are not included in the concatenation.
        StaticDataAuthenticationTagList sdaTagListObject = this.getStaticDataAuthenticationTagList();
        if(sdaTagListObject != null){
            List<Tag> sdaTagList = sdaTagListObject.getTagList();
            if (sdaTagList != null && !sdaTagList.isEmpty()) {
                if (sdaTagList.size() > 1 || sdaTagList.get(0) != EMVTags.APPLICATION_INTERCHANGE_PROFILE) {
                    throw new EMVException("SDA Tag list must contain only the 'Application Interchange Profile' tag: " + sdaTagList);
                } else {
                    byte[] aipBytes = this.getApplicationInterchangeProfile().getBytes();
                    stream.write(aipBytes, 0, aipBytes.length);
                }
            }
        }

        return stream.toByteArray();
    }

    //The initializedOnICC methods are only used to indicate that the
    //GET PROCESSING OPTS has been performed
    public void setInitializedOnICC() {
        isInitializedOnICC = true;
    }

    public boolean isInitializedOnICC() {
        return isInitializedOnICC;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getEmptyString(indent) + "Application");

        String indentStr = Util.getEmptyString(indent + 3);

        if (aid != null) {
            aid.dump(pw, indent + 3);
        }
        pw.println(indentStr + "Label: " + getLabel());
        pw.println(indentStr + "Preferred Name: " + getPreferredName());
        if (applicationEffectiveDate != null) {
            pw.println(indentStr + "Application Effective Date: " + applicationEffectiveDate);
        }
        if (applicationExpirationDate != null) {
            pw.println(indentStr + "Application Expiration Date: " + applicationExpirationDate);
        }
        if (applicationVersionNumber != -1) {
            pw.println(indentStr + "Application Version Number: " + applicationVersionNumber);
        }
        if (applicationCurrencyCode != -1) {
            String description = "";
            ISO4217_Numeric.Currency currency = ISO4217_Numeric.getCurrencyForCode(applicationCurrencyCode);
            if (currency != null) {
                description = " (" + currency.getCode() + " " + currency.getDisplayName() + ")";
            }
            pw.println(indentStr + "Application Currency Code (ISO 4217): " + applicationCurrencyCode + description);
        }
        if (applicationCurrencyExponent != -1) {
            pw.println(indentStr + "Application Currency Exponent: " + applicationCurrencyExponent + " (Position of the decimal point from the right)");
        }
        if (issuerCountryCode != -1) {
            String description = "";
            String countryStr = ISO3166_1.getCountryForCode(issuerCountryCode);
            if (countryStr != null && countryStr.trim().length() > 0) {
                description = " (" + countryStr + ")";
            }
            pw.println(indentStr + "Issuer Country Code (ISO 3166-1): " + issuerCountryCode + description);
        }
        if (applicationTransactionCounter != -1) {
            pw.println(indentStr + "Application Transaction Counter (ATC): " + applicationTransactionCounter);
        }
        if (lastOnlineATCRegister != -1) {
            pw.println(indentStr + "Last Online ATC Register: " + lastOnlineATCRegister);
        }
        if (pinTryCounter != -1) {
            pw.println(indentStr + "PIN Try Counter: " + pinTryCounter + " (Number of PIN tries remaining)");
        }
        if (cardholderName != null) {
            pw.println(indentStr + "Cardholder Name: " + cardholderName);
        }
        if (pan != null) {
            pan.dump(pw, indent + 3);
        }
        if (panSequenceNumber != -1) {
            pw.println(indentStr + "PAN Sequence Number: " + panSequenceNumber);
        }
        if (api != null) {
            api.dump(pw, indent + 3);
        }
        if (aip != null) {
            aip.dump(pw, indent + 3);
        }
        if (afl != null) {
            afl.dump(pw, indent + 3);
        }
        if (auc != null) {
            auc.dump(pw, indent + 3);
        }
        if (logFormat != null) {
            logFormat.dump(pw, indent + 3);
        }
        if (pdol != null) {
            pdol.dump(pw, indent + 3);
        }
        if (ddol != null) {
            ddol.dump(pw, indent + 3);
        }
        if (issuerCert != null) {
            issuerCert.dump(pw, indent + 3);
        }
        if (iccCert != null) {
            iccCert.dump(pw, indent + 3);
        }
        if (cdol1 != null) {
            cdol1.dump(pw, indent + 3);
        }
        if (cdol2 != null) {
            cdol2.dump(pw, indent + 3);
        }
        if (signedStaticAppData != null) {
            signedStaticAppData.dump(pw, indent + 3);
        }
        if (cvmList != null) {
            cvmList.dump(pw, indent + 3);
        }
        if (staticDataAuthTagList != null) {
            staticDataAuthTagList.dump(pw, indent + 3);
        }
        if (track1DiscretionaryData != null) {
            pw.println(indentStr + "Track 1 Discretionary Data:");
            pw.println(indentStr + "   " + Util.byteArrayToHexString(track1DiscretionaryData) + " (ASCII: " + Util.getSafePrintChars(track1DiscretionaryData) + ")");
        }
        if (track2DiscretionaryData != null) {
            pw.println(indentStr + "Track 2 Discretionary Data:");
            pw.println(indentStr + "   " + Util.byteArrayToHexString(track2DiscretionaryData) + " (ASCII: " + Util.getSafePrintChars(track2DiscretionaryData) + ")");
        }
        if (track2EquivalentData != null) {
            track2EquivalentData.dump(pw, indent + 3);
        }
        if (serviceCode != -1) {
            pw.println(indentStr + "Service Code: " + serviceCode);
        }
        if (languagePreference != null) {
            languagePreference.dump(pw, indent + 3);
        }
        if (issuerCodeTableIndex != -1) {
            pw.println(indentStr + "Issuer Code Table Index: " + issuerCodeTableIndex + " (ISO-8859-" + issuerCodeTableIndex + ")");
        }
        if (issuerActionCodeDefault != null) {
            pw.println(indentStr + "Issuer Action Code - Default:");
            for (byte b : issuerActionCodeDefault) {
                pw.println(indentStr + "   " + Util.byte2BinaryLiteral(b));
            }
        }
        if (issuerActionCodeDenial != null) {
            pw.println(indentStr + "Issuer Action Code - Denial:");
            for (byte b : issuerActionCodeDenial) {
                pw.println(indentStr + "   " + Util.byte2BinaryLiteral(b));
            }
        }
        if (issuerActionCodeOnline != null) {
            pw.println(indentStr + "Issuer Action Code - Online:");
            for (byte b : issuerActionCodeOnline) {
                pw.println(indentStr + "   " + Util.byte2BinaryLiteral(b));
            }
        }
        if (!unhandledRecords.isEmpty()) {
            pw.println(indentStr + "UNHANDLED APPLICATION RECORDS (" + unhandledRecords.size() + " found):");
        }
        for (BERTLV tlv : unhandledRecords) {
            pw.println(Util.getEmptyString(indent + 6) + tlv.getTag() + " " + tlv);
        }
        pw.println("");
    }
}
