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
package sasc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.util.HashMap;
import java.util.StringTokenizer;
import sasc.emv.RID;
import sasc.util.Util;

/**
 * Summary of the Register of Issued Numbers Report (RMG)
 *
 * @author sasc
 */
public class RID_DB {

    private static final HashMap<String, RID> ridMap;

    static {
        ridMap = new HashMap<String, RID>();

        InputStream is1 = null;
        InputStream is2 = null;
        BufferedReader br = null;

        try {
            is1 = RID_DB.class.getResourceAsStream("/RID-list_RMG.txt");
            is2 = RID_DB.class.getResourceAsStream("/RID-list_other.txt");
            br = new BufferedReader(new InputStreamReader(new SequenceInputStream(is1, is2)));

            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.trim().length() == 0) {
                    continue;
                } else {
                    StringTokenizer st = new StringTokenizer(line, ";");
                    if (st.countTokens() != 3) {
                        throw new RuntimeException("RID lists should contain three values pr line separated by \";\" . "+line);
                    }
                    String ridStr = st.nextToken().trim();
                    String applicant = st.nextToken().trim();
                    String country = st.nextToken().trim();
                    if (ridMap.containsKey(ridStr)) { //Should not happen
                        throw new RuntimeException("RID: Duplicate value \"" + ridStr + "\" found");
                    }
                    RID rid = new RID(ridStr, applicant, country);
                    ridMap.put(ridStr, rid);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (is1 != null) {
                try {
                    is1.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
            if (is2 != null) {
                try {
                    is2.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
        }

    }

    public static RID searchRID(byte[] atr) {
        return ridMap.get(Util.byteArrayToHexString(atr).toUpperCase());
    }

    public static void main(String[] args) {
        System.out.println(RID_DB.searchRID(new byte[]{(byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03}));
        System.out.println(RID_DB.searchRID(new byte[]{(byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x10}));
        System.out.println(RID_DB.searchRID(new byte[]{(byte) 0xD5, (byte) 0x78, (byte) 0x00, (byte) 0x00, (byte) 0x02}));
    }
}
