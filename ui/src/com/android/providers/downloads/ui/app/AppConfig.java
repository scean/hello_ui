package com.android.providers.downloads.ui.app;

import android.text.TextUtils;
import android.os.Environment;

import com.android.providers.downloads.ui.utils.DebugLog;

public class AppConfig {

    public static final boolean DEBUG = false;

    public static final String TAG = "DownloadProviderUI";

    public static final String LOG_DIR = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dlprovider" : null;

    public static final String PREF_NAME = "download_pref";
    public static final String DOWNLOADPROVIDER_PKG_NAME = "com.android.providers.downloads";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION_IS_DEFAULT = "xunlei_usage_permission_is_default";
    public static final String PREF_KEY_XUNLEI_USER_ID = "xunlei_user_id";

    public static void LOGD(String message) {
        LOGD(TAG, message);
    }

    public static void LOGD(String tag, String message) {
        if (!TextUtils.isEmpty(message)) {
            DebugLog.d(tag, message);
        }
    }
}
