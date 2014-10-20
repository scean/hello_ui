package com.android.providers.downloads.ui.notification;

import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.xunlei.auth.AuthManager;
import com.xunlei.constant.Constants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.R.integer;
import android.accounts.Account;
import miui.accounts.ExtraAccountManager;
import miui.content.ExtraIntent;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.PendingIntent;

/**
 * @author quan operate account data
 */
public class AccountLogic {
    private static AccountLogic instance;
    public static final int REQUEST_ADD_FLOW_REASON_UNKNOWN = 1;
    public static final int REQUEST_ADD_FLOW_REASON_NOTIFY = 2;
    private int mAddFlowReason = REQUEST_ADD_FLOW_REASON_UNKNOWN;
    
    private AccountLogic() {
    }

    public static AccountLogic getInstance() {
        if (null == instance) {
            instance = new AccountLogic();
        }
        return instance;
    }
    

    public void setAddFlowReason(int reason) {
    	mAddFlowReason = reason;
    }
    
    public int getAddFlowReason() {
    	return mAddFlowReason;
    }

    /**
     * check xiaomi account is logined
     *
     * @return
     */
    public boolean isLogined(Context context) {
        Account account = ExtraAccountManager.getXiaomiAccount(context);
        return account != null;
    }

    /**
     * check xiaomi account is authed by xunlei if not login return false yet
     * 
     * @return
     */
    public boolean isAuthed(Context context) {
        if (isLogined(context)) {
            String token = PreferenceLogic.getInstance(context).getToken();
            return !TextUtils.isEmpty(token);
        }
        return false;
    }

    /**
     *
     * 是否是迅雷VIP会员
     *
     * @Title: isVipXunleiAccount
     * @return
     * @return boolean
     * @date 2014年6月20日 上午11:50:54
     */
    public boolean isVipXunleiAccount(Context context) {
        boolean bool = false;
        String token = PreferenceLogic.getInstance(context).getToken();
        AccountInfoInstance accoutnInstance = AccountInfoInstance.getInstance(context, token);
        if (accoutnInstance != null && accoutnInstance.getAccountInfo() != null
                && accoutnInstance.getAccountInfo().isvip == 1) {
            bool = true;
        }
        return bool;
    }

    public boolean isUsingXunleiDownload(Context context) {
        return PreferenceLogic.getInstance(context).getIsHaveUseXunleiDownload();
    }

    public boolean isXunleiAccountExpired() {
        return false;
    }

    public PendingIntent getGoToXiaomiLoginPendingIntent(Context context,int flag) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_XIAOMI_LOGIN);
        notificationIntent.putExtra("flag", flag);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }
    public PendingIntent getGoToXiaomiLoginPendingIntent(Context context) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_XIAOMI_LOGIN);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }

    public PendingIntent getToAuthPendingIntent(Context context) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_XIAOMI_AUTH);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }

    public PendingIntent getToVipExpirePendingIntent(Context context) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_DOWNLOAD_VIP_EXPIRE_INIT);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }
    
    public PendingIntent getToVipExpirePendingIntent(Context context,int flag) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_DOWNLOAD_VIP_EXPIRE_INIT);
        notificationIntent.putExtra("flag", flag);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }

    public PendingIntent getToDownloadListPendingIntent(Context context) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_FORWARD_TO_DOWNLOADLIST);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }
    
    public PendingIntent getToDownloadListPendingIntent(Context context,int flag) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_FORWARD_TO_DOWNLOADLIST);
        notificationIntent.putExtra("flag", flag);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }

    public PendingIntent getToAutoObtainOneMonth(Context context) {
        Intent notificationIntent = new Intent(NotificationReveiver.ACTION_FORWARD_AUTO_OBTAIN);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return contentIntent;
    }
}
