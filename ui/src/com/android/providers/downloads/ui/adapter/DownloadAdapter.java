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

package com.android.providers.downloads.ui.adapter;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.MimeTypeMap;

import miui.os.Build;

import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.fragment.DownloadListFragment;
import com.android.providers.downloads.ui.view.DownloadProgressBar;
import com.android.providers.downloads.ui.view.DownloadItem;
import com.android.providers.downloads.ui.R;

/**
 * List adapter for Cursors returned by {@link DownloadManager}.
 */
public class DownloadAdapter extends CursorAdapter {
    private final DownloadListFragment mDownloadList;
    private Cursor mCursor;
    private Resources mResources;
    private DateFormat mDateFormat;

    private final int mTitleColumnId;
    private final int mStatusColumnId;
    private final int mReasonColumnId;
    private final int mTotalBytesColumnId;
    private final int mMediaTypeColumnId;
    private final int mDateColumnId;
    private final int mIdColumnId;
    private final int mFileNameColumnId;
    private final int mCurrentBytesColumnId;
    //private final int mAllowedNetworkTypesColumnId;
    //private final int mBypassSizeLimitColumnId;
	//wait xl vip open
    private final int mCurrentDownloadSpeed;
    private final int mXlVipSpeed;

    private DownloadManager mDownloadManager;
    private int mStatusPosition;
    //save flag if a download task has speedup.
    private final Set<Long> speedUpSet=new HashSet<Long>();

