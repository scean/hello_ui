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

package com.android.providers.downloads.ui.view;

import com.android.providers.downloads.ui.R;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * This class customizes RelativeLayout to directly handle clicks on the left part of the view and
 * treat them at clicks on the checkbox. This makes rapid selection of many items easier. This class
 * also keeps an ID associated with the currently displayed download and notifies a listener upon
 * selection changes with that ID.
 */
public class DownloadItem extends LinearLayout {
    private static float CHECKMARK_AREA = -1;

    private long mDownloadId;
    private String mFileName;
    private String mMimeType;
    private int mPosition;

    public DownloadItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    public DownloadItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public DownloadItem(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        if (CHECKMARK_AREA == -1) {
            CHECKMARK_AREA = getResources().getDimensionPixelSize(R.dimen.checkmark_area);
        }
    }

    public void setData(long downloadId, int position, String fileName, String mimeType) {
        mDownloadId = downloadId;
        mPosition = position;
        mFileName = fileName;
        mMimeType = mimeType;
    }

    public long getDownloadId() {
        return mDownloadId;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public int getPosition() {
        return mPosition;
    }
}
