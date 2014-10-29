package com.android.providers.downloads;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.providers.downloads.XLConfig;

public class NotificationHelper {
    private static boolean isShowLog = true;
    private static String TAG = "notification";
    public static final String ACTION_DOWNLOAD_NOTIFICATION_INIT = "com.downloads.notification.action.init";

     /** NotificationLogic switch*/
    public static final String PREF_KEY_NOTIFICATION_SWITCH_ON_OFF = "notification_switch_on_off";

    public static void initNotificationService(Context context) {
        log("SizeLimitAcitivity.NotificationHelper.initNotificationService");
        Intent intent = new Intent(ACTION_DOWNLOAD_NOTIFICATION_INIT);
        context.sendBroadcast(intent);
    }

    private static void log(String str) {
        if (isShowLog)
            Log.d(TAG, str);
    }

    /**
     * 
     * 消息提示开关， 默认开
     * @Title: getNotificationSwitch
     * @param context
     * @return
     * @return boolean
     * @date 2014年6月23日 下午9:55:13
     */
    @SuppressWarnings("deprecation")
    public static boolean getNotificationSwitch(Context context) {
        SharedPreferences mPreferences = context.getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_WORLD_WRITEABLE);
        return mPreferences.getBoolean(PREF_KEY_NOTIFICATION_SWITCH_ON_OFF, true);
    }

    @SuppressWarnings("deprecation")
    public static void saveNotificationSwitch(Context context, boolean value) {
        SharedPreferences mPreferences = context.getApplicationContext().getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_WORLD_WRITEABLE);
        mPreferences.edit().putBoolean(PREF_KEY_NOTIFICATION_SWITCH_ON_OFF, value);
    }
}
