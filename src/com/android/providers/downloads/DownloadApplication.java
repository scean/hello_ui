package com.android.providers.downloads;

import miui.external.Application;

import com.android.providers.downloads.DownloadApplicationDelegate;
import com.xiaomi.mipush.sdk.MiPushClient;

public class DownloadApplication extends miui.external.Application {

    public static final String APP_ID = "2882303761517144351";
    public static final String APP_KEY = "5991714422351";

    @Override
    public miui.external.ApplicationDelegate onCreateApplicationDelegate() {
        return new DownloadApplicationDelegate();
    }

    public void init() {
        initToken();

        if (XLConfig.DEBUG) {
            CrashHandler.getInstance().init(this);
        }
    }

    public void unInit() {
        unInitToken();
    }

	private void initToken() {
		MiPushClient.registerPush(this, APP_ID, APP_KEY);
		TokenHelper.getInstance().initWithContext(this.getApplicationContext());
	}

	private void unInitToken() {
		TokenHelper.getInstance().uninit();
	}

}
