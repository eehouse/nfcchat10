// -*- compile-command: find-and-gradle.sh inDeb; '*'

package org.eehouse.android.nfcchat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity
    implements View.OnClickListener,
               HostApduServiceExt.Wrapper.Procs {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int[] sButtons = { R.id.make,
                                            R.id.send,
                                            R.id.clear,
                                            R.id.clearall,
    };
    private static MainActivity sSelf = null;

    private EditText mEdit;
    private TextView mReadingStatus;
    private HostApduServiceExt.Wrapper mWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        Log.d( TAG, "onCreate()" );
        setContentView(R.layout.activity_main);

        mWrapper = new HostApduServiceExt.Wrapper( this, this );

        mEdit = (EditText)findViewById( R.id.edit );
        mReadingStatus = (TextView)findViewById( R.id.send_status );

        for ( int resid : sButtons ) {
            ((Button)findViewById( resid )).setOnClickListener( this );
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        sSelf = this;
        mWrapper.setResumed( true );
        rebuildList();          // wasteful. Sue me.
    }

    @Override
    public void onPause()
    {
        super.onPause();
        sSelf = null;
        mWrapper.setResumed( false );
    }

    @Override
    public void onClick( View view )
    {
        switch ( view.getId() ) {
        case R.id.clearall:
            clearAll();
            break;
        case R.id.make:
            fillMsgArea();
            break;
        case R.id.clear:
            clearMsg();
            break;
        case R.id.send:
            sendMsg();
            break;
        default:
            Assert.fail();
        }
    }

    public static class LocalMsg implements HostApduServiceExt.Wrapper.Msg {
        private DBHelper.Msg mMsg;

        LocalMsg( DBHelper.Msg dbmsg ) { mMsg = dbmsg; }

        DBHelper.Msg getMsg() { return mMsg; }
        
        @Override
        public byte[] getPayload()
        {
            return mMsg.getText().getBytes();
        }
    }
    
    @Override
    public void onReadingChange( boolean nowReading )
    {
        Log.d( TAG, "onReadingChange(nowReading=" + nowReading + ")" );
        String txt = getString( nowReading ? R.string.status_send : R.string.status_receive );
        mReadingStatus.setText( txt );
    }

    @Override
    public HostApduServiceExt.Wrapper.Msg[] getMsgs()
    {
        List<DBHelper.Msg> msgs = DBHelper.getPendingMessages( this );
        HostApduServiceExt.Wrapper.Msg[] result = new HostApduServiceExt.Wrapper.Msg[msgs.size()];
        int ii = 0;
        for ( DBHelper.Msg msg : msgs ) {
            result[ii++] = new LocalMsg( msg );
        }
        return result;
    }

    @Override
    public void onMsgAcked( HostApduServiceExt.Wrapper.Msg msg )
    {
        LocalMsg lm = (LocalMsg)msg;
        DBHelper.markSent( this, lm.getMsg() );
        rebuildList();
    }

    // Post a notification that will launch me. Unless I'm already running.
    static void postNotification( Context context )
    {
        MainActivity self = sSelf;
        if ( self != null ) {
            self.rebuildList();
        } else {
            Intent intent = new Intent( context, MainActivity.class )
                .setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP
                           | Intent.FLAG_ACTIVITY_SINGLE_TOP )
                ;
            PendingIntent pi = PendingIntent
                .getActivity( context, 3492, intent, PendingIntent.FLAG_ONE_SHOT );
            String channelID = makeChannelID( context );
            Notification notification =
                new NotificationCompat.Builder( context, channelID )
                .setContentIntent( pi )
                .setSmallIcon( R.mipmap.ic_launcher_round )
                .setContentText( context.getString( R.string.notify_body ) )
                .setAutoCancel( true )
                .build();

            NotificationManager nm = (NotificationManager)
                context.getSystemService( Context.NOTIFICATION_SERVICE );
            nm.notify( 1589, notification );
        }
    }

    private static String sChannelID = null;
    private static String makeChannelID( Context context )
    {
        if ( sChannelID == null ) {
            String name = "default";
            if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
                NotificationManager notMgr = (NotificationManager)
                    context.getSystemService( Context.NOTIFICATION_SERVICE );

                NotificationChannel channel = notMgr.getNotificationChannel( name );
                if ( channel == null ) {
                    String channelDescription = context.getString( R.string.notify_channel_desc );
                    channel = new NotificationChannel( name, channelDescription,
                                                       NotificationManager.IMPORTANCE_LOW );
                    channel.enableVibration( true );
                    notMgr.createNotificationChannel( channel );
                }
            }
            sChannelID = name;
        }
        return sChannelID;
    }

    private void fillMsgArea()
    {
        String date = new Date().toString();
        String newMsg = getString( R.string.msg_template, date );
        mEdit.setText( newMsg );
    }

    private void clearMsg()
    {
        mEdit.setText( "" );
    }

    private void sendMsg()
    {
        String msg = mEdit.getText().toString();
        if ( msg.length() > 0 ) {
            clearMsg();
            DBHelper.addPending( this, msg );
            rebuildList();
        }
    }

    private void clearAll()
    {
        DBHelper.clearAll( this );
        rebuildList();
    }

    private void rebuildList()
    {
        runOnUiThread( new Runnable() {
                @Override
                public void run() {

                    TableLayout layout = (TableLayout)findViewById( R.id.msg_list );
                    layout.removeAllViews();

                    List<DBHelper.Msg> msgs = DBHelper.getMessages( MainActivity.this );
                    Log.d( TAG, "rebuildList(): got " + msgs.size() + " messages" );
                    LayoutInflater factory = LayoutInflater.from( MainActivity.this );
                    boolean haveToSend = false;

                    for ( DBHelper.Msg msg : msgs ) {
                        TextView tv = (TextView)factory.inflate( R.layout.list_item, null );
                        String str = msg.getText();
                        switch( msg.getType() ) {
                        case PENDING:
                            tv.setBackgroundColor( Color.RED );
                            haveToSend = true;
                            break;
                        case SENT:
                            str = getString( R.string.prefix_sent, str );
                            break;
                        case RECEIVED:
                            str = getString( R.string.prefix_received, str );
                            break;
                        }
                        tv.setText( str );
                        layout.addView( tv );
                    }

                    mWrapper.setHaveData( haveToSend );
                }
            } );
    }
}
