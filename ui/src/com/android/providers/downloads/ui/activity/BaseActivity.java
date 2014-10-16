package com.android.providers.downloads.ui.activity;

import miui.app.Activity;
import android.content.Context;

import com.android.providers.downloads.ui.utils.DownloadUtils;

public class BaseActivity extends Activity {
	public static final int STATUS_ONLINE = 0;
	public static final int STATUS_OFFLINE = 1;
	
	private int ONLINE_EVENT_ID = -1;
	private boolean mPaused = false;	// 弹框的情况不统计下线（onPause）
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		if (ONLINE_EVENT_ID != -1 && !mPaused) {
			Context ctx = getApplicationContext();
			DownloadUtils.trackOnlineStatus(ctx, STATUS_ONLINE,
					!DownloadUtils.getXunleiUsagePermission(ctx), !DownloadUtils.getXunleiVipSwitchStatus(ctx), ONLINE_EVENT_ID);
		}
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		mPaused = true;
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		mPaused = false;
		
		if (ONLINE_EVENT_ID != -1) {
			Context ctx = getApplicationContext();
			DownloadUtils.trackOnlineStatus(ctx, STATUS_OFFLINE,
					!DownloadUtils.getXunleiUsagePermission(ctx), !DownloadUtils.getXunleiVipSwitchStatus(ctx), ONLINE_EVENT_ID);
		}
	}
	
	protected void setOnLineEventId(int eventId) {
		ONLINE_EVENT_ID = eventId;
	}
}
