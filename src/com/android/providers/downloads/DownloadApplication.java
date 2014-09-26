
package com.android.providers.downloads;
import miui.external.Application;
import com.android.providers.downloads.DownloadApplicationDelegate;
public class DownloadApplication extends miui.external.Application {
    @Override
    public miui.external.ApplicationDelegate onCreateApplicationDelegate() {
        return new DownloadApplicationDelegate();
    }
	public void initToken()
	{
		TokenHelper.getInstance().initWithContext(this.getApplicationContext());
	}
	public void uninitToken()
	{
		TokenHelper.getInstance().uninit();
	}
}