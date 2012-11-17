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
package sasc.emv.provider.mastercard;

import sasc.emv.EMVApplication;
import sasc.iso7816.Tag;
import sasc.iso7816.TagImpl;
import sasc.iso7816.TagValueType;

/**
 *
 * @author sasc
 */
public class MasterCardApplication extends EMVApplication {
    // Some tags from EMV Contactless Book C-2

    //MasterCard: 
    // Format Code:1, PAN:?-19, Field Separator: 1, Name:2-26, ExpiryDate: 4 (YYMM), Service Code: 3 (digits), Discretionary Data: var
    public static final Tag MC_TRACK1_DATA = new TagImpl("56", TagValueType.BINARY, "Track 1 Data", "Track 1 Data contains the data objects of the track 1 according to [ISO/IEC 7813] Structure B, excluding start sentinel, end sentinel and LRC.");
    public static final Tag MC_TRACK2_DATA = new TagImpl("9f6b", TagValueType.BINARY, "Track 2 Data", "Track 2 Data contains the data objects of the track 2 according to [ISO/IEC 7813] Structure B, excluding start sentinel, end sentinel and LRC.");

    public static final Tag MC_CVC3_TRACK1 = new TagImpl("9f60", TagValueType.BINARY, "CVC3 (Track1)", "The CVC3 (Track1) is a 2-byte cryptogram returned by the Card in the response to the COMPUTE CRYPTOGRAPHIC CHECKSUM command.");
    public static final Tag MC_CVC3_TRACK2 = new TagImpl("9f61", TagValueType.BINARY, "CVC3 (Track2)", "The CVC3 (Track2) is a 2-byte cryptogram returned by the Card in the response to the COMPUTE CRYPTOGRAPHIC CHECKSUM command.");

    public static final Tag MC_NATC_TRACK1 = new TagImpl("9f64", TagValueType.BINARY, "NATC(Track1)", "The value of NATC(Track1) represents the number of digits of the Application Transaction Counter to be included in the discretionary data field of Track 1 Data");
    public static final Tag MC_NATC_TRACK2 = new TagImpl("9f67", TagValueType.BINARY, "NATC(Track2)", "The value of NATC(Track2) represents the number of digits of the Application Transaction Counter to be included in the discretionary data field of Track 2 Data");

    public static final Tag MC_PCVC3_TRACK1 = new TagImpl("9f62", TagValueType.BINARY, "PCVC3(Track1)", "PCVC3(Track1) indicates to the Kernel the positions in the discretionary data field of the Track 1 Data where the CVC3 (Track1) digits must be copied");
    public static final Tag MC_PCVC_TRACK2 = new TagImpl("9f65", TagValueType.BINARY, "PCVC3(Track2)", "PCVC3(Track2) indicates to the Kernel the positions in the discretionary data field of the Track 2 Data where the CVC3 (Track2) digits must be copied");

    public static final Tag MC_PUNTAC_TRACK1 = new TagImpl("9f63", TagValueType.BINARY, "PUNTAC(Track1)", "PUNATC(Track1) indicates to the Kernel the positions in the discretionary data field of Track 1 Data where the Unpredictable Number (Numeric) digits and Application Transaction Counter digits have to be copied.");
    public static final Tag PUNTAC_TRACK2 = new TagImpl("9f66", TagValueType.BINARY, "PUNTAC(Track2)", "PUNATC(Track2) indicates to the Kernel the positions in the discretionary data field of Track 2 Data where the Unpredictable Number (Numeric) digits and Application Transaction Counter digits have to be copied.");
    public static final Tag UNKNOWN_MC_TAG = new TagImpl("9f6c", TagValueType.BINARY, "Unknown MC Tag", "?");
}
