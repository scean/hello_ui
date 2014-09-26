package com.android.providers.downloads.ui;

/**
 * Created by monkey on 14-9-22.
 */
import com.android.providers.downloads.ui.pay.MiBiPay;
import com.android.providers.downloads.ui.pay.util.XLUtil;
import miui.external.Application;
import com.android.providers.downloads.ui.GlobalApplicationDelegate;
public class GlobalApplication extends miui.external.Application{
	final String TAG =XLUtil.getTagString(GlobalApplication.class);
	String mToken;
	@Override
	public miui.external.ApplicationDelegate onCreateApplicationDelegate() {
		return new GlobalApplicationDelegate();
	}
	public void init(){
		XLUtil.askRequestToken(getApplicationContext());
		initToken();
		askPrizesList();
	}
	public void uninit(){

	}

	void askPrizesList() {
		MiBiPay mibiPay = new MiBiPay();
		mibiPay.initWithContext(getApplicationContext(), null);
		mibiPay.RequestPrize(0,3,mToken);
	}
	private void initToken(){
		String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
		XLUtil.logDebug(TAG, "initToken=" + xunlei_token + "\nmTken=" + mToken);
		mToken = xunlei_token;
		XLUtil.logDebug(TAG, "initToken() token=" + mToken);
	}
}
