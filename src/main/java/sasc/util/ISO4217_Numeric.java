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
package sasc.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * ISO 4217
 * ISO 3-digit Currency Code
 *
 * http://www.iso.org/iso/support/faqs/faqs_widely_used_standards/widely_used_standards_other/currency_codes/currency_codes_list-1.htm
 *
 * java.util.Currency is pretty useless in java 1.6. Must wait for java 1.7 to get the methods:
 * getDisplayName()
 * getNumericCode()
 * getAvailableCurrencies()
 *
 * @author sasc
 */
public class ISO4217_Numeric {

    private static final HashMap<String, Currency> map;

    static{
        map = new HashMap<String, Currency>();

        BufferedReader br = null;

        try{
            br = new BufferedReader(new InputStreamReader(ISO3166_1.class.getResourceAsStream("/iso4217_numeric.txt")));

            String line;
            while((line = br.readLine()) != null){
                if(line.trim().length() <= 0 || line.startsWith("#")){
                    continue;
                }
                StringTokenizer st = new StringTokenizer(line, ",");
                String numericCodeStr = st.nextToken();
                int numericCode = Integer.parseInt(numericCodeStr);
                map.put(numericCodeStr, new Currency(numericCode, st.nextToken(), st.nextToken()));
            }
        }catch(IOException e){
            throw new RuntimeException(e);
        }finally{
            if(br != null){
                try {
                    br.close();
                } catch (IOException ex) {
                    //Ignore
                }
            }
        }

    }

    public static String getCurrencyNameForCode(int code){

        return getCurrencyNameForCode(String.valueOf(code));
    }

    public static String getCurrencyNameForCode(String code){
        Currency c = map.get(code);
        if(c == null){
            return null;
        }
        return c.getDisplayName();
    }

    public static Currency getCurrencyForCode(int code){
        return map.get(String.valueOf(code));
    }

    public static class Currency{
        int numericCode;
        String code;
        String displayName;
        Currency(int numericCode, String code, String displayName){
            this.numericCode = numericCode;
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode(){
            return code;
        }
        public String getDisplayName(){
            return displayName;
        }
    }

    public static void main(String[] args){
        System.out.println(ISO4217_Numeric.getCurrencyNameForCode(578));
        System.out.println(ISO4217_Numeric.getCurrencyNameForCode(955));
        System.out.println(ISO4217_Numeric.getCurrencyNameForCode(999));
        System.out.println(ISO4217_Numeric.getCurrencyNameForCode(998));
        System.out.println(ISO4217_Numeric.getCurrencyNameForCode(1000));
    }

}
