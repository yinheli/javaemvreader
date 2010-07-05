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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import sasc.util.Util;

/**
 * Ludovic Rousseau's smartcard list
 * http://ludovic.rousseau.free.fr/softwares/pcsc-tools/smartcard_list.txt
 *
 * + some additional ATRs
 *
 * TODO: The ATRs in Rousseau's list are in regular expression form
 *
 * @author sasc
 */
public class ATR_DB {

    private static final HashMap<String, PublicATR> atrMap;

    static{
        atrMap = new HashMap<String, PublicATR>();

        InputStream is1 = null;
        InputStream is2 = null;
        BufferedReader br = null;

        try{
            is1 = ATR_DB.class.getResourceAsStream("/smartcard_list.txt");
            is2 = ATR_DB.class.getResourceAsStream("/smartcard_list_additional_ATRs.txt");
            br = new BufferedReader(new InputStreamReader(new SequenceInputStream(is1, is2)));

            String line;
            String currentATR = null;
            while((line = br.readLine()) != null){
                if(line.startsWith("#")  || line.trim().length() == 0){
                    continue;
                }else if(line.startsWith("\t") && currentATR != null){
                    atrMap.get(currentATR).addDescriptiveText(line.replace("\t", "").trim());
//                    Log.debug("Adding descriptive text for ATR="+currentATR+" "+line.replace("\t", ""));
                }else if(line.startsWith("3")){ // ATR hex
                    currentATR = Util.removeSpaces(line).toUpperCase();
                    if(!atrMap.containsKey(currentATR)){
                        atrMap.put(currentATR, new PublicATR(line));
                    }
                }else{
                    throw new RuntimeException("SOMETHING UNEXPECTED HAPPEND. currentATR="+currentATR+" Line="+line);
                }
            }
        }catch(IOException e){
            throw new RuntimeException(e);
        }finally{
            if(is1 != null){
                try {
                    is1.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
            if(is2 != null){
                try {
                    is2.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
            if(br != null){
                try {
                    br.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
        }

    }

    public static class PublicATR{
        String atr;
        List<String> descriptiveText = new ArrayList<String>();

        public PublicATR(String atr){
            this.atr = atr; //With spaces between bytes
        }

        public void addDescriptiveText(String text){
            descriptiveText.add(text);
        }

        public List<String> getDescriptiveText(){
            return Collections.unmodifiableList(descriptiveText);
        }

    }


    public static List<String> searchATR(byte[] atr){
        //TODO use Regex to match ATR. Pattern/Matcher
        PublicATR publicATR = atrMap.get(Util.byteArrayToHexString(atr).toUpperCase());
        if(publicATR != null){
            return publicATR.getDescriptiveText();
        }
        return null;
    }

    public static void main(String[] args){
        System.out.println(ATR_DB.searchATR(new byte[]{(byte)0x3B, (byte)0x90, (byte)0x95, (byte)0x80, (byte)0x1F, (byte)0xC3, (byte)0x59}));
        System.out.println(ATR_DB.searchATR(new byte[]{(byte)0x3B, (byte)0x04, (byte)0xA2, (byte)0x13, (byte)0x10, (byte)0x91}));
        System.out.println(ATR_DB.searchATR(new byte[]{(byte)0x3B, (byte)0x67, (byte)0x00, (byte)0x00, (byte)0xa6, (byte)0x40, (byte)0x40, (byte)0x00, (byte)0x09, (byte)0x90, (byte)0x00}));
//        3b 67 00 00 a6 40 40 00 09 90 00 
    }
}
