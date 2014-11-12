package com.android.providers.downloads;

import android.os.Environment;
import android.text.TextUtils;

public class XLConfig {
    public static final boolean DEBUG = false;

    public static final String TAG = "DownloadManager";

    public static final String LOG_DIR = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dlprovider" : null;

    public static final String PREF_NAME = "download_pref";
    public static final String PREF_KEY_XUNLEI_TOKEN = "xunlei_token";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION_IS_DEFAULT = "xunlei_usage_permission_is_default";
    public static final String PREF_KEY_XUNLEI_USER_ID = "xunlei_user_id";
    public static final String PREF_KEY_XUNLEI_PEERID = "xunlei_peerid";
    public static final String PREF_KEY_XIAOMI_ID = "xiaomi_id";

    public static final String DEFAULT_PEERID = "000000000000000V";
    public static final String PREF_NAME_IN_UI = "com.android.providers.downloads.ui_preferences";
    public static final String PREF_KEY_XUNLEI_VIP = "xl_optdownload_flag";

    public static final long XUNLEI_VIP_DISENABLED = 1;
    public static final long XUNLEI_VIP_ENABLED = 3;
    public static final int XUNLEI_CDN_QUERY_TIMES = 2;

    public static final String PRODUCT_NAME = "MIUI V6 Download";
    public static final String PRODUCT_VERSION = "1.1.0.1";
    public static final String APP_KEY = "bWl1aV9kb3dubG9hZF9hcHAAcRcB";

    public static final String ACTION_INTENT_DOWNLOADLIST_BROADCAST = "com.process.media.broadcast.downloadlist";
    public static final String ACTION_INTENT_XL_DOWNLOAD_START = "com.process.media.broadcast.xltask.startdownload";

    public static void LOGD(String message) {
        DebugLog.d(TAG, message);
    }

    public static void LOGD(String message, Throwable t) {
        DebugLog.d(TAG, message, t);
    }

    public static void LOGD(String tag, String message) {
        DebugLog.d(tag, message);
    }

    public static void LOGD(String tag, String message, Throwable t) {
        DebugLog.d(tag, message, t);
    }
}
