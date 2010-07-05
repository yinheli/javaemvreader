/*   * Copyright 2010 sasc   
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

/**
 * Application Definition File (ADF)
 *
 * @author sasc
 */
public class ApplicationDefinitionFile implements File{// implements DF{
//    private Type type = Type.ADF;
    private byte[] name;
    
    public ApplicationDefinitionFile() {
//        type = Type.ADF;
    }
    
    public void setName(byte[] name) {
        this.name = name;
    }

    public byte[] getName() {
        return name;
    }

//    @Override
//    public Type getType() {
//        return type;
//    }

//    @Override
    public void dump(PrintWriter pw, int indent) {
        //TODO
    }

}
