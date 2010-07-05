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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import sasc.util.Util;

/**
 * Track 2 Equivalent Data
 * A representation of the data that can be found on Track 2 on magnetic stripe cards
 * Contains the data elements of track 2 according to ISO/IEC 7813,
 * excluding start sentinel, end sentinel, and Longitudinal Redundancy Check (LRC)
 * @author sasc
 */
public class Track2EquivalentData {

    private PAN pan;
    private Date expirationDate; //n4
    private int serviceCode; //n3
    private String discretionaryData; //(defined by individual payment systems)

    public Track2EquivalentData(byte[] data) {
        if (data.length > 19) {
            throw new EMVException("Invalid Track2EquivalentData length: " + data.length);
        }
        String str = Util.byteArrayToHexString(data).toUpperCase();
        //Field Separator (Hex 'D')
        int fieldSepIndex = str.indexOf('D');
        pan = new PAN(str.substring(0, fieldSepIndex));
        //Skip Field Separator
        int YY = Util.numericHexToInt(str.substring(fieldSepIndex+1, fieldSepIndex+3));
        int MM = Util.numericHexToInt(str.substring(fieldSepIndex+3, fieldSepIndex+5));
        Calendar cal = Calendar.getInstance();
        cal.set(2000+YY, MM-1, 0, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        this.expirationDate = cal.getTime();
        serviceCode = Util.numericHexToInt(str.substring(fieldSepIndex+5, fieldSepIndex+8));
        int padIndex = str.indexOf('F', fieldSepIndex+8);
        if(padIndex != -1){
            //Padded with one Hex 'F' if needed to ensure whole bytes
            discretionaryData = str.substring(fieldSepIndex+8, padIndex);
        }else{
            discretionaryData = str.substring(fieldSepIndex+8);
        }
    }

    public Date getExpirationDate(){
        return (Date)expirationDate.clone();
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getEmptyString(indent) + "Track 2 Equivalent Data:");
        String indentStr = Util.getEmptyString(indent + 3);
        pan.dump(pw, indent + 3);
        pw.println(indentStr + "Expiration Date: " + expirationDate);
        pw.println(indentStr + "Service Code: " + serviceCode);
        pw.println(indentStr + "Discretionary Data: " + discretionaryData);
    }

    public static void main(String[] args) {
        Track2EquivalentData t2 = new Track2EquivalentData(Util.fromHexString("957852641234567890d120360112345678900f"));
        System.out.println(t2);
    }
}
