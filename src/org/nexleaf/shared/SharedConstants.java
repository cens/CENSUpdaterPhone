package org.nexleaf.shared;


/**
 * Encapsulates constants shared between applications.
 * Currently, this package is synchronized between applications.
 * By copying it from the master copy in rebooter/phoneman app.
 *
 */
public class SharedConstants {

    public static final String ACTION_SAMPLE_COUNT = "org.nexleaf.temptrace.SAMPLE_COUNT";
    public static final String ACTION_SMS_GATEWAY  = "org.nexleaf.temptrace.SMS_GATEWAY";
    
    // actions for global Sender service
    /**
     * Send an GPRS when signal available.
     */
    public static final String ACTION_SEND_GPRS    = "org.nexleaf.shared.SEND_GPRS";
    /**
     * Send an SMS when signal available.
     */
    public static final String ACTION_SEND_SMS     = "org.nexleaf.shared.SEND_SMS";
    /** 
     * Enable radio
     */
    public static final String ACTION_RADIO_ON = "org.nexleaf.shared.RADIO_ON";
    /** 
     * Disable radio
     */
    public static final String ACTION_RADIO_OFF = "org.nexleaf.shared.RADIO_OFF";
    
    /**
     * Time in millis to wait for airplane mode on to come on.
     * In test mode, use 5000 (5s).
     * In production, use 60000 (60s)
     */
    //public static final long DEFAULT_WAIT_RADIO_ON = 5000;
    //public static final long DEFAULT_WAIT_RADIO_ON = 15000;
    //public static final long DEFAULT_WAIT_RADIO_ON = 35000;
    public static final long DEFAULT_WAIT_RADIO_ON = 60000;
    
    /**
     * Time in millis an Alarm will be triggered to turn radio off again
     * after radio has been turned on.
     * In test mode, use 1*60*1000 (1m).
     * In production, use 2 to 5 minutes
     * 5*60*1000 (5m)
     * 
     */
    
    //public static final long DEFAULT_RADIO_OFF_DELAY = 1*60*1000;
    //public static final long DEFAULT_RADIO_OFF_DELAY = 2*60*1000;    
    public static final long DEFAULT_RADIO_OFF_DELAY = 3*60*1000;    
  

}
