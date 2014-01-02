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
import sasc.iso7816.AID;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nanoxml.XMLElement;
import sasc.util.ByteArrayWrapper;
import sasc.util.Util;

/**
 * Certification Authority (CA)
 *
 * Trusted third party that establishes a proof that links a public key and
 * other relevant information to its owner.
 *
 * EMV Book 2: Every terminal conforming to this specification shall contain the
 * appropriate certification authority’s public key(s) for every application
 * recognized by the terminal. To support SDA, each terminal shall be able to
 * store six certification authority public keys per Registered Application
 * Provider Identifier (RID) and shall associate with each such key the
 * key-related information to be used with the key (so that terminals can in the
 * future support multiple algorithms and allow an evolutionary transition from
 * one to another, as discussed in section 11.2.2). The terminal shall be able
 * to locate any such key (and the key-related information) given the RID and
 * Certification Authority Public Key Index as provided by the ICC. SDA shall
 * use a reversible algorithm as specified in Annex A2.1 and Annex B2. Section
 * 5.1 contains an overview of the keys and certificates involved in the SDA
 * process, and sections 5.2 to 5.4 specify the three main steps in the process,
 * namely:
 * - Retrieval of the Certification Authority Public Key by the terminal
 * - Retrieval of the Issuer Public Key by the terminal
 * - Verification of the Signed Static Application Data by the terminal
 *
 * If SDA fails then the terminal shall set the ‘SDA failed’ bit in the Terminal
 * Verification Results (TVR) to 1.
 *
 * @author sasc
 */
public class CA {

    private static final Map<ByteArrayWrapper, CA> certificationAuthorities = new LinkedHashMap<ByteArrayWrapper, CA>();
    private byte[] rid;
    private String name;
    private String description;
    private Map<Integer, CAPublicKey> publicKeys = publicKeys = new LinkedHashMap<Integer, CAPublicKey>();

    static {
        _initFromFile("/certificationauthorities.xml");
    }

    private static void _initFromFile(String filename) {
        certificationAuthorities.clear();
        addFromXmlFile(filename);
    }

    private CA() {
        //Private constructor
    }

    public static byte[] calculateCAPublicKeyCheckSum(byte[] rid, byte[] caPublicKeyIndex, byte[] caPublicKeyMod, byte[] caPublicKeyExp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(rid.length + caPublicKeyIndex.length + caPublicKeyMod.length + caPublicKeyExp.length);
        stream.write(rid, 0, rid.length);
        stream.write(caPublicKeyIndex, 0, caPublicKeyIndex.length);
        stream.write(caPublicKeyMod, 0, caPublicKeyMod.length);
        stream.write(caPublicKeyExp, 0, caPublicKeyExp.length);
        byte[] sha1Result;
        try {
            sha1Result = Util.calculateSHA1(stream.toByteArray());
        } catch (NoSuchAlgorithmException ex) {
            throw new SmartCardException("SHA-1 hash algorithm not available", ex);
        }
        return sha1Result;
    }

    public Collection<CAPublicKey> getCAPublicKeys() {
        return Collections.unmodifiableCollection(publicKeys.values());
    }

    //The RID and the Certification Public Key Index together uniquely identify the
    //Certification Authority Public Key and associate it with the proper Payment System
    public CAPublicKey getPublicKey(int index) {
        return publicKeys.get(new Integer(index));
    }

    public byte[] getRID() {
        return Util.copyByteArray(rid);
    }

    //Terminals that support Static Data Authentication and/or Dynamic Data Authentication
    //shall provide support for six Certification Authority Public Keys per Registered
    //Application Provider Identifier (RID) for Europay, MasterCard and Visa debit/credit
    //applications based on EMV ’96 IC Card Specification for Payment Systems, Version
    //3.1.1.
    private void setPublicKey(int index, CAPublicKey publicKey) {
        Integer idx = new Integer(index);
        if (publicKeys.containsKey(idx)) {
            throw new IllegalArgumentException("Public Key index " + index + " already added");
        }
        publicKeys.put(idx, publicKey);
    }

    private void setRID(byte[] rid) {
        this.rid = Util.copyByteArray(rid);
    }

