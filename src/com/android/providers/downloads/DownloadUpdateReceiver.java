package com.android.providers.downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DownloadUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(DownloadManager.ACTION_DOWNLOAD_UPDATE_PROGRESS_REGISTRATION)) {
            long[] registerIds = intent
                    .getLongArrayExtra(DownloadManager.INTENT_EXTRA_REGISTER_DOWNLOADS_UPDATE_PROGRESS);
            long[] unregisterIds = intent
                    .getLongArrayExtra(DownloadManager.INTENT_EXTRA_UNREGISTER_DOWNLOADS_UPDATE_PROGRESS);
            if (registerIds != null) {
                for (long id : registerIds) {
                    Constants.sDownloadSetNeedToUpdateProgress.add(id);
                }
            }
            if (unregisterIds != null) {
                for (long id : unregisterIds) {
                    Constants.sDownloadSetNeedToUpdateProgress.remove(id);
                }
            }
        }
    }

}
