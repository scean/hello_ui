/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.downloads;

import miui.app.Activity;
import miui.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Downloads;
import android.text.format.Formatter;
import android.util.Log;
import android.os.Bundle;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Activity to show dialogs to the user when a download exceeds a limit on download sizes for
 * mobile networks.  This activity gets started by the background download service when a download's
 * size is discovered to be exceeded one of these thresholds.
 */
public class PrivacyTipActivity extends Activity
        implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    private AlertDialog mDialog;
    private Queue<Intent> mDownloadsToShow = new LinkedList<Intent>();

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Helpers.isPrivacyTipShown(this)) {
            showDialog();
        } else {
            finish();
        }
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.privacy_tip_title)
            .setMessage(R.string.privacy_tip_content)
            .setPositiveButton(R.string.privacy_tip_ok, this)
            .setNegativeButton(R.string.privacy_tip_cancel, this);
        mDialog = builder.setOnCancelListener(this).show();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        dialogClosed();
    }

    private void dialogClosed() {
        Helpers.setPrivacyTipShown(this);
        mDialog = null;
        finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_NEGATIVE) {
            Helpers.setXunleiUsagePermission(this, false);
        } else if (which == AlertDialog.BUTTON_POSITIVE) {
        }
        dialogClosed();
    }

}