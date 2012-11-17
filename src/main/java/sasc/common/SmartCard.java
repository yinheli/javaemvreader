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
package sasc.common;

import sasc.iso7816.MasterFile;
import sasc.iso7816.BERTLV;
import sasc.iso7816.AID;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import sasc.iso7816.ATR;
import sasc.emv.DDF;
import sasc.emv.EMVApplication;
import sasc.util.Log;
import sasc.util.Util;

/**
 * A representation of the all the (EMV) applications (if any) on a smart card
 * 
 * @author sasc
 * 
 */
public class SmartCard {

    //TODO separate EMV and non-emv apps
    //private Map<AID, Application> otherApplicationsMap = new LinkedHashMap<AID, Application>();
    private Map<AID, EMVApplication> applicationsMap = new LinkedHashMap<AID, EMVApplication>();
    private Set<AID> otherAIDs = new LinkedHashSet<AID>();
    private EMVApplication selectedApp = null;
    private DDF pse = null;
    private List<BERTLV> unhandledRecords = new ArrayList<BERTLV>();

    private Set<ATR> atrSet = new LinkedHashSet<ATR>();
    private MasterFile mf = null;
    private Type type = Type.CONTACTED; //default
    
    //Use info from ATR, PSE/PPSE presence etc to determine interface type
    public enum Type{
        CONTACTED, CONTACTLESS;
    }
    
    public SmartCard(ATR atr) {
        if(atr == null){
            throw new IllegalArgumentException("Argument atr cannot be null");
        }
        atrSet.add(atr);
    }
    
    public void setType(Type type){
        this.type = type;
    }
    
    public Type getType(){
        return type;
    }
    
    /**
     * 
     * @return the final ATR (cold or warm)
     */
    public ATR getATR(){
        return atrSet.iterator().next();
    }
    
    /**
     * TODO
     * The first item is the cold ATR
     * Any subsequent ATRs (if present) are warm ATRs
     * @return Set of ATRs (cold and warm (if present))
     */
    public Set<ATR> getATRs(){
        return Collections.unmodifiableSet(atrSet);
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
        Log.debug("ADDING aid: "+Util.prettyPrintHexNoWrap(app.getAID().getAIDBytes()));
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

        for(ATR atr : atrSet){
            atr.dump(pw, indent);
        }
        
        pw.println("");
        
        pw.println(Util.getSpaces(indent+Log.INDENT_SIZE) + "Interface Type: "+type);
        
        if (mf != null) {
            mf.dump(pw, indent + Log.INDENT_SIZE);
        }

        pw.println("");

        if (pse != null) {
            pse.dump(pw, indent + Log.INDENT_SIZE);
        }

        if (!unhandledRecords.isEmpty()) {
            pw.println(Util.getSpaces(indent + Log.INDENT_SIZE) + "UNHANDLED GLOBAL RECORDS (" + unhandledRecords.size() + " found):");

            for (BERTLV tlv : unhandledRecords) {
                pw.println(Util.getSpaces(indent + Log.INDENT_SIZE*2) + tlv.getTag() + " " + tlv);
            }
        }
        pw.println("");

        pw.println("");
        pw.println(Util.getSpaces(indent + Log.INDENT_SIZE*2) + "Applications (" + getApplications().size() + " found):");
        pw.println("");
        for (EMVApplication app : getApplications()) {
            app.dump(pw, indent + Log.INDENT_SIZE*3);
        }

        pw.flush();
    }
}
