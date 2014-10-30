package com.android.providers.downloads.ui;

import android.content.Intent;

import miui.external.ApplicationDelegate;

import com.android.providers.downloads.ui.pay.MiBiPay;

public class GlobalApplicationDelegate extends miui.external.ApplicationDelegate {

    @Override
    public void onCreate() {
        super.onCreate();

        GlobalApplication app = (GlobalApplication)getApplication();
        app.init();
    }

    @Override
    public void onTerminate(){
        super.onTerminate();

        GlobalApplication app =(GlobalApplication)getApplication();
        app.uninit();
    }

}
