A tool to communicate with, and read data from an EMV smart card. Written in Java.
The primary purpose of this application is to read and parse all the openly accessible information (_**non-secure**_ data) on an EMV card. Support for transactions has not been implemented

---

![http://javaemvreader.googlecode.com/files/Chip-300x299.jpg](http://javaemvreader.googlecode.com/files/Chip-300x299.jpg)

## This project has now moved to GitHub: https://github.com/sasc999/javaemvreader ##

# New version uploaded 2014-04-16: #

# v0.6.0 #
### Improvements: ###
  * More refactoring work
  * prepareTransactionProcessing (Offline Data Authentication, Processing restrictions, Terminal Risk Management, etc) [experimental](experimental.md)
  * Added ability to specify a custom PIN callback handler
  * Parse ICCPinEnciphermentPublicKeyCertificate
  * Added MC and VISA proprietary tags
  * Added support for registering AtrHandlers and AidHandlers
  * Read data from Global Platform Security Domains
  * Read JCOP Identify Applet
  * Read (synchronous) storage cards (via PC/SC compliant readers)
  * Re-add CA test public keys
  * Use byte arrays instead of Strings when sending commands
  * Store application discretionary data
  * Differentiate between unprocessed records (known tag) and unknown records (unknown tag)
  * Experimental support for identifying context specific TLV tags based on IIN/RID
  * Automatically add Le if missing
  * Added support for Internal Auth response containing RESPONSE\_MESSAGE\_TEMPLATE\_2 (based on a patch by bgillis)
  * Get PC/SC error code description, if available
### Bugfixes: ###
  * Fixed bug in TerminalVerificationResults returning wrong array length, causing GPO to fail
  * Fixed bug in the handling of verifyPIN response (based on a patch by bgillis)

# v0.5.2 #
### Bugsfixes: ###
  * Temporarily disabled loading of CA test keys

# v0.5.1 #
### Improvements: ###
  * Added more CA test keys
  * Parse Service Code
  * Try to guess Terminal Country Code and Transaction Currency Code in GPO response
  * Handle Application Label Tag in P(P)SE DDF
  * Get PC/SC error message if available

# v0.5.0 #
  * Preliminary refactoring work
### Bugsfixes: ###
  * Fixed bit shift bug when parsing SFI in ApplicationElementaryFile
  * Fixed bit shift bug in Util.byte2Hex/short2Hex
  * Fixed bug in Util.byteArrayToInt
  * Fixed bug when reading tag id bytes
  * Gracefully handle the case when all terminals are removed when waiting for card insertion
  * Don't fail reading if CA public key is missing

### Improvements: ###
  * Use char array when passing PIN as argument
  * Added IBAN and BIC support (based on a patch by folkyvolk)
  * Parse IssuerCountryCodeAlpha3, IssuerIdentificationNumber and IssuerUrl

# v0.4.0 #
### Bugsfixes: ###
  * Always transmit Le
  * Fixed bug in parsing PPSE record (Thanks to N.Elenkov)
  * Don't crash if app processing fails. Just continue to process next app (Thanks to N.Elenkov)
  * Fixed bug when attempting to parse non-TLV data as TLV
  * Fixed bug in verifying the ICC Public Key Certificate (Thanks to B.Alecu)
  * Fixed error in CVRule text (Thanks to T.Crivat)
  * Fixed bug in partial selection (Thanks to T.Crivat)
  * Make sure Issuer PK is available before attempting to verify ICC PK Cert (Thanks to T.Crivat)
  * Correctly handle the case when no card readers are available [PC/SC Only](Thanks to F.Ferreira)

### Improvements: ###
  * PCSC class for building control commands
  * Set default delay after PowerOn to 150ms
  * Update feedback dialog: Unhandled record is not a bug.

# v0.3.0 #
### Bugsfixes: ###
  * Fixed a bug in internalAuthenticate

### Improvements: ###
  * Implemented transaction log reading and parsing (based on a patch by Thomas Souvignet -FR-)
  * Ludovic Rousseau' smart card list is now parsed as UTF-8
  * Experimental support for PPSE (Contactless EMV)
  * Terminal resident data can now be specified in an external properties file
  * Added logic to gracefully handle the case where no PCSC terminals are found
  * Added option to submit a bug report if the application terminates unexpectedly

# v0.2 #
(2012-02-25)
### Bugsfixes: ###
  * Fixed bug in Status Word byte to short conversion
  * Fixed bug in TLV length parsing when length was 0x80
  * Fixed bug when stripping leading and trailing 0x00/0xff from TLV data

### Improvements: ###
  * Implemented SignedDynamicApplicationData
  * Implemented track 2 service code parsing
  * Implemented Issuer Identifier Number (BIN) lookup
  * Separated generic iso7816 commands from emv specific commands

---

Supported commands/functions:

|**EMV function name**|**Command**|
|:--------------------|:----------|
|Initialize card                  |SELECT FILE "1PAY.SYS.DDF01" / "2PAY.SYS.DDF01"|
|                                |READ RECORD (to read all records in the specified SFI)|
|Application Selection           |SELECT|
|Initiate Application Processing |GET PROCESSING OPTIONS|
|                                |READ RECORD (all records listed in the AFL)|
|(Read other application data)	  |GET DATA (ATC, Last online ATC, PIN Try Counter, Log Format)|
|Dynamic Data Authentication     |INTERNAL AUTHENTICATE|
|Offline verification		  |VERIFY (only plaintext PIN verification is supported)|
|Read Transaction Log            |GET DATA/READ RECORD|

In addition to this, 'Brute force all SFI+records', 'SELECT Master File' and 'GET CHALLENGE' has been partially implemented


Try it out yourself:

<font color='RED'>
WARNING:I take NO RESPONSIBILITY WHAT SO EVER for what might happen to your card.<br>
</font>

(You need at a minimum Java 6 to run this)

NOTE! The WebStart link has been removed. Beginning with Java 7 update 51, self signed application are blocked by default.

You have to <a href='https://docs.google.com/uc?id=0B58aDiWVaiM3cjV5eVBfSTlGNlk&export=download'>download the jar-file</a> and either double-click the file, or run it on the command line like this:

`java -jar javaemvreader-latest-full.jar`

# If you want to discuss the project, please use the Forum link on the left #

If you want to help out and improve the code, then you are welcome to do so.

# I would also very much appreciate any donation to the project: #

[![](https://www.paypalobjects.com/en_US/GB/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=4EPEJG4JK25U8)


TODO:
  * Exception handling and usage should be reviewed.
  * Refactor generic smart card functions into reusable framework classes
  * Mastercard signed data currently not supported. Add CA Public Keys for Mastercard / Europay (others?)
  * Implement transaction processing