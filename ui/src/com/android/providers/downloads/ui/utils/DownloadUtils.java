package com.android.providers.downloads.ui.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Downloads;
import android.provider.Settings;
import android.util.Log;
import android.accounts.Account;
import android.app.DownloadManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

import com.android.providers.downloads.ui.R;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.app.AppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map;
import java.math.BigDecimal;

import miui.analytics.Analytics;
import miui.accounts.ExtraAccountManager;
import miui.provider.ExtraSettings;

public class DownloadUtils {
    /** track event id for triger xunlei usage switch */
    private static final String TRACK_EVENT_XL_SWITCH = "xunlei_usage_switch";
    /** whether xunlei switch is on in ui */
    private static final String TRACK_ID_XL_SWITCH_TRIGER = "xunlei_usage_switch_triger";
    private static final String LOG_TAG = "com.android.providers.downloads.ui.DownloadUtils";
	public static final String PRODUCT_VERSION="1.0.0.1";
	public static final boolean analyticsMark=false;
    // ��ƷҪ��λ��ʹ��һ���ֽ�
	
    public static final int DOWNLOADLIST_ONLINE_EVENT = 10000;
    public static final int SETTING_ONLINE_EVENT = 10003;
    public static final int SPEEDUP_ONLINE_EVENT = 10004;
    
    public static final String PREF_NAME = "download_pref";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
    public static final String DOWNLOADPROVIDER_PKG_NAME = "com.android.providers.downloads";
    private static final String STAT_TAG_ONLINESTATUS = "DownloadUtils.Stat.OnLineStatus";
    public static final String PRODUCT_NAME = "MIUI V6 Download";
    private static final boolean STAT_LOG_ON = false;
    private static final String STAT_TAG_BEHAVIOR = "DownloadUtils.Stat.Behavior";
    
    private static final long BASE_B = 1L; // ת��Ϊ�ֽڻ���
    private static final long BASE_KB = 1024; // ת��ΪKB
    private static final long BASE_MB = 1024 * 1024; // ת��ΪM�Ļ���
    private static final long BASE_GB = 1024 * 1024 * 1024;
    private static final long BASE_TB = 1024L * 1024L * 1024L * 1024L;
    public static final String UNIT_BIT = "B";
    public static final String UNIT_KB = "KB";
    public static final String UNIT_MB = "MB";
    public static final String UNIT_GB = "GB";
    public static final String UNIT_TB = "TB";
    public static String convertFileSize(long file_size, int precision) {
        long int_part = 0;
        double fileSize = file_size;
        double floatSize = 0L;
        long temp = file_size;
        int i = 0;
        long base = 1L;
        String baseUnit = "";
        String fileSizeStr = null;
        int indexMid = 0;

        while (temp / 1024 > 0) {
            int_part = temp / 1024;
            temp = int_part;
            i++;
            if (i == 4){
                break;
            }
        }

        switch (i) {
        case 0:    // B
            base = BASE_B;
            baseUnit = UNIT_BIT;
            break;

        case 1:    // KB
            base = BASE_KB;
            baseUnit = UNIT_KB;
            break;

        case 2:    // MB
            base = BASE_MB;
            baseUnit = UNIT_MB;
            break;

        case 3:    // GB
            base = BASE_GB;
            baseUnit = UNIT_GB;
            break;

        case 4:    // TB
            base = BASE_TB;
            baseUnit = UNIT_TB;
            break;
        default:
            break;
        }
        BigDecimal filesizeDecimal = new BigDecimal(fileSize);
        BigDecimal baseDecimal = new BigDecimal(base);
        floatSize = filesizeDecimal.divide(baseDecimal, precision, BigDecimal.ROUND_HALF_UP).doubleValue();
        fileSizeStr = Double.toString(floatSize);
        if (precision == 0) {
            indexMid = fileSizeStr.indexOf('.');
            if (-1 == indexMid) {    // �ַ�����û���������ַ�
                return fileSizeStr + baseUnit;
            }
            return fileSizeStr.substring(0, indexMid) + baseUnit;
        }

        // baseUnit = UNIT_BIT;
        if (baseUnit.equals(UNIT_BIT)) {
            int pos = fileSizeStr.indexOf('.');
            fileSizeStr = fileSizeStr.substring(0, pos);
        }

        if (baseUnit.equals(UNIT_KB)) {
            int pos = fileSizeStr.indexOf('.');
            if (pos != -1) {
                fileSizeStr = fileSizeStr.substring(0, pos + 2);
            } else {
                fileSizeStr = fileSizeStr + ".0";
            }
        }

        return fileSizeStr + baseUnit;
    }

