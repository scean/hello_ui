package com.android.providers.downloads.ui.pay.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.StringTokenizer;

import android.content.Intent;
import com.android.providers.downloads.ui.DownloadUtils;
import com.android.providers.downloads.ui.pay.util.XLLog.LogLevel;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;



public class XLUtil {
    private static final String TAG = getTagString(XLUtil.class);
    public static final int mProductId = 10031;//xiaomi;
    private static int mPartnerId = 0;
    private static String mPeerId = null;
    private static String mOSVersion = null;
    //    private static String mSelfAppVersion = null;
    private static String mIMEI = null;
    public static String mAPNName = null;
    public static int NETWORKTYPE = -1;
    public static int NETWORKSUBTYPE = -1;
    public static String SSID = null;
    public static final String PREF_NAME = "download_pref";
    public static final String PREF_KEY_XUNLEI_TOKEN = "xunlei_token";
    public static final String PREF_KEY_MIUI_NICKNAME = "miui_nickname";
    public static final String PREF_KEY_XIAOMI_ID = "xiaomi_id";
	public static final String ACTION_INTENT_DOWNLOADLIST_BROADCAST = "com.process.media.broadcast.downloadlist";
	public static final String ACTION_INTENT_FALSETOKEN_BROADCAST = "com.process.media.broadcast.falsetoken";
    static {
        if (Config.PRINT_LOG) {
            XLLog.setLogTag(" com.xunlei.downloadplatforms.log ");
            XLLog.setLogLevel(LogLevel.LOG_LEVEL_DEBUG);
        } else {
            XLLog.setLogLevel(LogLevel.LOG_LEVEL_OFF);
        }
        if (Config.LOG_WIRTE_FILE) {
            setLogWriteFile(true);
        }
    }
    //璁剧疆鏄惁鎵撳嵃鍒皊dcard
    public static void setLogWriteFile(boolean flag) {
        XLLog.setWriteFile(flag);
    }
    //鑾峰彇tag
    public static String getTagString(Class<?> cls) {
        return  cls.getSimpleName();
    }

    // 鏃ュ織鎵撳嵃锛岄粯璁ゆ墦鍗癲ebug绾у埆鐨勬棩锟�?
    public static void logDebug(String tag, String content) {
        if (Config.PRINT_LOG) {
            XLLog.d(tag, content);
        }
    }

    public static void logDebug(String tag, String content,boolean isPrint) {
        if (isPrint) {
            //            XLLog.d(tag, content);
            Log.d(tag, content);
        }
    }

    public static void logWarn(String tag, String content) {
        if (Config.PRINT_LOG) {
            XLLog.w(tag, content);
        }
    }

    public static void logError(String tag, String content) {
        if (Config.PRINT_LOG) {
            XLLog.e(tag, content);
        }
    }
	public static void printStackTrace(Exception e) {
		if (Config.PRINT_LOG) {
			e.printStackTrace();
		}
	}

    public static boolean ensureDir(String path) {
        logDebug(TAG, " ensureDir path : " + path);
        if (null == path) {
            return false;
        }
        boolean ret = false;
        File file = new File(path);
        logDebug(TAG, " ensureDir file.exists() : " + file.exists()
                + " , file.isDirectory() : " + file.isDirectory());
        if (!file.exists() || !file.isDirectory()) {
            try {
                ret = file.mkdirs();
                logDebug(TAG, " ensureDir file.mkdirs() ret : " + ret);
            } catch (SecurityException se) {
                se.printStackTrace();
            }
        }
        return ret;
    }

    public static boolean isFileExist(String path) {
        if (null == path) {
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            return false;
        }
        return true;
    }

