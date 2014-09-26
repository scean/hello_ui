package com.xunlei.speedup.util;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class Util {
    public static final boolean PRINT_LOG = true;// 控制崩溃时是否打印
    private static Toast mToast = null;

    public static boolean isNetworkAvailable(Context context) {
        Context ct = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) ct.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == cm) {
            return false;
        } else {
            NetworkInfo[] info = cm.getAllNetworkInfo();
            if (null != info) {
                for (int i = 0; i < info.length; i++) {
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int getScreenHeight(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        return (int) (dm.heightPixels);
    }

    /**
     * 获取具体的网络类型,返回值:none,wifi,2g_wap,2g_net,3g,4g,other *
     */
    public static String net_type = Constant.NETWORK_OTHER;
    public static boolean net_type_changed = true;

    public static String getNetworkType(final Context context) {
        if (!net_type_changed) {
            return net_type;
        }
        String res = Constant.NETWORK_NONE;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            // Log.e("NetworkInfo ",info.toString());
            // Log.e("NetWork Type Info : ",
            // "Type = "+info.getType()+" TypeName = "+info.getTypeName()+" SubType = "+info.getSubtype()+" SubtypeName = "+info.getSubtypeName()+" Extra = "+info.getExtraInfo());
            int type = info.getSubtype();
            int classType = getNetworkClass(type);
            // 判断Wifi网络
            if (info.getType() == 1) {
                return Constant.NETWORK_WIFI;
            } else {
                // if(info.getSubtypeName() != null &&
                // info.getSubtypeName().equalsIgnoreCase("GPRS")){
                // 判断2G网络
                if (classType == Constant.NETWORK_CLASS_2G) {
                    try {
                        String netMode = info.getExtraInfo();
                        if (netMode.equals("cmwap") || netMode.equals("3gwap") || netMode.equals("uniwap")) {
                            return Constant.NETWORK_2G_WAP;
                        }
                        final Cursor c = context.getContentResolver().query(Uri.parse("content://telephony/carriers/preferapn"), null, null, null, null);
                        String net_type = null;
                        if (c != null) {
                            c.moveToFirst();
                            net_type = c.getString(c.getColumnIndex("user"));
                            c.close();
                        }
                        if (!TextUtils.isEmpty(net_type)) {
                            if (net_type.startsWith("ctwap")) {
                                return Constant.NETWORK_2G_WAP;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Constant.NETWORK_2G_NET;
                    // 判断3G网络
                } else if (classType == Constant.NETWORK_CLASS_3G) {
                    return Constant.NETWORK_3G;
                    // 判断4G网络
                } else if (classType == Constant.NETWORK_CLASS_4G) {
                    return Constant.NETWORK_4G;
                } else {
                    return Constant.NETWORK_OTHER;
                }
            }
        }
        net_type_changed = false;
        net_type = res;
        return net_type;
    }

    private static int getNetworkClass(int networkType) {
        switch (networkType) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
        case TelephonyManager.NETWORK_TYPE_EDGE:
        case TelephonyManager.NETWORK_TYPE_CDMA:
        case TelephonyManager.NETWORK_TYPE_1xRTT:
        case TelephonyManager.NETWORK_TYPE_IDEN:
            return Constant.NETWORK_CLASS_2G;
        case TelephonyManager.NETWORK_TYPE_UMTS:
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
        case TelephonyManager.NETWORK_TYPE_HSDPA:
        case TelephonyManager.NETWORK_TYPE_HSUPA:
        case TelephonyManager.NETWORK_TYPE_HSPA:
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
        case TelephonyManager.NETWORK_TYPE_EHRPD:
        case 15:// TelephonyManager.NETWORK_TYPE_HSPAP = 15:
            return Constant.NETWORK_CLASS_3G;
        case TelephonyManager.NETWORK_TYPE_LTE:
            return Constant.NETWORK_CLASS_4G;
        default:
            return Constant.NETWORK_CLASS_UNKNOWN;
        }
    }

    public static boolean isSDCardExist() {
        return Environment.MEDIA_MOUNTED.equalsIgnoreCase(Environment.getExternalStorageState());
    }

    static String gPeerId = null;

    public static String getPeerId(Context context) {
        if (null == gPeerId) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            gPeerId = tm.getDeviceId();
            if (null == gPeerId || gPeerId.length() < 2) {
                gPeerId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
            }
        }
        return gPeerId;
    }
    
    public static String md5(String key) {
        try {
            char[] hex = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
            MessageDigest md;
            md = MessageDigest.getInstance("MD5");
            byte[] buf = key.getBytes();
            md.update(buf, 0, buf.length);
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                sb.append(hex[((b >> 4) & 0xF)]).append(hex[((b >> 0) & 0xF)]);
            }
            key = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return key;
    }
}
