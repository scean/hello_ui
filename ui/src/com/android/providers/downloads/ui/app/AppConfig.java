package com.android.providers.downloads.ui.app;

import android.text.TextUtils;
import android.os.Environment;

import com.android.providers.downloads.ui.utils.DebugLog;

public class AppConfig {

    public static final boolean DEBUG = false;

    public static final String TAG = "DownloadProviderUI";

    // xx.xx.xx.xx  鍏朵腑鍓�浣嶄负java灞傦紝 涓棿涓や釜涓�jni etm 灞�2浣嶆暟鐗堟湰鍙凤紝鏈�悗浣嶄负et灞�浣嶆暟鐗堟湰鍙�
    public static final String JAR_VERSION = "2.1.2.52";

    public static final String XL_PROPERTIES = "xl_mi";

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
