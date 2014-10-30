package com.android.providers.downloads.ui;

import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.utils.CrashHandler;
import com.android.providers.downloads.ui.GlobalApplicationDelegate;
import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.pay.MiBiPay;

import miui.external.Application;

public class GlobalApplication extends miui.external.Application {
    private final String TAG = GlobalApplication.class.getSimpleName();

    private String mToken;

    @Override
    public miui.external.ApplicationDelegate onCreateApplicationDelegate() {
        return new GlobalApplicationDelegate();
    }

    public void init() {
        XLUtil.askRequestToken(getApplicationContext());
        initToken();
        askPrizesList();

        if (AppConfig.DEBUG) {
            CrashHandler.getInstance().init(this);
        }
    }

    public void uninit() {
    }

    void askPrizesList() {
        MiBiPay mibiPay = new MiBiPay();
        mibiPay.initWithContext(getApplicationContext(), null);
        mibiPay.RequestPrize(0,3,mToken);
    }

    private void initToken(){
        String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
        AppConfig.LOGD(TAG, "initToken=" + xunlei_token + "; mTken=" + mToken);
        mToken = xunlei_token;
        AppConfig.LOGD(TAG, "initToken() token=" + mToken);
    }
}
