package com.android.providers.downloads;

import android.accounts.Account;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.net.Uri;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.os.Environment;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.File;
import java.io.IOException;

import miui.accounts.ExtraAccountManager;

import com.xiaomi.mipush.sdk.PushMessageReceiver;
import com.xiaomi.mipush.sdk.MiPushMessage;
import com.xiaomi.mipush.sdk.MiPushCommandMessage;
import com.xiaomi.mipush.sdk.MiPushClient;

public class DownloadPushMessageReceiver extends PushMessageReceiver {
    private static final String DEFAULT_FILENAME = "miui_attachment";

    @Override
    public void onReceiveMessage(Context context, MiPushMessage message) {
        try {
            String urlString = message.getContent();
            // check whether url is valid
            URL url = new URL(urlString);
            Uri uri = Uri.parse(urlString);
            Uri destUri = Uri.fromFile(new File(getSavedPath(urlString)));
            Request request = new Request(uri);
            request.setVisibleInDownloadsUi(true);
            request.setDestinationUri(destUri);
            request.setDescription(message.getDescription());
            request.setTitle(message.getTitle());
            request.allowScanningByMediaScanner();
            DownloadManager dm = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);
            XLConfig.LOGD("Created a pushed task : " + urlString);
        } catch (MalformedURLException e) {
            XLConfig.LOGD("Pushed url is invalid!", e);
        } catch (NullPointerException e) {
            XLConfig.LOGD("Pushed empty url!", e);
        }
    }

    @Override
    public void onCommandResult(Context context, MiPushCommandMessage message) {
        long result = message.getResultCode();
        String command = message.getCommand();

        if (MiPushClient.COMMAND_REGISTER.equals(command)) {
            if (result == 0) {
                try {
                    Account account = ExtraAccountManager.getXiaomiAccount(context);
                    if (account != null) {
                        MiPushClient.setAlias(context, account.name, null);
                    }
                } catch (Exception e) {
                    XLConfig.LOGD("Failed to get Xiaomi Account.", e);
                }
            } else {
                XLConfig.LOGD("Failed to register MiPush!");
            }
        } else if (MiPushClient.COMMAND_SET_ALIAS.equals(command)) {
            if (result != 0) {
                XLConfig.LOGD("Failed to set MiPush alias!");
            }
        }
    }

    /*
     * Get saved path
     */
    private String getSavedPath(String url) {
        File folder = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!folder.exists()) {
            folder = Environment.getExternalStorageDirectory();
        }
        // get folder
        String folderPath = folder.getPath();
        String filename = getFileName(url);
        String localPath;
        if (TextUtils.isEmpty(filename)) {
            filename = DEFAULT_FILENAME;
        }
        localPath = folderPath + "/" + filename;
        return localPath;
    }

    /*
     * Get file name
     */
    private String getFileName(String urlString) {
        String filename = null;
        URL url;
        HttpURLConnection conn = null;
        if (urlString == null || urlString.length() < 1) {
            return null;
        }

        try {
            url = new URL(urlString);
            conn = (HttpURLConnection)url.openConnection();
            conn.connect();
            conn.getResponseCode();
            // get file name from Content-Disposition header
            filename = conn.getHeaderField("Content-Disposition");
            if (TextUtils.isEmpty(filename)) {
                // get real Url
                URL absUrl = conn.getURL();
                filename = absUrl.getFile();
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = filename.substring(index);
                }
            }
        } catch (MalformedURLException e) {
            XLConfig.LOGD("Error when getFileName!", e);
        } catch (IOException e) {
            XLConfig.LOGD("Error when getFileName", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
                conn = null;
            }
        }
        return filename;
    }
}