    public DownloadAdapter(DownloadListFragment downloadList, Cursor cursor) {
        super(downloadList.getActivity(), cursor);
        mDownloadList = downloadList;
        mCursor = cursor;
        mResources = mDownloadList.getActivity().getResources();
        mDateFormat = DateFormat.getDateInstance(DateFormat.SHORT);

        mIdColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
        mTitleColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);
        mStatusColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
        mReasonColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON);
        mTotalBytesColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
        mMediaTypeColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE);
        mDateColumnId =
            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
        mFileNameColumnId =
            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME);
        mCurrentBytesColumnId = cursor
            .getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
        //mAllowedNetworkTypesColumnId = cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_ALLOWED_NETWORK_TYPES);
        //mBypassSizeLimitColumnId = cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT);
        mCurrentDownloadSpeed = cursor.getColumnIndexOrThrow(DownloadManager.ExtraDownloads.COLUMN_DOWNLOADING_CURRENT_SPEED);
        mXlVipSpeed =  cursor.getColumnIndexOrThrow(DownloadManager.ExtraDownloads.COLUMN_XL_ACCELERATE_SPEED);
        mDownloadManager = (DownloadManager) mDownloadList.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        mDownloadManager.setAccessAllDownloads(true);
    }

    public void setStatusPosition(int statusPosition) {
        mStatusPosition = statusPosition;
    }

    public View newView() {
        DownloadItem view = null;
        if (mStatusPosition == DownloadManager.STATUS_SUCCESSFUL && !Build.IS_TABLET) {
            view = (DownloadItem) LayoutInflater.from(mDownloadList.getActivity())
                .inflate(R.layout.download_list_finished_item_phone, null);
        } else {
            view = (DownloadItem) LayoutInflater.from(mDownloadList.getActivity())
                .inflate(R.layout.download_list_item, null);
        }
        return view;
    }
    //get title with suffix
    private String getFullTitle(String title,String extension)
    {
    	if (!TextUtils.isEmpty(title)) {
            int dotIndex = title.lastIndexOf(".");
            boolean missingExtension = dotIndex < 0 || dotIndex < title.lastIndexOf('/')
                || dotIndex == (title.length() - 1);
            if (missingExtension) {
				if(!TextUtils.isEmpty(extension))
                    {
                        return title+"."+extension;
                    }
			}
        }
    	return title==null?"":title;
    }

    public void bindView(View convertView, int position) {
        if (!(convertView instanceof DownloadItem)) {
            return;
        }
        final long downloadId = mCursor.getLong(mIdColumnId);
        ((DownloadItem) convertView).setData(downloadId, position,
                                             mCursor.getString(mFileNameColumnId),
                                             mCursor.getString(mMediaTypeColumnId));

        // Retrieve the icon for this download
        retrieveAndSetIcon(convertView);

        final int status = mCursor.getInt(mStatusColumnId);

        // set title
        String title = mCursor.getString(mTitleColumnId);
        // get extension
        String mediaType = mCursor.getString(mMediaTypeColumnId);
        String filename = mCursor.getString(mFileNameColumnId);
        String extension = getExtension(mediaType, filename);
        //show title with suffix
        title=getFullTitle(title,extension);
        if (title.isEmpty()) {
            title = mResources.getString(R.string.missing_title);
        }
        TextView titleView = (TextView) convertView.findViewById(R.id.download_title);
        titleView.setText(title);
        setTextForView(convertView, R.id.size_info, getSizeText());

        // set action button
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            DownloadProgressBar actionButton = (DownloadProgressBar) convertView.findViewById(R.id.action_button);
            setProgressBarStatus(actionButton, status);
            actionButton.setOnClickListener(new View.OnClickListener() {
                    long  id                  =     mCursor.getLong(mIdColumnId);
                    int   status              =     mCursor.getInt(mStatusColumnId);
                    //int   bypassValue         =     mCursor.getInt(mBypassSizeLimitColumnId);
                    int   bypassValue         =     0;
                    long  currentBytes        =     mCursor.getLong(mCurrentBytesColumnId);
                    long  totalBytes          =     mCursor.getLong(mTotalBytesColumnId);
                    @Override
                    public void onClick(View v) {
                        if (DownloadManager.STATUS_PENDING == status || DownloadManager.STATUS_RUNNING == status) {
                            mDownloadManager.pauseDownload(id);
                        } else if (DownloadManager.STATUS_PAUSED == status) {
                            if (!DownloadUtils.isNetworkAvailable(mContext)) {
                                Toast.makeText(mContext, R.string.retry_after_network_available, Toast.LENGTH_SHORT).show();
                            } else {
                                // Start SizeLimitActivity if the following conditions are all satisfied
                                //    1): Wi-Fi unavailable
                                //    2): BypassSizeLimit is not set
                                //    3): remain bytes are more than max download bytes in mobile
                                long maxBytesValue = DownloadUtils.getRecommendedMaxBytesOverMobile(mContext);
                                if ( !DownloadUtils.isWifiAvailable(mContext) && (bypassValue == 0) && ((totalBytes - currentBytes) > maxBytesValue) ) {
                                    DownloadUtils.notifyPauseDueToSize(mContext, id, false);
                                } else {
                                    mDownloadManager.resumeDownload(id);
                                }
                            }
                        } else if (DownloadManager.STATUS_FAILED == status) {
                            mDownloadManager.restartDownload(id);
                        }
                    }
                });
            if (mDownloadList.isEditable()) {
                actionButton.setVisibility(View.GONE);
            } else {
                actionButton.setVisibility(View.VISIBLE);
            }

            long totalBytes = mCursor.getLong(mTotalBytesColumnId);
            if (totalBytes < 0) {
                totalBytes = 0L;
            }

            long currentBytes = mCursor.getLong(mCurrentBytesColumnId);
            int progressAmount = 0;
            if (totalBytes > 0 && currentBytes > 0) {
                progressAmount = (int) (currentBytes * 100 / totalBytes);
            }
            actionButton.setProgress(progressAmount);
            String info;
            if (DownloadManager.STATUS_RUNNING == status
                || DownloadManager.STATUS_PENDING == status) {
                /*
                  double percent = totalBytes != 0 ? 100 * currentBytes / totalBytes * 1.0 : 0;
                  info = String.format("%.1f%%", percent);
                  setTextForView(convertView, R.id.date_status_info, info);
				//*/
				///*
				long currentspeed = mCursor.getLong(mCurrentDownloadSpeed);
                long vipspeed = mCursor.getLong(mXlVipSpeed);
                
				if (getSizeText().equals("")) {// show connect status when not get filesize				
					info = mContext.getString(R.string.download_wait_connect);
					setTextForViewAndColor(convertView, R.id.date_status_info,
                                           info, -1);
				} else if (currentspeed == 0 && DownloadManager.STATUS_RUNNING != status) {//waiting			
					info = mContext.getString(R.string.download_pending);
					setTextForViewAndColor(convertView, R.id.date_status_info,
                                           info, -1);
				} else {
					long id = mCursor.getLong(mIdColumnId);
					boolean blueFont = speedUpSet.contains(id);
					if (!blueFont) {
						if (vipspeed > 0) {
							speedUpSet.add(id);
							blueFont = true;
						}
					}
					String speedtext = "";
                    if(currentspeed >=1024*1024 && currentspeed < 10*1024*1024)
                    speedtext = DownloadUtils.convertFileSize(currentspeed,1) + "/s";
                    else    
                    speedtext=DownloadUtils.convertFileSize(currentspeed,0) + "/s";
					
					setTextForViewAndColor(convertView, R.id.date_status_info, speedtext, blueFont ? 0xff009bff : -1);
				}               
				//*/
				
            } else {
                info = mContext.getString(getStatusStringId());
				setTextForViewAndColor(convertView, R.id.date_status_info, info, -1);
            }

            //setTextForView(convertView, R.id.date_status_info, info);
        } else {
            if (Build.IS_TABLET) { // for tablet
                DownloadProgressBar actionButton = (DownloadProgressBar) convertView.findViewById(R.id.action_button);
                actionButton.setVisibility(View.GONE);
                String info = getDateString() + " " + mResources.getString(getStatusStringId());
                setTextForView(convertView, R.id.date_status_info, info);
            }
        }
    }

    private String getDateString() {
        Date date = new Date(mCursor.getLong(mDateColumnId));
        if (date.before(getStartOfToday())) {
            return mDateFormat.format(date);
        } else {
            int flagsTime = DateUtils.FORMAT_SHOW_TIME;
            if(android.text.format.DateFormat.is24HourFormat(mContext)) {
                flagsTime |= DateUtils.FORMAT_24HOUR;
            } else {
                flagsTime |= DateUtils.FORMAT_12HOUR;
            }
            return DateUtils.formatDateTime(mContext, date.getTime(), flagsTime);
        }
    }

    private Date getStartOfToday() {
        Calendar today = new GregorianCalendar();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return today.getTime();
    }

    private String getSizeText() {
        long currentBytes = mCursor.getLong(mCurrentBytesColumnId);
        long totalBytes = mCursor.getLong(mTotalBytesColumnId);
        
        //   Log.i("DownloadList", "---------------------currentBytes = " + currentBytes);
        String sizeText = "";
        if (totalBytes >= 0) {
            if ( mStatusPosition == DownloadManager.STATUS_SUCCESSFUL ) {
            	try{
                    sizeText = DownloadUtils.convertFileSize(totalBytes, 1);
                    if (!Build.IS_TABLET) { // for phone
                        String info = getDateString() + " " + mResources.getString(getStatusStringId());
                        sizeText = sizeText + " | " + info;
                    }
            	}catch(Exception e){
            		e.printStackTrace();
            	}
            } else if (mStatusPosition != -1) {
               
            	sizeText = DownloadUtils.convertFileSize(currentBytes, 1) + "/" +  DownloadUtils.convertFileSize(totalBytes, 1);
            }
        }
        return sizeText;
    }

    private int getStatusStringId() {
        switch (mCursor.getInt(mStatusColumnId)) {
        case DownloadManager.STATUS_FAILED:
            final int failReason = mCursor.getInt(mReasonColumnId);
            switch (failReason) {
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return R.string.download_error_insufficient_space;
            default:
                return R.string.download_error;
            }

        case DownloadManager.STATUS_SUCCESSFUL:
            return R.string.download_success;

        case DownloadManager.STATUS_PENDING:
            return R.string.download_pending;

        case DownloadManager.STATUS_RUNNING:
            return R.string.download_running;

        case DownloadManager.STATUS_PAUSED:
            final int reason = mCursor.getInt(mReasonColumnId);
            switch(reason) {
            case DownloadManager.PAUSED_BY_APP:
                return R.string.paused_by_app;
            case DownloadManager.PAUSE_INSUFFICIENT_SPACE:
                return R.string.paused_insufficient_space;
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                return R.string.paused_queued_for_wifi;
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                return R.string.paused_waiting_for_network;
            case DownloadManager.PAUSED_WAITING_TO_RETRY:
                return R.string.paused_waiting_to_retry;
            case DownloadManager.PAUSED_UNKNOWN:
                return R.string.paused_unknown;
            default:
                return R.string.download_running;
            }
        }
        throw new IllegalStateException("Unknown status: " + mCursor.getInt(mStatusColumnId));
    }

    // get extension of downloaded file.
    private String getExtension(String mimeType, String filename) {
        String extension = null;
        // get extension from file name first.
        if (!TextUtils.isEmpty(filename)) {
            int dotIndex = filename.lastIndexOf(".");
            boolean missingExtension = dotIndex < 0 || dotIndex < filename.lastIndexOf('/')
                || dotIndex == (filename.length() - 1);
            if (!missingExtension) {
                extension = filename.substring(dotIndex + 1);
            }
        }
        // if extension is still null, get from mimetype
        if (TextUtils.isEmpty(extension)) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }
        return extension;
    }

    // get audio file icon resource id
    private int getAudioFileIconId(String extension) {
        int resId = R.drawable.file_type_music;
        // if extension is not null, then get corresponding icon
        if (!TextUtils.isEmpty(extension)) {
            if (extension.equalsIgnoreCase("3gpp")) {
                resId = R.drawable.file_type_3gpp;
            } else if (extension.equalsIgnoreCase("amr")) {
                resId = R.drawable.file_type_amr;
            } else if (extension.equalsIgnoreCase("mid")) {
                resId = R.drawable.file_type_mid;
            } else if (extension.equalsIgnoreCase("mp3")) {
                resId = R.drawable.file_type_mp3;
            } else if (extension.equalsIgnoreCase("m4a")) {
                resId = R.drawable.file_type_m4a;
            } else if (extension.equalsIgnoreCase("wma")) {
                resId = R.drawable.file_type_wma;
            } else if (extension.equalsIgnoreCase("wav")) {
                resId = R.drawable.file_type_wav;
            } else if (extension.equalsIgnoreCase("wav")) {
                resId = R.drawable.file_type_wav;
            } else if (extension.equalsIgnoreCase("ape")) {
                resId = R.drawable.file_type_ape;
            }
        }
        return resId;
    }

    // get video file icon resource id
    private int getVideoFileIconId(String extension) {
        int resId = R.drawable.file_type_video;
        // if extension is not null, then get corresponding icon
        if (!TextUtils.isEmpty(extension)) {
            if (extension.equalsIgnoreCase("3gpp")) {
                resId = R.drawable.file_type_3gpp;
            }
        }
        return resId;
    }

    // get image file icon resource id
    private int getImageFileIconId(String extension) {
        int resId = R.drawable.file_type_image;
        // if extension is not null, then get corresponding icon
        return resId;
    }

    // get text file icon resource id
    private int getTextFileIconId(String extension) {
        int resId = R.drawable.file_type_txt;
        // if extension is not null, then get corresponding icon
        if (!TextUtils.isEmpty(extension)) {
            if (extension.equalsIgnoreCase("xml")) {
                resId = R.drawable.file_type_xml;
            } else if (extension.equalsIgnoreCase("html")) {
                resId = R.drawable.file_type_html;
            } else if (extension.equalsIgnoreCase("vcf")) {
                resId = R.drawable.file_type_contact;
            }
        }
        return resId;
    }

    // get binary file icon resource id
    private int getBinaryFileIconId(String extension) {
        int resId = R.drawable.file_type_unknown;
        // if extension is not null, then get corresponding icon
        if (!TextUtils.isEmpty(extension)) {
            if (extension.equalsIgnoreCase("doc") || extension.equalsIgnoreCase("docx")
                || extension.equalsIgnoreCase("dot") || extension.equalsIgnoreCase("dotx")) {
                resId = R.drawable.file_type_doc;
            } else if (extension.equalsIgnoreCase("xls") || extension.equalsIgnoreCase("xlsx")
                       || extension.equalsIgnoreCase("xlt") || extension.equalsIgnoreCase("xltx")) {
                resId = R.drawable.file_type_xls;
            } else if (extension.equalsIgnoreCase("ppt") || extension.equalsIgnoreCase("pptx")
                       || extension.equalsIgnoreCase("pot") || extension.equalsIgnoreCase("potx")) {
                resId = R.drawable.file_type_ppt;
            } else if (extension.equalsIgnoreCase("pps") || extension.equalsIgnoreCase("ppsx")) {
                resId = R.drawable.file_type_pps;
            } else if (extension.equalsIgnoreCase("pdf")) {
                resId = R.drawable.file_type_pdf;
            } else if (extension.equalsIgnoreCase("ogg")) {
                resId = R.drawable.file_type_ogg;
            } else if (extension.equalsIgnoreCase("rar")) {
                resId = R.drawable.file_type_rar;
            } else if (extension.equalsIgnoreCase("zip")) {
                resId = R.drawable.file_type_zip;
            } else if (extension.equalsIgnoreCase("flac")) {
                resId = R.drawable.file_type_flac;
            } else if (extension.equalsIgnoreCase("apk")) {
                resId = R.drawable.file_type_apk;
            }
        }
        return resId;
    }

    // get icon resource id for other file type
    private int getOtherFileIconId(String extension) {
        int resId = R.drawable.file_type_unknown;
        // if extension is not null, then get corresponding icon
        if (!TextUtils.isEmpty(extension)) {
            if (extension.equalsIgnoreCase("dps")) {
                resId = R.drawable.file_type_dps;
            } else if (extension.equalsIgnoreCase("dpt")) {
                resId = R.drawable.file_type_dpt;
            } else if (extension.equalsIgnoreCase("et")) {
                resId = R.drawable.file_type_et;
            } else if (extension.equalsIgnoreCase("ett")) {
                resId = R.drawable.file_type_ett;
            } else if (extension.equalsIgnoreCase("wps")) {
                resId = R.drawable.file_type_wps;
            } else if (extension.equalsIgnoreCase("wpt")) {
                resId = R.drawable.file_type_wpt;
            }
        }
        return resId;
    }

    private void retrieveAndSetIcon(View convertView) {
        ImageView iconView = (ImageView) convertView.findViewById(R.id.download_icon);
        String mediaType = mCursor.getString(mMediaTypeColumnId);
        String filename = mCursor.getString(mFileNameColumnId);
        String extension = getExtension(mediaType, filename);
        int resId = R.drawable.file_type_unknown;
        if (MimeTypeMap.getSingleton().hasMimeType(mediaType)) {
            if (mediaType.startsWith("audio/")) {
                resId = getAudioFileIconId(extension);
            } else if (mediaType.startsWith("image/")) {
                resId = getImageFileIconId(extension);
            } else if (mediaType.startsWith("text/")) {
                resId = getTextFileIconId(extension);
            } else if (mediaType.startsWith("video/")) {
                resId = getVideoFileIconId(extension);
            } else if (mediaType.startsWith("application/")) {
                resId = getBinaryFileIconId(extension);
            }
        } else {
            resId = getOtherFileIconId(extension);
        }
        iconView.setImageResource(resId);
        iconView.setVisibility(View.VISIBLE);
    }

    private void setTextForView(View parent, int textViewId, String text) {
        TextView view = (TextView) parent.findViewById(textViewId);
        view.setVisibility(View.VISIBLE);
        view.setText(text);
    }

    /**
     * @param color if color is -1, then need not set text color, otherwise should set text color.
     */
    private void setTextForViewAndColor(View parent, int textViewId, String text, int color) {
        TextView view = (TextView) parent.findViewById(textViewId);
        view.setVisibility(View.VISIBLE);
        view.setText(text);
        if (color != -1) {
            view.setTextColor(color);
        } else {
            view.setTextAppearance(mContext, miui.R.style.TextAppearance_List_Secondary);
        }
    }

    // CursorAdapter overrides
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return newView();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        bindView(view, cursor.getPosition());
    }

    private void setProgressBarStatus(DownloadProgressBar progressBar, int status) {
        if (progressBar == null) {
            return;
        }
        switch(status) {
        case DownloadManager.STATUS_FAILED:
            progressBar.setDownloadStatus(DownloadProgressBar.STATUS_FAILED);
            break;
        case DownloadManager.STATUS_PENDING:
        case DownloadManager.STATUS_RUNNING:
            progressBar.setDownloadStatus(DownloadProgressBar.STATUS_DOWNLOADING);
            break;
        case DownloadManager.STATUS_PAUSED:
            final int reason = mCursor.getInt(mReasonColumnId);
            if (reason == DownloadManager.PAUSED_BY_APP) {
                progressBar.setDownloadStatus(DownloadProgressBar.STATUS_PAUSE_BY_APP);
            } else {
                progressBar.setDownloadStatus(DownloadProgressBar.STATUS_PAUSE);
            }
            break;
        }
    }
}
