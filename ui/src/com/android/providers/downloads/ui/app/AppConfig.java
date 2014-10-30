package com.android.providers.downloads.ui.app;

import android.text.TextUtils;
import android.os.Environment;

import com.android.providers.downloads.ui.utils.DebugLog;

public class AppConfig {

    public static final boolean DEBUG = false;

    public static final String TAG = "DownloadProviderUI";

    public static final String LOG_DIR = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dlprovider" : null;

    public static void LOGD(String message) {
        LOGD(TAG, message);
    }

    public static void LOGD(String tag, String message) {
        if (!TextUtils.isEmpty(message)) {
            DebugLog.d(tag, message);
        }
    }
}
