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
import sasc.util.Util;

/**
 *
 * @author sasc
 */
public class BERTLV {

    private Tag tag;
    private byte[] rawEncodedLengthBytes;
    private byte[] valueBytes;
    private int length;

    /**
     *
     * @param tag
     * @param length contains the number of value bytes (parsed from the rawEncodedLengthBytes)
     * @param rawLengthBytes the raw encoded length bytes
     * @param valueBytes
     */
    public BERTLV(Tag tag, int length, byte[] rawEncodedLengthBytes, byte[] valueBytes) {
        if (length != valueBytes.length) {
            //Assert
            throw new RuntimeException("length != bytes.length");
        }
        this.tag = tag;
        this.rawEncodedLengthBytes = rawEncodedLengthBytes;
        this.valueBytes = valueBytes;
        this.length = length;
    }

    public byte[] getTagBytes() {
        return tag.getTagBytes();
    }

    public byte[] getRawEncodedLengthBytes() {
        return rawEncodedLengthBytes;
    }

    public byte[] getValueBytes() {
        return valueBytes;
    }

    public ByteArrayInputStream getValueStream() {
        return new ByteArrayInputStream(valueBytes);
    }

    public byte[] toBERTLVByteArray() {
        byte[] tagBytes = tag.getTagBytes();
        ByteArrayOutputStream stream = new ByteArrayOutputStream(tagBytes.length+rawEncodedLengthBytes.length+valueBytes.length);
        stream.write(tagBytes, 0, tagBytes.length);
        stream.write(rawEncodedLengthBytes, 0, rawEncodedLengthBytes.length);
        stream.write(valueBytes, 0, valueBytes.length);
        return stream.toByteArray();
    }

    @Override
    public String toString() {
        return "BER-TLV[" + Util.byteArrayToHexString(getTagBytes()) + ", " + Util.int2Hex(length) + " (raw " + Util.byteArrayToHexString(rawEncodedLengthBytes) + ")" + ", " + Util.byteArrayToHexString(valueBytes) + "]";
    }

    public Tag getTag() {
        return tag;
    }

    public int getLength() {
        return length;
    }
}
