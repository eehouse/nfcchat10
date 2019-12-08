// -*- compile-command: find-and-gradle.sh inDeb; '*'

package org.eehouse.android.nfcchat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

class DBHelper extends SQLiteOpenHelper {
    private static final String TAG = DBHelper.class.getSimpleName();

    private static final String DB_NAME = "nfcchat_db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "msgs";

    public static enum MsgType { PENDING, SENT, RECEIVED, }
    
    public static class Msg {
        private String mText;
        private MsgType mTyp;
        private long mRowid = 0;

        private Msg( String msg, MsgType typ, long rowid ) {
            mText = msg; mTyp = typ; mRowid = rowid;
        }

        public String getText() { return mText; }
        public MsgType getType() { return mTyp; }
        public boolean isPending() { return mTyp == MsgType.PENDING; }
    }

    private static enum Cols {
        MSG("TEXT"), TYP("INTEGER(2)"), TIMESTAMP("DATETIME DEFAULT CURRENT_TIMESTAMP");

        private String mAttr;
        private Cols( String attr ) { mAttr = attr; }
        String attr() { return mAttr; }
    }

    public DBHelper( Context context )
    {
        super( context, DB_NAME, null, DB_VERSION );
    }

    @Override
    public void onCreate( SQLiteDatabase db )
    {
        StringBuilder query =
            new StringBuilder( String.format("CREATE TABLE %s (", TABLE_NAME ) );

        List<String> cols = new ArrayList<>();
        for ( Cols desc : Cols.values() ) {
            cols.add( String.format( "%s %s", desc.toString(), desc.attr() ) );
        }
        query.append( TextUtils.join( ",", cols ) );

        query.append( ");" );
        Log.d( TAG, "query: " + query.toString() );
        db.execSQL( query.toString() );
    }

    @Override
    public void onUpgrade( SQLiteDatabase db, int oldVersion, int newVersion )
    {
        db.execSQL( "DROP TABLE " + TABLE_NAME + ";" );
        onCreate( db );
    }

    private static void add( Context context, String msg, MsgType typ )
    {
        ContentValues values = new ContentValues();
        values.put( Cols.MSG.toString(), msg );
        values.put( Cols.TYP.toString(), typ.ordinal() );

        synchronized (DBHelper.class) {
            initDB( context ).insert( TABLE_NAME, null, values );
        }
    }

    static void addPending( Context context, String msg )
    {
        add( context, msg, MsgType.PENDING );
    }

    public static void addReceived( Context context, String msg )
    {
        add( context, msg, MsgType.RECEIVED );
    }

    public static void markSent( Context context, Msg msg )
    {
        Log.d( TAG, "need to mark with rowid: " + msg.mRowid );

        ContentValues values = new ContentValues();
        values.put( Cols.TYP.toString(), MsgType.SENT.ordinal() );

        String selection = "rowid=" + msg.mRowid;

        synchronized ( DBHelper.class ) {
            initDB(context).update( TABLE_NAME, values, selection, null );
        }
    }

    public static void clearAll( Context context )
    {
        synchronized ( DBHelper.class ) {
            initDB(context).delete( TABLE_NAME, null, null );
        }
    }

    private static List<Msg> getMessages( Context context, MsgType typ )
    {
        List<Msg> msgs = new ArrayList<>();

        // Msg msg = new Msg( "hello world", MsgType.PENDING );
        // msgs.add( msg );

        String[] columns = { Cols.MSG.toString(), Cols.TYP.toString(), "rowid" };
        String selection = null;
        if ( typ != null ) {
            selection = Cols.TYP + "=" + typ.ordinal();
        }
        String orderBy = "" + Cols.TIMESTAMP;

        synchronized (DBHelper.class) {
            SQLiteDatabase db = initDB( context );
            Cursor cursor = db.query( TABLE_NAME, columns, selection, null, null, null, orderBy );
            int msgIndex = cursor.getColumnIndex( Cols.MSG.toString() );
            int typIndex = cursor.getColumnIndex( Cols.TYP.toString() );
            int rowidIndex = cursor.getColumnIndex( "rowid" );
            while ( cursor.moveToNext() ) {
                String msg = cursor.getString( msgIndex );
                MsgType thisType = MsgType.values()[cursor.getInt( typIndex )];
                long rowid = cursor.getLong( rowidIndex );
                msgs.add( new Msg( msg, thisType, rowid ) );
            }
            cursor.close();
        }

        return msgs;
    }
    
    static List<Msg> getMessages( Context context )
    {
        return getMessages( context, null );
    }

    static List<Msg> getPendingMessages( Context context )
    {
        return getMessages( context, MsgType.PENDING );        
    }

    private static DBHelper sInstance;
    private synchronized static SQLiteDatabase initDB( Context context )
    {
        if ( null == sInstance ) {
            sInstance = new DBHelper( context );
            // force any upgrade
            sInstance.getWritableDatabase().close();
        }
        return sInstance.getWritableDatabase();
    }
}
