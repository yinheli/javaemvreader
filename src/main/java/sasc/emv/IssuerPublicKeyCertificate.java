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
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class IssuerPublicKeyCertificate {

    private IssuerPublicKey issuerPublicKey;
    private int caPublicKeyIndex = -1;
    private boolean isValid = false;
    private byte[] signedBytes;
    private CA ca;
    private int issuerIdentifier = -1;
    private byte certFormat;
    private byte[] certExpirationDate = new byte[2];
    private byte[] certSerialNumber = new byte[3];
    private int hashAlgorithmIndicator;
    private int issuerPublicKeyAlgorithmIndicator;
    private byte[] hash = new byte[20];
    private boolean validationPerformed = false;

    public IssuerPublicKeyCertificate(CA ca) {
        //ca == null is permitted
//        if(ca == null) {
//            throw new IllegalArgumentException("Argument 'ca' cannot be null");
//        }
        this.ca = ca;
        issuerPublicKey = new IssuerPublicKey();
    }

    public void setCAPublicKeyIndex(int index) {
        this.caPublicKeyIndex = index;
    }

    public void setSignedBytes(byte[] signedBytes) {
        this.signedBytes = signedBytes;
    }

    public IssuerPublicKey getIssuerPublicKey() {
        return issuerPublicKey;
    }

    //Perform lazy validation, since we might not have all the data elements initially
    //This method must only be called after ALL application records have been read
    public boolean validate() {
        if (validationPerformed) { //Validation already run
            return isValid();
        }
        validationPerformed = true;
        if(this.ca == null){
            isValid = false;
            return isValid();
        }
        CAPublicKey caPublicKey = ca.getPublicKey(caPublicKeyIndex);

        if (caPublicKey == null) {
            throw new EMVException("No suitable CA Public Key found");
        }
        //Decipher data using RSA
        byte[] recoveredBytes = Util.performRSA(signedBytes, caPublicKey.getExponent(), caPublicKey.getModulus());

        Log.debug("IssuerPKCert recoveredBytes="+Util.prettyPrintHex(recoveredBytes));

        ByteArrayInputStream bis = new ByteArrayInputStream(recoveredBytes);

        if (bis.read() != 0x6a) { //Header
            throw new EMVException("Header != 0x6a");
        }

        certFormat = (byte) bis.read();

        if (certFormat != 0x02) {
            throw new EMVException("Invalid certificate format");
        }

        byte[] issuerIdentifierPaddedBytes = new byte[4];

        bis.read(issuerIdentifierPaddedBytes, 0, issuerIdentifierPaddedBytes.length);

        //Remove padding (if any) from issuerIdentifier
        String iiStr = Util.byteArrayToHexString(issuerIdentifierPaddedBytes);
        int padStartIndex = iiStr.toUpperCase().indexOf('F');
        if(padStartIndex != -1){
            iiStr = iiStr.substring(0, padStartIndex);
        }
        issuerIdentifier = Util.numericHexToInt(iiStr);

        bis.read(certExpirationDate, 0, certExpirationDate.length);

        bis.read(certSerialNumber, 0, certSerialNumber.length);

        hashAlgorithmIndicator = bis.read() & 0xFF;

        issuerPublicKeyAlgorithmIndicator = bis.read() & 0xFF;

        int issuerPublicKeyModLengthTotal = bis.read() & 0xFF;

        int issuerPublicKeyExpLengthTotal = bis.read() & 0xFF;

        int modBytesLength = bis.available() - 21;

        if(issuerPublicKeyModLengthTotal < modBytesLength){
            //The mod bytes block in this cert contains padding.
            //we don't want padding in our key
            modBytesLength = issuerPublicKeyModLengthTotal;
        }

        byte[] modtmp = new byte[modBytesLength];

        bis.read(modtmp, 0, modtmp.length);

        issuerPublicKey.setModulus(modtmp);

        //Now read padding bytes (0xbb), if available
        //The padding bytes are not used
        byte[] padding = new byte[bis.available()-21];
        bis.read(padding, 0, padding.length);

        bis.read(hash, 0, hash.length);

        //TODO check hash validation
        ByteArrayOutputStream hashStream = new ByteArrayOutputStream();

        hashStream.write(certFormat);
        hashStream.write(issuerIdentifierPaddedBytes, 0, issuerIdentifierPaddedBytes.length);
        hashStream.write(certExpirationDate, 0, certExpirationDate.length);
        hashStream.write(certSerialNumber, 0, certSerialNumber.length);
        hashStream.write((byte)hashAlgorithmIndicator);
        hashStream.write((byte)issuerPublicKeyAlgorithmIndicator);
        hashStream.write((byte)issuerPublicKeyModLengthTotal);
        hashStream.write((byte)issuerPublicKeyExpLengthTotal);
        byte[] ipkModulus = issuerPublicKey.getModulus();
        hashStream.write(ipkModulus, 0, ipkModulus.length);
        byte[] ipkExponent = issuerPublicKey.getExponent();
        hashStream.write(ipkExponent, 0, ipkExponent.length);


        byte[] sha1Result = null;
        try {
            sha1Result = Util.calculateSHA1(hashStream.toByteArray());
        } catch (NoSuchAlgorithmException ex) {
            throw new SignedDataException("SHA-1 hash algorithm not available", ex);
        }

        if (!Arrays.equals(sha1Result, hash)) {
            throw new SignedDataException("Hash is not valid");
        }

        int trailer = bis.read();

        if (trailer != 0xbc) {//Trailer
            throw new EMVException("Trailer != 0xbc");
        }

        if (bis.available() > 0) {
            throw new EMVException("Error parsing certificate. Bytes left=" + bis.available());
        }
        isValid = true;
        return true;
    }

    public boolean isValid() {
        return isValid;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getEmptyString(indent) + "Issuer Public Key Certificate");
        String indentStr = Util.getEmptyString(indent + 3);

        if (!validationPerformed) {
            validate();
        }

        if (isValid()) {
            pw.println(indentStr + "Issuer Identifier: " + issuerIdentifier);
            if (caPublicKeyIndex != -1) {
                pw.println(indentStr + "CA Public Key Index: " + caPublicKeyIndex);
            }
            pw.println(indentStr + "Certificate Format: " + certFormat);
            pw.println(indentStr + "Certificate Expiration Date (MMYY): " + Util.byteArrayToHexString(certExpirationDate));
            pw.println(indentStr + "Certificate Serial Number: " + Util.byteArrayToHexString(certSerialNumber) + " ("+Util.byteArrayToInt(certSerialNumber)+")");
            pw.println(indentStr + "Hash Algorithm Indicator: " + hashAlgorithmIndicator + " (=SHA-1)");
            pw.println(indentStr + "Issuer Public Key Algorithm Indicator: " + issuerPublicKeyAlgorithmIndicator + " (=RSA)");
            pw.println(indentStr + "Hash: " + Util.byteArrayToHexString(hash));

            issuerPublicKey.dump(pw, indent + 3);
        } else {
            if(this.ca == null){
                pw.println(indentStr + "NO CA CONFIGURED FOR THIS RID. UNABLE TO VALIDATE CERTIFICATE");
            }else{
                pw.println(indentStr + "CERTIFICATE NOT VALID");
            }
        }
    }
}
