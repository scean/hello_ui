package com.android.providers.downloads;
import miui.external.ApplicationDelegate;
public class DownloadApplicationDelegate extends miui.external.ApplicationDelegate {
    private static final String APP_ID = "2882303761517144351";
    private static final String APP_KEY = "5991714422351";

	@Override
	public void onCreate() {
		super.onCreate();
		DownloadApplication app =(DownloadApplication)getApplication ();
		app.initToken();
		//TokenHelper.getInstance().initWithContext(getApplication ().getApplicationContext());
	}
	@Override
	public void onTerminate(){
		super.onTerminate();
		DownloadApplication app =(DownloadApplication)getApplication ();
		app.uninitToken();
		//TokenHelper.getInstance().uninit();
	}
}