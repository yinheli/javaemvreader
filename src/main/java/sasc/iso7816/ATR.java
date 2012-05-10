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
package sasc.iso7816;

import sasc.iso7816.IsoATR;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import sasc.ATR_DB;
import sasc.util.Log;
import sasc.util.Util;

/**
 * Answer To Reset (ATR)
 * @author sasc
 */
public class ATR {
    private byte[] atrBytes;
    private IsoATR isoATR = null;
    private boolean isIsoCompliant = false;
    private String errorMsg = "";

    public ATR(byte[] atrBytes){
        this.atrBytes = atrBytes;

        try{
            isoATR = IsoATR.parse(atrBytes);
            isIsoCompliant = true;
        }catch(IsoATR.ParseException ex){
            errorMsg = ex.getMessage();
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
        pw.println(Util.getSpaces(indent)+"Answer To Reset (ATR)");
        String indentStr = Util.getSpaces(indent+3);
        List<String> descriptiveText = ATR_DB.searchATR(atrBytes);
        pw.println(indentStr+Util.prettyPrintHexNoWrap(atrBytes));
        if(descriptiveText != null){
            //Just use List/ArrayList.toString(), which prints [value1, value2] according to Javadoc API
            pw.println(indentStr+"Description From Public Database - "+descriptiveText);
        }

        if(isIsoCompliant()){
            isoATR.dump(pw, indent+3);
        }else{
            pw.println(indentStr+"ATR is not ISO compliant ("+errorMsg+")");
        }

    }

    public static void main(String[] args){
        System.out.println(new ATR(Util.fromHexString("3F 24 00 30 42 30 30")));
        System.out.println(new ATR(Util.fromHexString("3A 00"))); //Invalid convention
        System.out.println(new ATR(Util.fromHexString("3B 20"))); //Missing interface characters
        System.out.println(new ATR(Util.fromHexString("3B 34 00 00"))); //No historical bytes
        System.out.println(new ATR(Util.fromHexString("3B 80 01"))); //TCK not present
    }
}
