package com.android.providers.downloads.ui.app;

import android.text.TextUtils;
import android.os.Environment;

import java.io.File;

import com.android.providers.downloads.ui.utils.DebugLog;

public class AppConfig {

    public static final String TAG = "DownloadProviderUI";

    public static final String LOG_DIR = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dlprovider" : null;

    public static final boolean DEBUG = new File(LOG_DIR, ".log").exists();

    public static final String ACTION_PRIVACY_ACCEPT = "com.android.providers.downloads.PRIVACY_ACCEPT";

    public static final String PREF_NAME = "download_pref";
    public static final String PREF_NAME_UI = "com.android.providers.downloads.ui_preferences";
    public static final String DOWNLOADPROVIDER_PKG_NAME = "com.android.providers.downloads";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
    public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION_IS_DEFAULT = "xunlei_usage_permission_is_default";
    public static final String PREF_KEY_XUNLEI_USER_ID = "xunlei_user_id";
    public static final String PREF_KEY_VIP_FLAG_IS_DEFAULT = "xunlei_key_vip_flag_is_default";
    public static final String PREF_KEY_IS_PRIVACY_TIP_SHOWN = "is_privacy_tip_shown";
    public static final String PREF_KEY_IS_APP_ACTIVED = "is_app_actived";

    public static void LOGD(String message) {
        LOGD(TAG, message);
    }

    public static void LOGD(String tag, String message) {
        if (!TextUtils.isEmpty(message)) {
            DebugLog.d(tag, message);
        }
    }
}
