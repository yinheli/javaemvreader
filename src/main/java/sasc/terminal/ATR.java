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
package sasc.terminal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import sasc.ATR_DB;
import sasc.emv.Log;
import sasc.util.Util;

/**
 * Answer To Reset (ATR)
 * @author sasc
 */
public class ATR {
    private byte[] atrBytes;
    private IsoATR isoATR = null;
    private boolean isIsoCompliant = false;

    public ATR(byte[] atrBytes){
        this.atrBytes = atrBytes;

        try{
            isoATR = IsoATR.parse(atrBytes);
            isIsoCompliant = true;
        }catch(IsoATR.ParseException ex){
            //Ignore
            Log.debug(ex.getMessage());
        }

    }

    public boolean isIsoCompliant(){
        return isIsoCompliant;
    }

    /**
     * Get the ISO compliant ATR
     * @return the ISO compliant ATR, or null if the ATR is not ISO compliant
     */
    public IsoATR getIsoCompliantATR(){
        return isoATR;
    }

    @Override
    public String toString(){
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent){
        pw.println(Util.getEmptyString(indent)+"Answer To Reset (ATR)");
        String indentStr = Util.getEmptyString(indent+3);
        List<String> descriptiveText = ATR_DB.searchATR(atrBytes);
        pw.println(indentStr+Util.prettyPrintHexNoWrap(atrBytes));
        if(descriptiveText != null){
            //Just use List/ArrayList.toString(), which prints [value1, value2] according to Javadoc API
            pw.println(indentStr+"Description From Public Database - "+descriptiveText);
        }

        if(isIsoCompliant()){
            isoATR.dump(pw, indent+3);
        }else{
            pw.println(indentStr+"ATR is not ISO compliant");
        }

    }

    public static void main(String[] args){
        byte[] atrBytes = new byte[]{(byte)0x3F, (byte)0x24, (byte)0x00, (byte)0x30, (byte)0x42, (byte)0x30, (byte)0x30};
        ATR atr = new ATR(atrBytes);
        System.out.println(atr.toString());
    }
}
