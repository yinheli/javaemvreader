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

import sasc.util.Util;

/**
 * EMV book 3 page 61
 *
 * @author sasc
 */
public enum SW {

    SUCCESS("9000", "Success"),
    SELECTED_FILE_INVALIDATED("6283", "State of non-volatile memory unchanged; selected file invalidated"),
    AUTHENTICATION_FAILED("6300", "State of non-volatile memory changed; authentication failed"),
    COMMAND_NOT_ALLOWED_AUTHENTICATION_METHOD_BLOCKED("6983", "Command not allowed; authentication method blocked"),
    COMMAND_NOT_ALLOWED_REFERENCE_DATA_INVALIDATED("6984", "Command not allowed; referenced data invalidated"),
    COMMAND_NOT_ALLOWED_CONDITIONS_OF_USE_NOT_SATISFIED("6985", "Command not allowed; conditions of use not satisfied"),
    WRONG_P1_P2_FUNCTION_NOT_SUPPORTED("6a81", "Wrong parameter(s) P1 P2; function not supported"),
    WRONG_P1_P2_FILE_NOT_FOUND("6a82", "Wrong parameter(s) P1 P2; file not found"),
    WRONG_P1_P2_RECORD_NOT_FOUND("6a83", "Wrong parameter(s) P1 P2; record not found"),
    REFERENCE_DATA_NOT_FOUND("6a88", "Referenced data (data objects) not found"),
    INSTRUCTION_CODE_NOT_SUPPORTED_OR_INVALID("6d00", "Instruction code not supported or invalid");
    private String swStr;
    private String msg;
    private byte sw1;
    private byte sw2;
    private short sw;

    private SW(String swStr, String message) {
        this.swStr = swStr;
        this.msg = message;
        this.sw1 = Util.fromHexString(swStr)[0];
        this.sw2 = Util.fromHexString(swStr)[1];
        this.sw = (short)(sw1 << 8 | sw2);
        //Due to specifications in ISO/IEC 7816-3, any value different from
        //'6XXX' and '9XXX' is invalid; any value '60XX' is also invalid
    }

    public byte getSW1() {
        return sw1;
    }

    public byte getSW2() {
        return sw2;
    }

    public short getSW(){
        return sw;
    }
}
