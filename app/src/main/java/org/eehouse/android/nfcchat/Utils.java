// -*- compile-command: find-and-gradle.sh inDeb; '*'

package org.eehouse.android.nfcchat;

import java.util.Arrays;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    private static final String HEX_CHARS = "0123456789ABCDEF";
    private static char[] HEX_CHARS_ARRAY = HEX_CHARS.toCharArray();

    private static byte[] hexStringToByteArrayImpl( String data )
    {
        byte[] result = new byte[data.length() / 2];

        for (int ii = 0; ii < data.length(); ii += 2 ) {
            int firstIndex = HEX_CHARS.indexOf(data.charAt(ii));
            int secondIndex = HEX_CHARS.indexOf(data.charAt(ii + 1));
            result[ii/2] = (byte)((firstIndex << 4) | secondIndex);
        }

        return result;
    }

    public static byte[] hexStringToByteArray( String data )
    {
        byte[] result = hexStringToByteArrayImpl( data );

        Assert.assertTrue( data.equals(byteArraytoHexStringImpl(result)) );
        return result;
    }

    public static String byteArraytoHexStringImpl( final byte[] data )
    {
        StringBuffer sb = new StringBuffer();

        for ( int ii = 0; ii < data.length; ++ii ) {
            byte octet = data[ii];
            sb.append(HEX_CHARS_ARRAY[(octet >> 4) & 0x0F]);
            sb.append(HEX_CHARS_ARRAY[octet & 0x0F]);
        }

        String result = sb.toString();
        return result;
    }

    public static String byteArraytoHexString( final byte[] data )
    {
        String result = byteArraytoHexStringImpl( data );

        byte[] tmp = hexStringToByteArrayImpl( result );
        Assert.assertTrue( Arrays.equals( hexStringToByteArrayImpl(result), data ) );
        return result;
    }
}
