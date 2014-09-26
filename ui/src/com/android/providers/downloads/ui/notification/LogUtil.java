package com.android.providers.downloads.ui.notification;

import android.util.Log;
import com.android.providers.downloads.ui.pay.util.XLUtil;

/**
 * 日志辅助类， isDebug=true 时 打印LOG， false 不打印
 * @Package com.android.providers.downloads.ui.notification
 * @ClassName: LogUtil
 * @author gaogb
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月21日 下午12:37:46
 */
public class LogUtil {

    private static final String TAG = "notification";

    private static boolean isDebug = true;

    public static void debugLog(String msg) {
        if (isDebug){
	        XLUtil.logDebug(TAG, null == msg ? "null" : msg);
        }
    }
     public static void errorLog(String msg) {
        if (isDebug){
	        XLUtil.logError(TAG, null==msg?"null":msg);
        }
    }
}
