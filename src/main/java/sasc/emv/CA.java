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
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Trusted third party that establishes a proof that links a public key and
 * other relevant information to its owner.
 *
 * Currently, only VISA CA is supported
 * @author sasc
 */
public class CA {

    private static final Map<ByteArrayWrapper, CA> certificationAuthorities = new LinkedHashMap<ByteArrayWrapper, CA>();
    private byte[] rid;
    private String name;
    private String description;
    private Map<Integer, CAPublicKey> publicKeys = publicKeys = new LinkedHashMap<Integer, CAPublicKey>();

    static {
        _initFromFile("/CertificationAuthorities.xml");
    }

    private static void _initFromFile(String filename) {
        certificationAuthorities.clear();
        try {
            XMLElement certificationAuthoritiesElement = new XMLElement();
            certificationAuthoritiesElement.parseFromReader(new InputStreamReader(CA.class.getResourceAsStream(filename)));

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
        return Arrays.copyOf(rid, rid.length);
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
        this.rid = Arrays.copyOf(rid, rid.length);
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

    //Only used for testing
    //TODO this method should not be public
    public static void initFromFile(String fileName) {
        _initFromFile(fileName);
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
