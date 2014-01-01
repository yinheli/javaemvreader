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

import sasc.common.SessionProcessingEnv;
import sasc.util.Log;
import sasc.iso7816.AID;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import sasc.CardEmulator;
import sasc.terminal.CardConnection;
import sasc.util.Util;

import static org.junit.Assert.*;
import sasc.common.CardSession;
import sasc.common.SmartCard;

/**
 * This Test is only used to track changes in output from revision to revision.
 * (So that we are aware of the changes being made before committing)
 *
 * @author sasc
 */
public class SASCIntegrationTest {

    public SASCIntegrationTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of main method, of class CardEmulatorTestMain.
     */
    @Test
    public void testStringOutput() throws Exception {
        SmartCard smartCard = null;
        StringWriter dumpWriter = new StringWriter();
        Log.setPrintWriter(new PrintWriter(dumpWriter));

        sasc.common.Context.init();
        CA.initFromFile("/CertificationAuthorities_Test.xml");
        CardConnection term = new CardEmulator("/SDACardTransaction.xml");
        CardSession cardSession = CardSession.createSession(term, new SessionProcessingEnv());
        smartCard = cardSession.initCard();
        EMVSession session = EMVSession.startSession(smartCard, term);

        AID targetAID = new AID("a1 23 45 67 89 10 10"); //Our TEST AID

        
        session.initContext();
        for(EMVApplication app : smartCard.getApplications()){
            session.selectApplication(app);
            session.initiateApplicationProcessing();
            session.readAdditionalData();
            if (targetAID.equals(app.getAID())) {
                session.verifyPIN(new char[]{'1','2','3','4'}, true);
            }
//            session.getChallenge();
        }

        smartCard.dump(new PrintWriter(dumpWriter), 0);

//        System.out.println("dumpWriter: "+dumpWriter.toString());

        String expectedOutput = Util.readInputStreamToString(SASCIntegrationTest.class.getResourceAsStream("/output_svn_old_rev.txt"), "UTF-8").trim();

        String actualOutput = dumpWriter.toString().trim();

        StringTokenizer tokenizerOutputOlderSVNRev = new StringTokenizer(expectedOutput, "\n\r"+System.getProperty("line.separator"));
        StringTokenizer tokenizerCurrentOutput = new StringTokenizer(actualOutput, "\n\r"+System.getProperty("line.separator"));

        int lineNumber = 1;
        while(tokenizerOutputOlderSVNRev.hasMoreTokens()){
            String tokenExpected = tokenizerOutputOlderSVNRev.nextToken().trim();
            String tokenActual = tokenizerCurrentOutput.hasMoreTokens()?tokenizerCurrentOutput.nextToken().trim():"";

            if(!tokenExpected.equals(tokenActual)){
                System.err.println(actualOutput);
                System.err.println("Expected (length="+tokenExpected.length()+"): "+tokenExpected);
                System.err.println("Expected Hex: "+Util.prettyPrintHexNoWrap(tokenExpected.getBytes()));
                System.err.println("Actual   (length="+tokenActual.length()+"): "+tokenActual);
                System.err.println("Actual   Hex: "+Util.prettyPrintHexNoWrap(tokenActual.getBytes()));
                fail("Line "+lineNumber+" is not equal: Expected: "+tokenExpected + " Actual: "+tokenActual);
            }
            lineNumber++;
        }
    }
}