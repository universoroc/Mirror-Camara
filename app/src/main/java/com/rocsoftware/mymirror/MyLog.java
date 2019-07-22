package com.rocsoftware.mymirror;

import android.util.Log;

/**
 * Created by Rogelio on 20/10/2016.
 */

public class MyLog {
    public static final boolean DEBUG = true;


    public static void d(String TAG,String s)
    {
        if (DEBUG && TAG != null && s != null ) {

            Log.d(TAG, s);
        }
    }
    public static void e(String TAG,String s){
        if (DEBUG && TAG != null && s != null ) {
            Log.e(TAG, s);
        }
    }
    public static void i(String TAG,String s)
    {
        if (DEBUG && TAG != null && s != null ) {
            Log.i(TAG, s);
        }
    }
    public static void v(String TAG,String s){
        if (DEBUG && TAG != null && s != null ) {
            Log.v(TAG,s);
        }
    }
    public static void w(String TAG,String s){
        if (DEBUG && TAG != null && s != null ) {
            Log.w(TAG, s);
        }
    }

    /** Get the current line number.
     * @return int - Current line number. Must be used in the  calling class directly. join string s.
     */
    public static int getLineNumber() {
        return Thread.currentThread().getStackTrace()[2].getLineNumber();
    }
}
