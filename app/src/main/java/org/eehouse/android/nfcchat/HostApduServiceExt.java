// -*- compile-command: find-and-gradle.sh inDeb '*'

package org.eehouse.android.nfcchat;

import android.app.Activity;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.HostApduService;
import android.nfc.tech.IsoDep;
import android.os.Bundle;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

    public static class Wrapper implements NfcAdapter.ReaderCallback {
        private Activity mActivity;
        private boolean mHaveData = false;
        private NfcAdapter mAdapter;
        private Procs mProcs;
        private final int mMinMS = 700;
        private final int mMaxMS = 300;
        private boolean mConnected = false;

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
        }

        public void setResumed( boolean resumed )
        {
            if ( resumed ) {
                startReadModeThread();
            } else {
                stopReadModeThread();
            }
        }

        public void setHaveData( boolean haveData )
        {
            if ( mHaveData != haveData ) {
                mHaveData = haveData;
                interruptThread();
            }
        }

        @Override
        public void onTagDiscovered( Tag tag )
        {
            Log.d( TAG, "onTagDiscovered()" );
            mConnected = true;
            Msg[] msgs = mProcs.getMsgs();
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
                    }
                }

                isoDep.close();
            } catch ( IOException ioe ) {
                Log.e( TAG, "got ioe: " + ioe.getMessage() );
            }

            mConnected = false;
            interruptThread();
            Log.d( TAG, "onTagDiscovered() DONE" );
        }

        private class ReadModeThread extends Thread {
            private boolean mShouldStop = false;
            private boolean mInReadMode = false;
            private final int mFlags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

            @Override
            public void run()
            {
                Log.d( TAG, "ReadModeThread.run() starting" );

                Random random = new Random();

                while ( !mShouldStop ) {
                    boolean wantReadMode = mConnected || !mInReadMode && mHaveData;
                    if ( wantReadMode && !mInReadMode ) {
                        mAdapter.enableReaderMode( mActivity, Wrapper.this, mFlags, null );
                        mProcs.onReadingChange( true );
                    } else if ( mInReadMode && !wantReadMode ) {
                        mAdapter.disableReaderMode( mActivity );
                        mProcs.onReadingChange( false );
                    }
                    mInReadMode = wantReadMode;
                    Log.d( TAG, "mInReadMode now: %b", mInReadMode );

                    // Now sleep. If we aren't going to want to toggle read
                    // mode soon, sleep until interrupted by a state change,
                    // e.g. getting data or losing connection.
                    long intervalMS = Long.MAX_VALUE;
                    if ( (mInReadMode && !mConnected) || mHaveData ) {
                        intervalMS = mMinMS + (Math.abs(random.nextInt())
                                               % (mMaxMS - mMinMS));
                    }
                    try {
                        Thread.sleep( intervalMS );
                    } catch ( InterruptedException ie ) {
                        Log.d( TAG, "run interrupted" );
                    }
                }

                // Kill read mode on the way out
                if ( mInReadMode ) {
                    mAdapter.disableReaderMode( mActivity );
                    mInReadMode = false;
                }

                // Clear the reference only if it's me
                synchronized ( mThreadRef ) {
                    if ( mThreadRef[0] == this ) {
                        mThreadRef[0] = null;
                    }
                }
                Log.d( TAG, "ReadModeThread.run() exiting" );
            }

            public void doStop()
            {
                mShouldStop = true;
                interrupt();
            }
        }

        private ReadModeThread[] mThreadRef = {null};

        private void interruptThread()
        {
            synchronized ( mThreadRef ) {
                if ( null != mThreadRef[0] ) {
                    mThreadRef[0].interrupt();
                }
            }
        }

        private void startReadModeThread()
        {
            synchronized ( mThreadRef ) {
                if ( null == mThreadRef[0] ) {
                    mThreadRef[0] = new ReadModeThread();
                    mThreadRef[0].start();
                }
            }
        }

        private void stopReadModeThread()
        {
            ReadModeThread thread;
            synchronized ( mThreadRef ) {
                thread = mThreadRef[0];
                mThreadRef[0] = null;
            }

            if ( null != thread ) {
                thread.doStop();
                try {
                    thread.join();
                } catch ( InterruptedException ex ) {
                    Log.d( TAG, "stopReadModeThread(): %s", ex );
                }
            }
        }
    }
}
