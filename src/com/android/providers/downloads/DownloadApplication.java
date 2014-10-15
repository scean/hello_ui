
package com.android.providers.downloads;
import miui.external.Application;
import com.android.providers.downloads.DownloadApplicationDelegate;
import com.xiaomi.mipush.sdk.MiPushClient;
public class DownloadApplication extends miui.external.Application {
    private static final String APP_ID = "2882303761517144351";
    private static final String APP_KEY = "5991714422351";
    @Override
    public miui.external.ApplicationDelegate onCreateApplicationDelegate() {
        return new DownloadApplicationDelegate();
    }
	public void initToken()
	{
		com.xiaomi.mipush.sdk.Constants.useOfficial();
		MiPushClient.registerPush(this, APP_ID, APP_KEY);
		TokenHelper.getInstance().initWithContext(this.getApplicationContext());
	}
	public void uninitToken()
	{
		TokenHelper.getInstance().uninit();
	}
}