    public static boolean ensureFile(String path) {
        if (null == path) {
            return false;
        }

        boolean ret = false;

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            try {
                file.createNewFile();
                ret = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public static boolean deleteFile(String path) {
        if (path == null) {
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            return true;
        }
        return file.delete();

    }

    public static String getSDCardDir() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    public static boolean isSDCardExist() {
        return Environment.MEDIA_MOUNTED.equalsIgnoreCase(Environment
                .getExternalStorageState());
    }

    // 鑾峰彇sdcard鍓╀綑绌洪棿澶у皬鍗曚綅鏄疢B
    public static long getSdCardAvailaleSize() {
        long ret = 0;
        String strSDCardPath = getSDCardDir();
        if (null != strSDCardPath) {
            StatFs stat = new StatFs(strSDCardPath);
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            ret = (availableBlocks * blockSize) / 1024 / 1024;
        }
        return ret;
    }

    public static long getAvailableExternalMemorySize() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    public static long getTotalExternalMemorySize() {
        File path = Environment.getExternalStorageDirectory();
        Environment.getExternalStorageState();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    /**
     * 鑾峰彇褰撳墠绉掓暟
     * */
    public static long getCurrentUnixTime() {
        return (System.currentTimeMillis() / 1000L);
    }
    public static  long getCurrentUnixMillTime(){
        return  System.currentTimeMillis();
    }

    // 鑾峰彇peerid
    public static String getPeerid(final Context context) {
        if (null == mPeerId && null != context) {
            WifiManager wm = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            if (null != wm && null != wm.getConnectionInfo()) {
                String mac = wm.getConnectionInfo().getMacAddress();
                mac += "004V";
                mPeerId = mac.replaceAll(":", "");
                mPeerId = mPeerId.replaceAll(",", "");
                mPeerId = mPeerId.replaceAll("[.]", "");
                mPeerId = mPeerId.toUpperCase();
            }
        }
        XLUtil.logDebug(TAG, "getPeerid mPeerId : " + mPeerId + " , context : "
                + context);
        return mPeerId;
    }

    // 鑾峰彇绯荤粺鐗堟湰淇℃伅
    //    public static String getOSVersion() {
    //        if (null == mOSVersion) {
    //            mOSVersion = "SDKV = " + android.os.Build.VERSION.RELEASE;
    //            mOSVersion += "_MANUFACTURER = " + android.os.Build.MANUFACTURER;
    //            mOSVersion += "_MODEL = " + android.os.Build.MODEL;
    //            mOSVersion += "_PRODUCT = " + android.os.Build.PRODUCT;
    //            mOSVersion += "_FINGERPRINT = " + android.os.Build.FINGERPRINT;
    //            mOSVersion += "_CPU_ABI = " + android.os.Build.CPU_ABI;
    //            mOSVersion += "_ID = " + android.os.Build.ID;
    //        }
    //        return mOSVersion;
    //    }

    // 鑾峰彇鑷繁鐨勭増鏈彿
    public static String getSelfAppVersion(final Context context) {
        /*if (null == mSelfAppVersion && null != context) {
            try {
                mSelfAppVersion = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return mSelfAppVersion;*/
        return DownloadUtils.PRODUCT_VERSION;
    }

    // 鑾峰彇IMEI锟�?
    public static String getIMEI(final Context context) {
        if (null == mIMEI && null != context) {
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            if (null != tm) {
                mIMEI = tm.getDeviceId();
            }
            if (null == mIMEI) {
                mIMEI = "000000000000000";
            }
        }
        return mIMEI;
    }

    public static int getPartnerId(final Context context) {
        if (0 == mPartnerId && null != context) {
            /*try {
                String partnerId =  context.getString(R.string.partner_id);
                if (null != partnerId && partnerId.length() > 0) {
                    mPartnerId = Integer.parseInt(partnerId, 16);
                }
                logDebug(TAG , "getPartnerId mPartnerId : " + mPartnerId + " , partnerId : " + partnerId);
            } catch (Exception e) {
                logError(TAG , " getPartnerId Error : " + e.getMessage());
                mPartnerId = 0;
            }*/
        }
        return mPartnerId;
    }

    public static boolean isNetworkOk(final Context context) {
        boolean ret = false;
        if (null != context) {
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (null != cm) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (null != info && info.isConnectedOrConnecting()) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    /**
     * 鍒ゆ柇瀛楃涓叉槸鍚︿负锟�?
     * **/
    public static boolean isNullOrEmpty(final String str) {
        boolean ret = false;
        if (null == str || "".equals(str) || "null".equals(str)) {
            ret = true;
        }
        return ret;
    }
    public static long convertBtoMB(long src){
        return src/1024/1024;
    }
    /*
     *  return  first/second%   0-100
     */
    public static int ratedDivide(long first, long second) {
        int res =0;
        if (second != 0) {
             res =(int) (first*100/second);
        }
        // show progress larger than 1
        if (first > 0) {
            res = (int)Math.max(1, res);
        }
        return res;
    }
    /*
        20140607 to 2014/06/07
     */
    public static String convertDateToShowDate2(String str){
        if (isNullOrEmpty(str)) {
            return "";
        }
        String res="";
        if (str.length() >6) {
            res +=str.substring(0, 4);
            res +="-";
            res +=str.substring(4, 6);
            res +="-";
            res +=str.substring(6);
        } else {
            res =str;
        }
        return res;
    }
    /*
     * 20140607 to 2014-06-07
     */
    public static String convertDateToShowDate(String str){
        if (isNullOrEmpty(str)) {
            return "";
        }
        String res="";
        if (str.length() >6) {
            res +=str.substring(0, 4);
            res +="-";
            res +=str.substring(4, 6);
            res +="-";
            res +=str.substring(6);
        } else {
            res =str;
        }
        return res;
    }

    public static void killProcess() {
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
    }



    /**
     * 鑾峰彇骞冲潎涓嬭浇閫熷害 KB/s
     * @param startTime 璧峰涓嬭浇鏃堕棿
     * @param endTime 缁撴潫涓嬭浇鏃堕棿
     * @param fileSize 鏂囦欢澶у皬锛堝瓧鑺傦級
     * @return 骞冲潎涓嬭浇閫熷害
     */
    public static long getAverageDownloadSpeed(long startTime, long endTime, long fileSize) {

        XLUtil.logDebug(TAG, "func getAverageDownloadSpeed begins, startTime:" + startTime + ",endTime:" + endTime + ",filesize:" + fileSize);
        long avgSpeed = 0;
        long deltaTime = endTime - startTime;
        if (0 == deltaTime) {
            XLUtil.logWarn(TAG, "func getAverageDownloadSpeed, 0 == deltaTime");
            return 0;
        }
        fileSize = fileSize / 1024; //杞垚KB
        avgSpeed = fileSize / deltaTime;
        XLUtil.logDebug(TAG, "func getAverageDownloadSpeed ends, avgSpeed:" + avgSpeed);
        return avgSpeed;

    }

    /**
     * 鑾峰彇鏂囦欢鍚庣紑锟�?
     * @param fileName 鏂囦欢锟�?
     * @param url 瀹屾暣url
     * @return 杩斿洖鎵╁睍锟�?
     */
    public static String getFileExt(String fileName, String url){
        XLUtil.logDebug(TAG, "func getFileExt begins, fileName:" + fileName);
        String fileExt = "";
        if (isNullOrEmpty(fileName)) {
            XLUtil.logDebug(TAG, "func getFileExt ends,fileExt:" + fileExt);
            return fileExt;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (-1 != dotIndex) {
            fileExt = fileName.substring(dotIndex + 1);
            XLUtil.logDebug(TAG, "func getFileExt ends,fileExt:" + fileExt);
            return fileExt;
        }
        if (!isNullOrEmpty(url)) {
            dotIndex = url.lastIndexOf('.');
            int sepIndex = url.lastIndexOf('/');
            if (-1 != dotIndex && dotIndex > sepIndex) {
                fileExt = url.substring(dotIndex + 1);
            }
            XLUtil.logDebug(TAG, "func getFileExt, dotIndex:" + dotIndex  + ",sepIndex:" + sepIndex);
        }
        XLUtil.logDebug(TAG, "func getFileExt ends,fileExt:" + fileExt);
        return fileExt;
    }

    public static void saveLongPreferences(Context context, String key, long value) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = pref.edit();
        editor.putLong(key, value);
        editor.commit();
    }

    public static long getLongPreferences(Context context, String key) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getLong(key, 0);
    }
    public static void saveStringPreferences(Context context, String key, String str) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = pref.edit();
        editor.putString(key, str);
        editor.commit();

    }
    public static String getStringPreferences(Context context, String key) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(key, "");

    }
    public static String getStringPackage(Context context,String package_name,String pre_name,String key){
        Context DownloadServiceContext =null;
        String str ="";
        try {
            DownloadServiceContext = context.createPackageContext(package_name,Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (DownloadServiceContext !=null) {
            SharedPreferences    Preferences = DownloadServiceContext.getSharedPreferences(pre_name, Context.MODE_MULTI_PROCESS);
            str = Preferences.getString(key, "");
        }
        return  str;
    }
    public synchronized static int  saveStringPackage(Context context,String package_name,String pre_name,String key,String value) {
        int res =-1;
        Context DownloadServiceContext =null;
        String str ="";
        try {
            DownloadServiceContext = context.createPackageContext(package_name,Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (DownloadServiceContext !=null) {
            SharedPreferences    Preferences = DownloadServiceContext.getSharedPreferences(pre_name, Context.MODE_MULTI_PROCESS);
            Editor editor = Preferences.edit();
            editor.putString(key, value);
            editor.commit();
            res =0;
        }
        return res;
    }

    public synchronized static String getStringXiaomiIdPreference( Context context) {

        String str ="";
        str = getStringPackage(context,"com.android.providers.downloads",PREF_NAME,PREF_KEY_XIAOMI_ID);

        return  str;
    }

    public synchronized static String getStringPackagePreference( Context context) {

        String str ="";
        str = getStringPackage(context,"com.android.providers.downloads",PREF_NAME,PREF_KEY_XUNLEI_TOKEN);

        return  str;
    }
    public synchronized static int  saveStringPackagePreferenc(Context context,String value) {
        int res =-1;

        res =saveStringPackage(context,"com.android.providers.downloads",PREF_NAME,PREF_KEY_XUNLEI_TOKEN,value);
        return res;
    }
    public synchronized static int saveStringPackagePreferenc(Context context, String key, String value){
    	int res = -1;
    	res = saveStringPackage(context, "com.android.providers.downloads", PREF_NAME, key, value);
    	return res;
    }
    public synchronized static String getStringPackagePreferenceNickName( Context context) {

        String str ="";
        str = getStringPackage(context,"com.android.providers.downloads",PREF_NAME,PREF_KEY_MIUI_NICKNAME);

        return  str;
    }


    public static String getAPNName(Context ctx) {
            ConnectivityManager conManager= (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = conManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE); 
            String apnName = info.getExtraInfo(); 

        return apnName;
    }

    public static String getSSID(Context ctx) {
        WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        return wifiInfo.getSSID();
    }
	public static void askRequestToken(Context context) {
		if(context ==null){
			return;
		}
		Intent intent = new Intent(ACTION_INTENT_DOWNLOADLIST_BROADCAST);
		context.sendBroadcast(intent);
	}
	public static void askRequestTokenFake(Context context){
		if(context ==null){
			return;
		}
		Intent intent = new Intent(ACTION_INTENT_FALSETOKEN_BROADCAST);
		context.sendBroadcast(intent);
	}
    public static String getNetworkSubType(int subType) {
        String type = null;
        switch (subType) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
        case TelephonyManager.NETWORK_TYPE_EDGE:
        case TelephonyManager.NETWORK_TYPE_CDMA:
            // 2G缃戠粶
            type = "2G";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
        case TelephonyManager.NETWORK_TYPE_HSDPA:
        case TelephonyManager.NETWORK_TYPE_UMTS:
            // 3G缃戠粶
            type = "3G";
            break;
        default:
            type = "OTHER";
            break;
        }

        return type;
    }
	public static boolean isLanguageCNorTW() {
		String language = getLanguageEnv();

		if (language != null
				&& (language.trim().equals("zh-CN") || language.trim().equals("zh-TW")))
			return true;
		else
			return false;
	}
	private static String getLanguageEnv() {
		Locale l = Locale.getDefault();
		String language = l.getLanguage();
		String country = l.getCountry().toLowerCase();
		if ("zh".equals(language)) {
			if ("cn".equals(country)) {
				language = "zh-CN";
			} else if ("tw".equals(country)) {
				language = "zh-TW";
			}
		} else if ("pt".equals(language)) {
			if ("br".equals(country)) {
				language = "pt-BR";
			} else if ("pt".equals(country)) {
				language = "pt-PT";
			}
		}
		return language;
	}
}
