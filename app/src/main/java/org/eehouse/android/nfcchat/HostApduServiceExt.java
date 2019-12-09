// -*- compile-command: find-and-gradle.sh inDeb '*'

package org.eehouse.android.nfcchat;

import android.os.Handler;
import android.app.Activity;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.HostApduService;
import android.nfc.tech.IsoDep;
import android.nfc.tech.IsoDep;
import android.os.Bundle;

import java.util.Random;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HostApduServiceExt extends HostApduService {
    private static final String TAG = HostApduServiceExt.class.getSimpleName();

    private static final int MIN_APDU_LENGTH = 12;
    private static final String DEFAULT_CLA = "00";
    private static final String SELECT_INS = "A4";
    private static final String STATUS_FAILED = "6F00";
    private static final String CLA_NOT_SUPPORTED = "6E00";
    private static final String INS_NOT_SUPPORTED = "6D00";
    private static final String STATUS_SUCCESS = "9000";
    private static final String STATUS_NOMSGS = "9001";
    
    private static final byte CMD_MSG = 0x70;
    private static final byte CMD_MSG_REQ = 0x71;

    private boolean mInConversation = false;
    private int mReceivedCount = 0;

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.d( TAG, "onCreate()" );
    }

    @Override
    public byte[] processCommandApdu( byte[] apdu, Bundle extras ) {
        Log.d( TAG, "processCommandApdu(" + Utils.byteArraytoHexString(apdu) + ", " + extras + ")" );

        String resStr = STATUS_FAILED;
        byte[] result = null;
        if ( null != apdu ) {
            if ( mInConversation ) {
                result = handleCommand( this, apdu );
            } else {
                String cmdStr = Utils.byteArraytoHexString( apdu );
                if ( cmdStr.length() >= MIN_APDU_LENGTH ) {
                    if (! cmdStr.substring(0, 2).equals( DEFAULT_CLA ) ) {
                        resStr = CLA_NOT_SUPPORTED;
                    } else if ( ! cmdStr.substring(2, 4).equals( SELECT_INS ) ) {
                        resStr = INS_NOT_SUPPORTED;
                    } else {
                        String fromCmd = cmdStr.substring( 10, 10 + BuildConfig.AID.length() );
                        if ( fromCmd.equals( BuildConfig.AID ) ) {
                            resStr = STATUS_SUCCESS;
                            mInConversation = true;
                        } else {
                            Log.d( TAG, "aid mismatch: got " + fromCmd + " but wanted " + BuildConfig.AID );
                        }
                    }
                }
            }
        }
        if ( result == null ) {
            result = Utils.hexStringToByteArray( resStr );
        }
        Log.d( TAG, "processCommandApdu() => " + result + "("
               + Utils.byteArraytoHexString(result) + ")" );
        return result;
    }

    @Override
    public void onDeactivated( int reason )
    {
        Log.d( TAG, "onDeactivated(reason=" + reason + ")" );
        mInConversation = false;
        if ( mReceivedCount > 0 ) {
            MainActivity.postNotification( this );
        }
        mReceivedCount = 0;
    }

    private byte[] handleCommand( Context context, byte[] apdu )
    {
        String resStr = STATUS_FAILED;
        byte[] result = null;
        if ( apdu.length >= 1 ) {
            byte cmd = apdu[0];
            switch ( cmd ) {
            case CMD_MSG:
                byte[] rest = Arrays.copyOfRange( apdu, 1, apdu.length );
                String msgTxt = new String( rest );
                Log.d( TAG, "got msg: " + msgTxt );
                DBHelper.addReceived( context, msgTxt );
                ++mReceivedCount;
                resStr = STATUS_SUCCESS;
                break;
            // case CMD_MSG_REQ:
            //     Log.d( TAG, "CMD_MSG_REQ case" );
            //     List<DBHelper.Msg> msgs = DBHelper.getPendingMessages( context );
            //     if ( msgs.size() == 0 ) {
            //         resStr = STATUS_NOMSGS;
            //         Log.d( TAG, "no messages: STATUS_NOMSGS" );
            //     } else {
            //         DBHelper.Msg msg = msgs.get(0);
            //         result = wrapMsg( msg.getText() );
            //         Log.d( TAG, "wrapping message" );

            //         // This is very bad! There needs to be something sent back
            //         // from the remote to confirm receipt before we can nuke
            //         // the thing in the DB. PENDING
            //         DBHelper.markSent( context, msg );
            //     }
                // break;
            default:
                Assert.fail();
            }
        }

        if ( result == null ) {
            result = Utils.hexStringToByteArray( resStr );
        }

        Log.d( TAG, "handleCommand() => " + result );
        return result;
    }

    public static byte[] wrapMsg( byte[] strBytes )
    {
        byte[] result = new byte[strBytes.length + 1];
        result[0] = CMD_MSG;
        System.arraycopy( strBytes, 0, result, 1, strBytes.length );
        return result;
    }

    private static boolean responseIsGood( byte[] response )
    {
        boolean result = Arrays.equals( response, Utils.hexStringToByteArray(STATUS_SUCCESS) );
        Log.d( TAG, "responseIsGood(" + Utils.byteArraytoHexString(response) + ") => " + result );
        return result;
    }

    // private static void requestMessages( Context context, IsoDep isoDep )
    // {
    //     Log.d( TAG, "requestMessages()" );
    //     byte[] req = { CMD_MSG_REQ };
    //     for ( ; ; ) {
    //         try {
    //             byte[] response = isoDep.transceive( req );
    //             Log.d( TAG, "transceive() => " + response[0] + "..." );
    //             if ( STATUS_NOMSGS.equals( Utils.byteArraytoHexString(response) ) ) {
    //                 break;
    //             }
    //             response = handleCommand( context, response );
    //             if ( ! responseIsGood( response ) ) {
    //                 break;
    //             }
    //         } catch ( IOException ioe ) {
    //             Log.e( TAG, "got ioe: " + ioe.getMessage() );
    //             break;
    //         }
    //     }
    // }

    public static class Wrapper implements NfcAdapter.ReaderCallback, Runnable {
        private Activity mActivity;
        private boolean mResumed = false;
        private boolean mHaveData = false;
        private boolean mInReadSlot = false;
        private NfcAdapter mAdapter;
        private Procs mProcs;
        private Handler mHandler;
        private Random mRandom;
        private int mMinMS = 700;
        private int mMaxMS = 300;

        public interface Msg {
            public byte[] getPayload();
        }

        public interface Procs {
            void onReadingChange( boolean nowReading );
            Msg[] getMsgs();
            void onMsgAcked( Msg msg );
        }

        public Wrapper( Activity activity, Procs procs )
        {
            mActivity = activity;
            mProcs = procs;
            mAdapter = NfcAdapter.getDefaultAdapter( activity );
            mHandler = new Handler();
            mRandom = new Random();
            scheduleToggle();
        }

        public void setResumed( boolean resumed )
        {
            if ( mResumed != resumed ) {
                mResumed = resumed;
                disEnableReaderMode();
            }
        }

        public void setHaveData( boolean haveData )
        {
            if ( mHaveData != haveData ) {
                mHaveData = haveData;
                disEnableReaderMode();
            }
        }

        @Override
        // To be called to toggle our being on/off as part of rendezvous
        // algorithm
        public void run()
        {
             if ( mResumed ) {
                mInReadSlot = !mInReadSlot;
                disEnableReaderMode();
            }
            scheduleToggle();
        }

        @Override
        public void onTagDiscovered( Tag tag )
        {
            Log.d( TAG, "onTagDiscovered()" );
            Msg[] msgs = mProcs.getMsgs();
            // List<DBHelper.Msg> msgs = DBHelper.getPendingMessages( this );
            IsoDep isoDep = IsoDep.get( tag );
            try {
                isoDep.connect();
                Log.d( TAG, "onTagDiscovered(): connected " + isoDep );
                Assert.assertTrue( 6 == (BuildConfig.AID.length() / 2) );
                // The '06' below gives the length
                String aidCmd = "00A4040006" + BuildConfig.AID;
                byte[] response = isoDep.transceive( Utils.hexStringToByteArray(aidCmd) );
                Log.d( TAG, "response 1: " + Utils.byteArraytoHexString(response) );

                if ( responseIsGood( response ) ) {
                    for ( Msg msg : msgs ) {
                        byte[] out = wrapMsg( msg.getPayload() );
                        response = isoDep.transceive( out );
                        if ( ! responseIsGood( response ) ) {
                            break;
                        }
                        mProcs.onMsgAcked( msg );
                        // DBHelper.markSent( this, msg );
                    }
                }

                // requestMessages( mActivity, isoDep );

                isoDep.close();

            //     // rebuildList();
            } catch ( IOException ioe ) {
                Log.e( TAG, "got ioe: " + ioe.getMessage() );
            }
            Log.d( TAG, "onTagDiscovered() DONE" );
        }

        private void disEnableReaderMode()
        {
            if ( mAdapter != null ) {
                // Log.d( TAG, "mResumed: " + mResumed
                //        + "; mHaveData: " + mHaveData
                //        + "; mInReadSlot: " + mInReadSlot );
                boolean reading = mResumed && mHaveData && mInReadSlot;
                if ( reading ) {
                    int flags = NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
                    mAdapter.enableReaderMode( mActivity, this, flags, null );
                } else {
                    mAdapter.disableReaderMode( mActivity );
                }
                mProcs.onReadingChange( reading );
            }
        }

        private void scheduleToggle()
        {
            int intervalMS = mMinMS + mRandom.nextInt() % (mMaxMS - mMinMS);
            mHandler.postDelayed( this, intervalMS );
        }
    }
}
