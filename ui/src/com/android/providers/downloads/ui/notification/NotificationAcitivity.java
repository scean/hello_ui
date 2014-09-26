package com.android.providers.downloads.ui.notification;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.xunlei.auth.AuthManager;
import com.xunlei.constant.Constants;

import android.accounts.*;
import miui.accounts.ExtraAccountManager;
import miui.content.ExtraIntent;

/**
 * 中转Activity， 为授权跳转传的参数 Activity
 * @Package com.android.providers.downloads.ui.notification
 * @ClassName: NotificationAcitivity
 * @author gaogb
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月18日 下午9:05:52
 */
public class NotificationAcitivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initData();
    }

    private String getAction() {
        String action = NotificationReveiver.ACTION_DOWNLOAD_NOTIFICATION_INIT;
        Intent intent = getIntent();
        if (null != intent) {
            action = intent.getStringExtra(NotificationReveiver.PAGE_TYPE);
        }
        return action;
    }

    private void initData() {
        String action = getAction();
        LogUtil.debugLog("NotificationAcitivity.initData.action="+action);
        if (NotificationReveiver.ACTION_XIAOMI_LOGIN.equals(action)) {
            AccountManager accountManager = AccountManager.get(getApplicationContext());
            accountManager.addAccount(ExtraIntent.XIAOMI_ACCOUNT_TYPE,
                    null, null, null, this, null, null);
            finish();
        } else if (NotificationReveiver.ACTION_XIAOMI_AUTH.equals(action)) {
            // 跳转到授权页
            AuthManager.getInstance().startProcessAuth(this,
                    Constants.AUTH_TYPE_REAL);
            finish();
        }
    }
}
