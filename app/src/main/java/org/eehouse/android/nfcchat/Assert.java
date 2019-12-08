/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2012 by Eric House (xwords@eehouse.org).  All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.android.nfcchat;

public class Assert {
    private static final String TAG = Assert.class.getSimpleName();

    public static void fail() {
        assertTrue(false);
    }

    public static void assertFalse(boolean val)
    {
        assertTrue(! val);
    }

    public static void assertTrue(boolean val) {
        if (BuildConfig.DEBUG && ! val) {
            Log.e( TAG, "firing assert!" );
            throw new RuntimeException();
        }
    }

    public static void assertNotNull( Object val )
    {
        assertTrue( val != null );
    }

    public static void assertNull( Object val )
    {
        assertTrue( val == null );
    }

    public static void assertEquals( Object obj1, Object obj2 )
    {
        assertTrue( (obj1 == null && obj2 == null)
                    || (obj1 != null && obj1.equals(obj2)) );
    }
}
