package com.xunlei.speedup.util;


import android.util.Log;

public class XLLOG {

    public static final boolean PRINT_LOG = true;
    
    public static void d(String TAG,String msg){
        if(PRINT_LOG){
            Log.d(TAG, msg);
        }
    }
    
    public static void w(String TAG,String msg){
        if(PRINT_LOG){
            Log.w(TAG, msg);
        }
    }
    
    public static void e(String TAG,String msg){
        if(PRINT_LOG){
            Log.e(TAG, msg);
        }
    }
    
    public static void i(String TAG,String msg){
        if(PRINT_LOG){
            Log.i(TAG, msg);
        }
    }
    
    public static void zxlog(String TAG,String msg){
//        if(PRINT_LOG){
//            Log.d(TAG, msg);
//        }
    }
}
