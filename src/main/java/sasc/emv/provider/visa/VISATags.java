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
package sasc.emv.provider.visa;

/**
 *
 * @author sasc
 */
public class VISATags {
    //    public static final Tag APPLICATION_DEFAULT_ACTION              = new TagImpl("9f52", TagValueType.BINARY, "Application Default Action (ADA)", "Visa proprietary data element indicating the action a card should take when exception conditions occur");
    //9f53 Consecutive Transaction Limit (International)
    //9f54 Cumulative Total Transaction Amount Limit
    //9f55 Geographic Indicator
    //9f56 Issuer Authentication Indicator
    //Tag[9f55] Name=[UNHANDLED TAG], TagType=PRIMITIVE, ValueType=BINARY, Class=CONTEXT_SPECIFIC BER-TLV[9f55, 01 (raw 01), 00]
    //Tag[9f56] Name=[UNHANDLED TAG], TagType=PRIMITIVE, ValueType=BINARY, Class=CONTEXT_SPECIFIC BER-TLV[9f56, 12 (raw 12), 00007fffffe0000000000000000000000000]
    //9f58 Lower Consecutive Offline Limit
    //9f59 Upper Consecutive Offline Limit
    //9f5c Cumulative Total Transaction Amount Upper Limit
    //9f72 Consecutive Transaction Limit (International--Country)
    //9f75 Cumulative Transaction Amount Limit--Dual Currency
    //9f77 VLP Funds Limit
    //9f78 VLP Single Transaction Limit
    //9f79 VLP Available Funds (Decremented during Card Action Analysis for offline approved VLP transactions)
    //9f7f Card Production Life Cycle (CPLC) History File Identifiers
    
    //9f80 Log Format TagAndLength found in GET DATA LOG FORMAT on VISA Electron card: Ex 9f8004 with Log value 03 60 60 00
}