    private void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static CA getCA(byte[] rid) {
        return certificationAuthorities.get(ByteArrayWrapper.wrapperAround(rid));
    }

    public static CA getCA(AID aid) {
        return certificationAuthorities.get(ByteArrayWrapper.wrapperAround(aid.getRIDBytes()));
    }

    public static Collection<CA> getCAs() {
        return Collections.unmodifiableCollection(certificationAuthorities.values());
    }

    public static void initFromFile(String fileName) {
        _initFromFile(fileName);
    }

    public static void addFromXmlFile(String fileName) {
        try {
            XMLElement certificationAuthoritiesElement = new XMLElement();
            certificationAuthoritiesElement.parseFromReader(new InputStreamReader(Util.loadResource(CA.class, fileName), "UTF-8"));

            if (!"CertificationAuthorities".equalsIgnoreCase(certificationAuthoritiesElement.getName())) {
                throw new RuntimeException("Unexpected Root Element: <" + certificationAuthoritiesElement.getName() + "> . Expected <CertificationAuthorities>");
            }
            for (Object caObject : certificationAuthoritiesElement.getChildren()) {
                XMLElement caElement = (XMLElement) caObject;
                byte[] rid = Util.fromHexString(caElement.getStringAttribute("RID"));
                if (rid.length != 5) {
                    throw new SmartCardException("Unexpected RID length: " + rid.length + ". Length must be 5 bytes. RID=" + Util.prettyPrintHexNoWrap(rid));
                }

                CA ca = new CA();
                ca.setRID(rid);
                for (Object caChild : caElement.getChildren()) {
                    XMLElement caChildElement = (XMLElement) caChild;
                    String name = caChildElement.getName();
                    if ("Name".equalsIgnoreCase(name)) {
                        ca.setName(caChildElement.getContent().trim());
                    } else if ("Description".equalsIgnoreCase(name)) {
                        ca.setDescription(caChildElement.getContent().trim());
                    } else if ("PublicKeys".equalsIgnoreCase(name)) {
                        for (Object pkObject : caChildElement.getChildren()) {
                            XMLElement pkElement = (XMLElement) pkObject;
                            byte[] exp = null;
                            byte[] mod = null;
                            int index = pkElement.getIntAttribute("index");
                            int hashAlgorithmIndicator = -1;
                            int publicKeyAlgorithmIndicator = -1;
                            String description = "";
                            String expirationDate = "";
                            byte[] hash = null;
                            for (Object pkObjectChild : pkElement.getChildren()) {
                                XMLElement pkChildElement = (XMLElement) pkObjectChild;
                                String pkChildElementName = pkChildElement.getName();
                                if ("Description".equalsIgnoreCase(pkChildElementName)) {
                                    description = pkChildElement.getContent().trim();
                                } else if ("ExpirationDate".equalsIgnoreCase(pkChildElementName)) {
                                    expirationDate = pkChildElement.getContent().trim();
                                } else if ("Exponent".equalsIgnoreCase(pkChildElementName)) {
                                    exp = Util.fromHexString(pkChildElement.getContent().trim());
                                } else if ("Modulus".equalsIgnoreCase(pkChildElementName)) {
                                    mod = Util.fromHexString(Util.removeCRLFTab(pkChildElement.getContent().trim()));
                                } else if ("HashAlgorithmIndicator".equalsIgnoreCase(pkChildElementName)) {
                                    hashAlgorithmIndicator = Util.byteArrayToInt(Util.fromHexString(pkChildElement.getContent().trim()));
                                } else if ("Hash".equalsIgnoreCase(pkChildElementName)) {
                                    hash = Util.fromHexString(Util.removeCRLFTab(pkChildElement.getContent().trim()));
                                } else if ("PublicKeyAlgorithmIndicator".equalsIgnoreCase(pkChildElementName)) {
                                    publicKeyAlgorithmIndicator = Util.byteArrayToInt(Util.fromHexString(pkChildElement.getContent().trim()));
                                } else {
                                    throw new RuntimeException("Unexpected XML Element: <" + pkChildElementName + "> : " + pkChildElement);
                                }

                            }
                            byte[] sha1ChecksumResult = calculateCAPublicKeyCheckSum(ca.getRID(), Util.intToByteArray(index), mod, exp);
                            if (!Arrays.equals(hash, sha1ChecksumResult)) {
                                throw new SmartCardException("Checksum not correct for key index " + index + " for CA RID " + Util.prettyPrintHexNoWrap(ca.getRID()) + ". Expected " + Util.byteArrayToHexString(hash) + " but was " + Util.byteArrayToHexString(sha1ChecksumResult));
                            }
                            CAPublicKey pk = new CAPublicKey(index, exp, mod, sha1ChecksumResult, publicKeyAlgorithmIndicator, hashAlgorithmIndicator, description, expirationDate);
                            ca.setPublicKey(index, pk);
                        }
                    } else {
                        throw new RuntimeException("Unexpected XML Element: <" + name + "> : " + caChildElement);
                    }
                }
                certificationAuthorities.put(ByteArrayWrapper.wrapperAround(ca.getRID()), ca);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    public static void addFromDirectory(String dirName) {
        try {
            File dirFile = new File(CA.class.getResource(dirName).toURI());
            if (!dirFile.isDirectory()) {
                throw new IllegalArgumentException(dirName + " does not exist or is not a directory");
            }
            for (File keyFile : dirFile.listFiles()) {
                String keyFileName = keyFile.getName();
                String ridStr = keyFileName.substring(0, keyFileName.indexOf('.'));
                String keyIndexHex = keyFileName.substring(keyFileName.indexOf('.') + 1);
                byte[] rid = Util.fromHexString(ridStr);
                int keyIndex = Util.byteArrayToInt(Util.fromHexString(keyIndexHex));
                CA ca = new CA();
                ca.setRID(rid);

                FileInputStream fis = null;
                String encodedKey = null;
                try {
                    fis = new FileInputStream(keyFile);
                    encodedKey = Util.readInputStreamToString(fis, "UTF-8");
                } finally {
                    if (fis != null) {
                        fis.close();
                    }
                }

                if (encodedKey == null || encodedKey.length() < 3) {
                    throw new NullPointerException("Unable to read key from file: " + keyFileName);
                }

                int numModBytes = Integer.parseInt(encodedKey.substring(0, 3));
                int modBytesStartIndex = 3;
                int modBytesEndIndex = 3 + numModBytes * 2;
                int numExpBytes = Integer.parseInt(encodedKey.substring(modBytesEndIndex, modBytesEndIndex + 2));
                int expBytesStartIndex = modBytesEndIndex + 2;

                String modStr = encodedKey.substring(modBytesStartIndex, modBytesEndIndex);
                String expStr = encodedKey.substring(expBytesStartIndex);

                byte[] mod = Util.fromHexString(modStr);
                byte[] exp = Util.fromHexString(expStr);

                String expirationDate = "31 December 2999"; //Test keys never expire
                String description = "TEST key";
                int publicKeyAlgorithmIndicator = 1; //RSA
                int hashAlgorithmIndicator = 1; //SHA-1

                byte[] sha1ChecksumResult = calculateCAPublicKeyCheckSum(ca.getRID(), Util.intToByteArray(keyIndex), mod, exp);
                CAPublicKey pk = new CAPublicKey(keyIndex, exp, mod, sha1ChecksumResult, publicKeyAlgorithmIndicator, hashAlgorithmIndicator, description, expirationDate);
                ca.setPublicKey(keyIndex, pk);

                certificationAuthorities.put(ByteArrayWrapper.wrapperAround(ca.getRID()), ca);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CA(Name=");
        sb.append(name);
        sb.append(",RID=");
        sb.append(Util.byteArrayToHexString(rid));
        sb.append(",NumPublicKeys=");
        sb.append(publicKeys.size());
        sb.append(")");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        CA.initFromFile("/CertificationAuthorities_Test.xml");

        for (CA ca : CA.getCAs()) {
            System.out.println(ca);
            for (CAPublicKey caPublicKey : ca.getCAPublicKeys()) {
                System.out.println(caPublicKey);
            }
        }
    }
}
