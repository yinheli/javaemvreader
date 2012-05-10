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

import sasc.iso7816.MasterFile;
import sasc.iso7816.BERTLV;
import sasc.iso7816.AID;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import sasc.iso7816.ATR;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sasc.util.Util;

/**
 * A representation of an EMV smart card
 * 
 * @author sasc
 */
public class EMVCard {

    private Map<AID, EMVApplication> applicationsMap = new LinkedHashMap<AID, EMVApplication>();
    private EMVApplication selectedApp = null;
    private ATR atr = null;
    private DDF pse = null;
    private MasterFile mf = null;
    private List<BERTLV> unhandledRecords = new ArrayList<BERTLV>();
    private Type type = Type.CONTACTED;
    
    public enum Type{
        CONTACTED, CONTACTLESS;
    }

    public EMVCard(ATR atr) {
        this.atr = atr;
    }
    
    public void setType(Type type){
        this.type = type;
    }

    public void setMasterFile(MasterFile mf) {
        this.mf = mf;
    }
    
    public MasterFile getMasterFile() {
        return this.mf;
    }

    public void addApplication(EMVApplication app) {
//        if (applicationsMap.containsKey(app.getAID())) {
//            throw new IllegalArgumentException("EMVApplication already added: " + app.getAID() + " " + app.getPreferredName());
//        }
        if(app.getAID() == null){
            throw new IllegalArgumentException("Invalid Application object: AID == null");
        }
        applicationsMap.put(app.getAID(), app);
    }

    public EMVApplication getSelectedApplication() {
        return selectedApp;
    }

    public void setSelectedApplication(EMVApplication app) {
        this.selectedApp = app;
    }

    public EMVApplication getApplication(AID aid) {
        return selectedApp;
    }

    public Collection<EMVApplication> getApplications() {
        return Collections.unmodifiableCollection(applicationsMap.values());
    }

    public void setPSE(DDF pse) {
        this.pse = pse;
    }

    public DDF getPSE() {
        return pse;
    }

    public void addUnhandledRecord(BERTLV bertlv) {
        unhandledRecords.add(bertlv);
    }

    public List<BERTLV> getUnhandledRecords() {
        return Collections.unmodifiableList(unhandledRecords);
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), 0);
        return sw.toString();
    }

    //Dump all information read from card
    public void dump(PrintWriter pw, int indent) {
        pw.println(Util.getSpaces(indent) + "======================================");
        pw.println(Util.getSpaces(indent) + "               [EMVCard]              ");
        pw.println(Util.getSpaces(indent) + "======================================");
        atr.dump(pw, indent);
        
        pw.println("");
        
        pw.println(Util.getSpaces(indent+3) + "Interface Type: "+type);

        if (mf != null) {
            mf.dump(pw, indent + 3);
        }

        pw.println("");
        if (pse != null) {
            pse.dump(pw, indent + 3);
        }

        if (!unhandledRecords.isEmpty()) {
            pw.println(Util.getSpaces(indent + 3) + "UNHANDLED GLOBAL RECORDS (" + unhandledRecords.size() + " found):");

            for (BERTLV tlv : unhandledRecords) {
                pw.println(Util.getSpaces(indent + 6) + tlv.getTag() + " " + tlv);
            }
        }
        pw.println("");

        pw.println("");
        pw.println(Util.getSpaces(indent + 6) + "Applications (" + getApplications().size() + " found):");
        pw.println("");
        for (EMVApplication app : getApplications()) {
            app.dump(pw, indent + 9);
        }

        pw.println("---------------------------------------");
        pw.println("                FINISHED               ");
        pw.println("---------------------------------------");
        pw.flush();
    }
}
