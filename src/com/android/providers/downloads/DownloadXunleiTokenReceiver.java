package com.android.providers.downloads;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Downloads;
import android.util.Log;
import android.os.Bundle;

import com.google.common.annotations.VisibleForTesting;
import com.android.providers.downloads.DownloadService;

import java.io.File;

/**
 * Receives system broadcasts (boot, network connectivity)
 */
public class DownloadXunleiTokenReceiver extends BroadcastReceiver {

    public static final String ACTION_INTENT_DOWNLOADLIST_BROADCAST = "com.process.media.broadcast.downloadlist";
    public static final String ACTION_INTENT_FALSETOKEN_BROADCAST = "com.process.media.broadcast.falsetoken";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            XLConfig.LOGD("Received broadcast intent for null!");
            return;
        }

        XLConfig.LOGD("DownloadXunleiTokenReceiver#onReceive action = " + action);
        if(action.equals(ACTION_INTENT_DOWNLOADLIST_BROADCAST)) {
            XLConfig.LOGD("Received broadcast intent for " + ACTION_INTENT_DOWNLOADLIST_BROADCAST);
            // Intent sIntent =new Intent(context, DownloadService.class);
            // Bundle bundle1 = new Bundle();
            // int status = 1;
            // bundle1.putInt("CMD_TYPE", status);
            // sIntent.putExtras(bundle1);
            // context.startService(sIntent);
            TokenHelper.getInstance().RequestToken(true);
        }

        if(action.equals(ACTION_INTENT_FALSETOKEN_BROADCAST)) {
            TokenHelper.getInstance().RequestToken(false);
        }
    }

}
