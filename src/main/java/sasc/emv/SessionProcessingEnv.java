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

/**
 *
 * @author sasc
 */
public class SessionProcessingEnv {
    private boolean maskPersonalInformation = false;
    private boolean bruteForceSFIRecords = false;
    private boolean readMasterFile = false;
    private boolean warmUpCard = false;
    private int initialPauseMillis = 150;
    //TODO
    //-Try to SELECT known AIDs from a list (To find AIDs not listed in the PSE)
    //-etc
    
    public SessionProcessingEnv(){
        
    }
    
    public boolean getReadMasterFile(){
        return readMasterFile;
    }
    
    public void setReadMasterFile(boolean value){
        readMasterFile = value;
    }
    
    public boolean getWarmUpCard(){
        return warmUpCard;
    }
    
    public void setWarmUpCard(boolean value){
        warmUpCard = value;
    }

    public int getInitialPauseMillis(){
        return initialPauseMillis;
    }
    
    /**
     * Set the delay in milliseconds between PowerOn 
     * and the first command sent to the card
     * @param millis 
     */
    public void setInitialPauseMillis(int millis){
        this.initialPauseMillis = millis;
    }
}
