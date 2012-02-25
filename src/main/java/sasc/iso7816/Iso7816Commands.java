/*
 * Copyright 2010 sasc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sasc.iso7816;

import java.io.ByteArrayOutputStream;
import sasc.util.Log;
import sasc.util.Util;

/**
 * See Iso7816-4:2005 Table 4.2
 * 
 * @author sasc
 */
public class Iso7816Commands {
    
    /**
     * Select Master File.
     * Standard iso7816 command
     */
    public static String selectMasterFile() {
        return "00 A4 00 00 00";
    }

    public static String selectMasterFileByIdentifier() {
        return "00 A4 00 00 02 3F 00";
    }

    public static String selectByDFName(byte[] fileBytes) {
        if (fileBytes.length > 16) throw new IllegalArgumentException("DF name not valid (length > 16). Length = "+fileBytes.length);
        //INS A4 = ISO SELECT FILE
        //04 - Direct selection by DF name (data field=DF name)
        return "00 A4 04 00 " + Util.byte2Hex((byte) fileBytes.length) + " " + Util.prettyPrintHexNoWrap(fileBytes);
    }
    
    public static String selectByDFNameNextOccurrence(byte[] fileBytes) {
        //INS A4 = ISO SELECT FILE
        //04 - Direct selection by DF name (data field=DF name)
        //02 - Next occurrence
        return "00 A4 04 02 " + Util.byte2Hex((byte) fileBytes.length) + " " + Util.prettyPrintHexNoWrap(fileBytes);
    }

    public static String readRecord(int recordNum, int sfi) {
        //Valid Record numbers: 1 to 255
        //Valid SFI: 1 to 30
        //SFI=0 : Currently selected EF
        if (recordNum < 1 || recordNum > 255) {
            throw new IllegalArgumentException("Argument 'recordNum' must be in the rage 1 to 255. recordNum=" + recordNum);
        }
        if (sfi < 0 || sfi > 30) {
            throw new IllegalArgumentException("Argument 'sfi' must be in the rage 1 to 30. Use 0 for currently selected EF. sfi=" + sfi);
        }

        String P1 = Util.byte2Hex((byte) recordNum);

        //00010100 = P2
        //00010    = SFI (= 2 << 3)
        //     100 = "Record number can be found in P1" (=4)
        String P2 = Util.byte2Hex((byte) ((sfi << 3) | 4));

        Log.debug("Iso7816Commands.readRecord() P1=" + P1 + " P2=" + P2);

        //00 = No secure messaging
        //B2 = READ RECORD
        //P1 = Record number or record identifier of the first record to be read ('00' indicates the current record)
        //P2 = SFI + 4 (Indicates that the record number can be found in P1)

        return "00 B2 " + P1 + " " + P2 + " 00";
    }

    public static String internalAuthenticate(byte[] authenticationRelatedData) {
        return "00 88 00 00 " + Util.byte2Hex((byte) authenticationRelatedData.length) + " " + Util.prettyPrintHex(authenticationRelatedData) + " 00";
    }

    public static String externalAuthenticate(byte[] cryptogram, byte[] proprietaryBytes) {
        if (cryptogram == null) {
            throw new IllegalArgumentException("Argument 'cryptogram' cannot be null");
        }
        if (cryptogram.length != 8) {
            throw new IllegalArgumentException("Argument 'cryptogram' must have a length of 8. length=" + cryptogram.length);
        }
        int length = cryptogram.length;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(cryptogram, 0, cryptogram.length);
        if (proprietaryBytes != null) {
            if (proprietaryBytes.length < 1 || proprietaryBytes.length > 8) {
                throw new IllegalArgumentException("Argument 'proprietaryBytes' must have a length in the range 1 to 8. length=" + proprietaryBytes.length);
            }
            length += proprietaryBytes.length;
            stream.write(proprietaryBytes, 0, proprietaryBytes.length);
        }
        String lengthStr = Util.byte2Hex((byte) length);
        return "00 82 00 00 " + lengthStr + " " + Util.prettyPrintHex(stream.toByteArray());
    }
}