    public static String getAppLabel(Context c, String packageName) {
        PackageManager pm = c.getPackageManager();
        String appLabel = packageName;
        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
            appLabel = pm.getApplicationLabel(appInfo).toString();
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "error", e);
        }
        return appLabel;
    }

    /**
     * Returns whether the Wifi network is available
     */
    public static boolean isWifiAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            Log.w(LOG_TAG, "couldn't get connectivity manager");
        } else {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (int i = 0; i < info.length; i++) {
                    if (!connectivity.isActiveNetworkMetered()
                          && info[i].getState() == NetworkInfo.State.CONNECTED)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the network is available
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            Log.w(LOG_TAG, "couldn't get connectivity manager");
        } else {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (int i = 0; i < info.length; i++) {
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void notifyPauseDueToSize(Context context, long id, boolean isWifiRequired) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(ContentUris.withAppendedId(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, id));
        intent.setClassName("com.android.providers.downloads", "com.android.providers.downloads.SizeLimitActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Returns whether the network is available
     */
    public static Long getRecommendedMaxBytesOverMobile(Context context) {
        Long maxBytesValue = DownloadManager.getRecommendedMaxBytesOverMobile(context);;
        if(maxBytesValue == null) {
            // Third party mobile have not the record DOWNLOAD_RECOMMENDED_MAX_BYTES_OVER_MOBILE
            // in database, so add the record if the record does not exist.
            maxBytesValue = (long)context.getResources().getInteger(R.integer.default_recommended_max_bytes_over_mobile);
            DownloadManager.setRecommendedMaxBytesOverMobile(context, maxBytesValue);
        }
        return maxBytesValue;
    }
    /**
     * track xunlei usage switch event
     */
    public static void trackXLSwitchTrigerEvent(Context context, boolean enable) {
        /*Map <String,String> trackData = new HashMap<String, String>();
        Log.v(LOG_TAG, "XUNLEI usage switch is triggered to " + (enable ? "on" : "off"));
        Analytics tracker = Analytics.getInstance();
        trackData.put(TRACK_ID_XL_SWITCH_TRIGER, Boolean.toString(enable));
        tracker.startSession(context);
        tracker.trackEvent(TRACK_EVENT_XL_SWITCH, trackData);
        tracker.endSession();*/
        if(analyticsMark)
			return;
        if (enable) {
    		trackBehaviorEvent(context, "xunlei_open", 0, 0);
    	} else {
    		trackBehaviorEvent(context, "xunlei_close", 0, 0);
    	}
    }

    public static void trackEventStatus(Context context,String behavior,String fromAppName,  int event1,int event2) {
	    if(analyticsMark)
			return;
        int network = -1;
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager
                    .getActiveNetworkInfo();
            if (networkInfo != null) {
                network = networkInfo.getType();
            }
        }
        
        String xlId = "";

        String xmId = "";

        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, PRODUCT_NAME, PRODUCT_VERSION, xmId, xlId, fromAppName, "", network, event1, event2);
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_event_" + behavior, trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_BEHAVIOR, behavior, trackData);
    }
    
    /**
     * track  behavior for common
     */
    static void trackCommon(Context context, Map<String, String> trackData,String product, String productVersion, String xmId, String xlId,String pkgName,String ip,int network,int event1,int event2)
    {
		if(analyticsMark)
			return;
    	 if (trackData == null) return;
         String device = android.os.Build.MODEL;
         String imsi = "";
         String imei = "";
         TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
         if (telephonyManager != null) {
             imsi = telephonyManager.getSubscriberId();
             imei = telephonyManager.getDeviceId();
         }
         String mac = "";
         WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
         if (wifiManager != null) {
             mac = wifiManager.getConnectionInfo().getMacAddress();
         }
         String MIUIVersion = Build.VERSION.INCREMENTAL;
         String time = Long.toString(System.currentTimeMillis() / 1000);
         //trackData.put("behavior_event", behavior);
         trackData.put("product_name", product);
         trackData.put("product_version", productVersion);
         trackData.put("miui_version", MIUIVersion);

         //trackData.put("xiaomi_id", xmId);
         //trackData.put("xunlei_id", xlId);
         
        // trackData.put("imei", imei);
         trackData.put("mac", mac);
         //trackData.put("ip", ip);
         trackData.put("network_type", Integer.toString(network));
         trackData.put("event_data1", Integer.toString(event1));
         trackData.put("event_data2", Integer.toString(event2));
         trackData.put("time", time);
         /*
         if (!TextUtils.isEmpty(pkgName)) {
         	trackData.put("application_name", pkgName);
         }*/
         
         if (pkgName != null)
        	 trackData.put("application_name", pkgName);
    }
    /**
     * track  behavior for common
     */
    public static void trackBehaviorEvent(Context context,String behavior ,int event1,int event2) {
		if(analyticsMark)
			return;	
        int network = -1;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                network = networkInfo.getType();
            }
        }
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        
        String packageName = "";	// this name means the place where app downloading
        String product = "MIUI V6 Download";
        String productVersion = PRODUCT_VERSION; //synchronize this with DownloadService
        String xmId = "";
        String xlId = "";
        
        trackCommon(context, trackData, product, productVersion, xmId, xlId, packageName, "", network, event1, event2);
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_event_" + behavior, trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_BEHAVIOR, behavior, trackData);
    }
    
    /*
     * 注意此处的xlEnable和xlVipEnable字面意思和实际含义是反的
     */
    public static void trackOnlineStatus(Context context, int status, boolean xlEnable, boolean xlVipEnable, int eventId) {
		if(analyticsMark)
			return;	
        int network = -1;
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager
                    .getActiveNetworkInfo();
            if (networkInfo != null) {
                network = networkInfo.getType();
            }
        }
        String xlId = "";
        String xmId = "";
		
        String imsi = "";
        String imei = "";
        TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            imsi = telephonyManager.getSubscriberId();
            imei = telephonyManager.getDeviceId();
        }
        String mac = "";
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mac = wifiManager.getConnectionInfo().getMacAddress();
        }
        String time = Long.toString(System.currentTimeMillis() / 1000);
        String MIUIVersion = Build.VERSION.INCREMENTAL;
        
        Map <String,String> trackData = new HashMap<String, String>();
        trackData.put("online_event", String.valueOf(eventId));
        trackData.put("product_name", PRODUCT_NAME);
        trackData.put("product_version", PRODUCT_VERSION);
        trackData.put("phone_type", android.os.Build.MODEL);
        trackData.put("system_version", android.os.Build.VERSION.RELEASE);
        trackData.put("miui_version", MIUIVersion);
        
        //trackData.put("xiaomi_id", xmId);
        //trackData.put("xunlei_id", xlId);
        //trackData.put("application_name", "");
      //  trackData.put("imsi", imsi);
        //trackData.put("imei", imei);
        trackData.put("mac", mac);
        trackData.put("network_type", String.valueOf(network));
        trackData.put("time", time);
        trackData.put("online_event_status", String.valueOf(status));
        trackData.put("xunlei_open", String.valueOf(xlEnable ? 1 : 0));
        trackData.put("xunlei_vip_open", String.valueOf(xlVipEnable ? 1 : 0));
        // do track
        Analytics tracker = Analytics.getInstance();
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_online_status", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_ONLINESTATUS, "download_xunlei_online_status", trackData);
    }
    
    static void traceLog(String tag, String behavior, Map<String, String> trackData) {
  	  if (STAT_LOG_ON) {
    		StringBuffer buffer = new StringBuffer();
    		for (Map.Entry<String, String> entry : trackData.entrySet()) {
    			buffer.append(entry.getKey());
    			buffer.append(": ");
    			buffer.append(entry.getValue());
    			buffer.append("; ");
    		}
    		Log.d(tag, "event = " + behavior + ", data = " + buffer.toString());
    	}
  }
    
    public static void trackOnlineStatus(Context context, int status) {
		if(analyticsMark)
			return;
    	int network = -1;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                network = networkInfo.getType();
            }
        }
        
        String packageName = "";	// this name means the place where app downloading
        String product = "MIUI V6 Download";
        String productVersion = PRODUCT_VERSION; //synchronize this with DownloadService
        String xmId = "";
        String xlId = "";
        
        // do track
        Analytics tracker = Analytics.getInstance();
        Map <String,String> trackData = new HashMap<String, String>();
        trackCommon(context, trackData, product, productVersion, xmId, xlId, packageName, "", network, 0, 0);
        tracker.startSession(context);
        tracker.trackEvent("download_xunlei_online_event", trackData);
        tracker.endSession();
        
        traceLog(STAT_TAG_ONLINESTATUS, "download_xunlei_online_status", trackData);
    }
    /**
     * 是否传入的值为刚好整，比如10.1传回false,10.0传回true
     * @return
     */
    public static boolean isNumGood(double value)
    {
    	/*if(value-(int)value!=0)
    	{
    		return false;
    	}else
    	{
    		return true;
    	}*/
	String strValue=String.valueOf(value);
	int pos=strValue.indexOf('.');
	if(pos==-1)
	{
		return true;
	}else
	{
		if(strValue.charAt(pos+1)=='0')
		{
			return true;
		}
		return false;
	}
    }
    
    public static String formatUnit(String unit)
    {
    	if(unit==null||unit.equals(""))
    	{
    		return unit;
    	}
    	if(unit.endsWith("b")||unit.endsWith("B"))
    	{
    		return unit;
    	}else {
			return unit+"B";
		}
    }
    
    public static boolean isInternationalBuilder(){
        boolean res = false;
        if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) {
            res =true;
        }
        return  res;
    }
    
    public static boolean getXunleiUsagePermission(Context ctx) {
        // get DownloadProvider's context
        if (isInternationalBuilder()) {
            return false;
        }

            Context ct = null;
        try {
            ct = ctx.createPackageContext(DOWNLOADPROVIDER_PKG_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            return false;
        }
        SharedPreferences xPreferences = ct.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
        boolean xunlei_usage = xPreferences.getBoolean(PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
        return xunlei_usage;
    }
    
    public static boolean getXunleiVipSwitchStatus(Context ctx) {
        SharedPreferences xPreferences = ctx.getSharedPreferences(
                "com.android.providers.downloads.ui_preferences",
                Context.MODE_WORLD_WRITEABLE);
        long vipflag = xPreferences.getLong("xl_optdownload_flag", 3);
        return vipflag == 3;
    }
}
