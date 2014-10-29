/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import android.app.DownloadManager;
import android.app.DownloadManager.ExtraDownloads;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.os.Environment;
import android.provider.Downloads;
import android.provider.Downloads.Impl;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.IndentingPrintWriter;
import com.xunlei.downloadplatforms.XLDownloadManager;
import com.xunlei.downloadplatforms.util.XLUtil;

/**
 * Stores information about an individual download.
 */
public class DownloadInfo {
    // TODO: move towards these in-memory objects being sources of truth, and
    // periodically pushing to provider.
    private static final String TAG = "DownloadInfo";

    public static final int MAX_BYTES_OVER_MOBILE = 2147483647;

    public static class Reader {
        private ContentResolver mResolver;
        private Cursor mCursor;

        public Reader(ContentResolver resolver, Cursor cursor) {
            mResolver = resolver;
            mCursor = cursor;
        }

        public DownloadInfo newDownloadInfo(Context context, SystemFacade systemFacade,
                StorageManager storageManager, DownloadNotifier notifier, boolean xlEngineFlag, XLDownloadManager dm, SharedPreferences pf) {
            final DownloadInfo info = new DownloadInfo(
                    context, systemFacade, storageManager, notifier, dm, pf);
            try {
            	updateFromDatabase(info);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}

            // if a new task, rewrite this flag
            info.mXlTaskOpenMark = checkDownloadEngine(info.mPackage, info.mAppointName, xlEngineFlag);
            readRequestHeaders(info);
            return info;
        }

        public void updateFromDatabase(DownloadInfo info) {
            info.mId = getLong(Downloads.Impl._ID);
            info.mUri = getString(Downloads.Impl.COLUMN_URI);
            info.mUriDomain = getUriDomain(info.mUri);
            info.mNoIntegrity = getInt(Downloads.Impl.COLUMN_NO_INTEGRITY) == 1;
            info.mHint = getString(Downloads.Impl.COLUMN_FILE_NAME_HINT);
            info.mFileName = getString(Downloads.Impl._DATA);
            info.mMimeType = getString(Downloads.Impl.COLUMN_MIME_TYPE);
            info.mDestination = getInt(Downloads.Impl.COLUMN_DESTINATION);
            info.mVisibility = getInt(Downloads.Impl.COLUMN_VISIBILITY);
            if (info.mStatus != Downloads.Impl.STATUS_SUCCESS) {
                int cursorStatus = getInt(Downloads.Impl.COLUMN_STATUS);
                // Access database to get the newest status of the download.
                // This is necessary as cursor could be out of date, especially when it was created long ago.
                // By doing this, we can prevent a download from being executed more than once.

	            Cursor latestCursor = null;
	            try {
		            latestCursor = mResolver.query(info.getAllDownloadsUri(), null, null, null, null);
		            if (latestCursor != null && latestCursor.moveToNext()) {
			            info.mStatus = getInt(latestCursor, Downloads.Impl.COLUMN_STATUS);
		            } else {
			            info.mStatus = cursorStatus;
		            }
	            }catch (Exception e){
		            XLConfig.LOGD(TAG,e.toString());
	            }finally {
		            if(latestCursor != null){
			            latestCursor.close();
		            }
	            }
            }

            info.mNumFailed = getInt(Downloads.Impl.COLUMN_FAILED_CONNECTIONS);
            int retryRedirect = getInt(Constants.RETRY_AFTER_X_REDIRECT_COUNT);
            info.mRetryAfter = retryRedirect & 0xfffffff;
            info.mLastMod = getLong(Downloads.Impl.COLUMN_LAST_MODIFICATION);
            info.mPackage = getString(Downloads.Impl.COLUMN_NOTIFICATION_PACKAGE);
            info.mClass = getString(Downloads.Impl.COLUMN_NOTIFICATION_CLASS);
            info.mExtras = getString(Downloads.Impl.COLUMN_NOTIFICATION_EXTRAS);
            info.mCookies = getString(Downloads.Impl.COLUMN_COOKIE_DATA);
            info.mUserAgent = getString(Downloads.Impl.COLUMN_USER_AGENT);
            info.mReferer = getString(Downloads.Impl.COLUMN_REFERER);
            //info.mTotalBytes = getLong(Downloads.Impl.COLUMN_TOTAL_BYTES);
            long cursorBytes = getLong(Downloads.Impl.COLUMN_TOTAL_BYTES);
            if (info.mTotalBytes <= 0 || cursorBytes > info.mTotalBytes) {
                info.mTotalBytes = cursorBytes;
            }
            info.mCurrentBytes = getLong(Downloads.Impl.COLUMN_CURRENT_BYTES);
            info.mETag = getString(Constants.ETAG);
            info.mIfRange = getString(ExtraDownloads.COLUMN_IF_RANGE_ID);
            info.mUid = getInt(Constants.UID);
            info.mMediaScanned = getInt(Constants.MEDIA_SCANNED);
            info.mDeleted = getInt(Downloads.Impl.COLUMN_DELETED) == 1;
            info.mMediaProviderUri = getString(Downloads.Impl.COLUMN_MEDIAPROVIDER_URI);
            info.mIsPublicApi = getInt(Downloads.Impl.COLUMN_IS_PUBLIC_API) != 0;
            info.mAllowedNetworkTypes = getInt(Downloads.Impl.COLUMN_ALLOWED_NETWORK_TYPES);
            info.mAllowRoaming = getInt(Downloads.Impl.COLUMN_ALLOW_ROAMING) != 0;
            info.mAllowMetered = getInt(Downloads.Impl.COLUMN_ALLOW_METERED) != 0;
            info.mTitle = getString(Downloads.Impl.COLUMN_TITLE);
            info.mDescription = getString(Downloads.Impl.COLUMN_DESCRIPTION);
            info.mBypassRecommendedSizeLimit =
                    getInt(Downloads.Impl.COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT);
            //v5 have  v6 don't have
			//info.mSubDirectory = getString(ExtraDownloads.COLUMN_SUB_DIRECTORY);
            //info.mAppointName = getString(ExtraDownloads.COLUMN_APPOINT_NAME);
            // Read xunlei relevant fields
            info.mFileCreateTime = getLong(ExtraDownloads.COLUMN_FILE_CREATE_TIME);
//            info.mFileCreateTime = System.currentTimeMillis();  // close by hzg 20140923
            info.mDownloadingCurrentSpeed = getLong(ExtraDownloads.COLUMN_DOWNLOADING_CURRENT_SPEED);
            info.mDownloadSurplustime = getLong(ExtraDownloads.COLUMN_DOWNLOAD_SURPLUS_TIME);
            info.mXlAccelerateSpeed = getLong(ExtraDownloads.COLUMN_XL_ACCELERATE_SPEED);
            info.mDownloadedTime = getLong(ExtraDownloads.COLUMN_DOWNLOADED_TIME);
            info.mXlVipStatus = getInt(ExtraDownloads.COLUMN_XL_VIP_STATUS);
            info.mXlVipCdnUrl =  getString(ExtraDownloads.COLUMN_XL_VIP_CDN_URL);
            // needed by updateDownload function in DownloadService, not working in insertDownloadLocked
            info.mXlTaskOpenMark = getInt(ExtraDownloads.COLUMN_XL_TASK_OPEN_MARK);
            // end

            if (info.mStatus != Downloads.Impl.STATUS_INSUFFICIENT_SPACE_ERROR) {
                info.mInsufficientAlreadyPosted = false;
            }

            synchronized (this) {
                info.mControl = getInt(Downloads.Impl.COLUMN_CONTROL);
            }
        }

        private String getUriDomain(String uriString) {
            if (TextUtils.isEmpty(uriString)) {
                return null;
            }

            URI uri = null;
            try {
                uri = URI.create(uriString);
            } catch (IllegalArgumentException e) {
                Log.e(Constants.TAG, "Illegal url:" + uriString, e);
                return null;
            }
            String host = null;
            if (!TextUtils.isEmpty(uri.toString())) {
                host = uri.getHost();
                if (!TextUtils.isEmpty(host)) {
                    host = host.toLowerCase();
                }
            }
            return host;
        }

        private void readRequestHeaders(DownloadInfo info) {
        	Cursor cursor;
            info.mRequestHeaders.clear();
            Uri headerUri = Uri.withAppendedPath(
                    info.getAllDownloadsUri(), Downloads.Impl.RequestHeaders.URI_SEGMENT);
            try{
             cursor = mResolver.query(headerUri, null, null, null, null);
            }catch(Exception e){
            	e.printStackTrace();
            	return;
            }
            
            try {
                int headerIndex =
                        cursor.getColumnIndexOrThrow(Downloads.Impl.RequestHeaders.COLUMN_HEADER);
                int valueIndex =
                        cursor.getColumnIndexOrThrow(Downloads.Impl.RequestHeaders.COLUMN_VALUE);
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    addHeader(info, cursor.getString(headerIndex), cursor.getString(valueIndex));
                }
            }catch(Exception e){ 
            	e.printStackTrace();
            }
            finally {
                cursor.close();
            }

            if (info.mCookies != null) {
                addHeader(info, "Cookie", info.mCookies);
            }
            if (info.mReferer != null) {
                addHeader(info, "Referer", info.mReferer);
            }
        }

        private void addHeader(DownloadInfo info, String header, String value) {
            info.mRequestHeaders.add(Pair.create(header, value));
        }

        private String getString(String column) {
            int index = mCursor.getColumnIndexOrThrow(column);
            String s = mCursor.getString(index);
            return (TextUtils.isEmpty(s)) ? null : s;
        }

        private Integer getInt(String column) {
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(column));
        }

        private Integer getInt(Cursor cursor, String column) {
            return cursor.getInt(cursor.getColumnIndexOrThrow(column));
        }

        private Long getLong(String column) {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(column));
        }
    }

    /**
     * Constants used to indicate network state for a specific download, after
     * applying any requested constraints.
     */
    public enum NetworkState {
        /**
         * The network is usable for the given download.
         */
        OK,

        /**
         * There is no network connectivity.
         */
        NO_CONNECTION,

        /**
         * The download exceeds the maximum size for this network.
         */
        UNUSABLE_DUE_TO_SIZE,

        /**
         * The download exceeds the recommended maximum size for this network,
         * the user must confirm for this download to proceed without WiFi.
         */
        RECOMMENDED_UNUSABLE_DUE_TO_SIZE,

        /**
         * The current connection is roaming, and the download can't proceed
         * over a roaming connection.
         */
        CANNOT_USE_ROAMING,

        /**
         * The app requesting the download specific that it can't use the
         * current network connection.
         */
        TYPE_DISALLOWED_BY_REQUESTOR,

        /**
         * Current network is blocked for requesting application.
         */
        BLOCKED;
    }

    /**
     * For intents used to notify the user that a download exceeds a size threshold, if this extra
     * is true, WiFi is required for this download size; otherwise, it is only recommended.
     */
    public static final String EXTRA_IS_WIFI_REQUIRED = "isWifiRequired";

    public long mId;
    public String mUri;
    public String mUriDomain;
    public boolean mNoIntegrity;
    public String mHint;
    public String mFileName;
    public String mMimeType;
    public int mDestination;
    public int mVisibility;
    public int mControl;
    public int mStatus;
    public int mNumFailed;
    public int mRetryAfter;
    public long mLastMod;
    public String mPackage;
    public String mClass;
    public String mExtras;
    public String mCookies;
    public String mUserAgent;
    public String mReferer;
    public long mTotalBytes;
    public long mCurrentBytes;
    public String mETag;
    public String mIfRange;
    public int mUid;
    public int mMediaScanned;
    public boolean mDeleted;
    public String mMediaProviderUri;
    public boolean mIsPublicApi;
    public int mAllowedNetworkTypes;
    public boolean mAllowRoaming;
    public boolean mAllowMetered;
    public String mTitle;
    public String mDescription;
    public int mBypassRecommendedSizeLimit;
    public String mSubDirectory;
    public String mAppointName;
    public boolean mInsufficientAlreadyPosted;
    public long mFileCreateTime;
    public long mDownloadingCurrentSpeed;
    public long mDownloadSurplustime;
    public long mXlAccelerateSpeed;
    public long mDownloadedTime;
    public int mXlVipStatus;
    public String mXlVipCdnUrl;
    public int mXlTaskOpenMark;

    public int mFuzz;
    // whether SizeLimitActivity has shown for one time.
    public boolean mHasNotifyDueToSize = false;

    private List<Pair<String, String>> mRequestHeaders = new ArrayList<Pair<String, String>>();

    /**
     * Result of last {@link DownloadThread} started by
     * {@link #startDownloadIfReady(ExecutorService)}.
     */
    @GuardedBy("this")
    private Future<?> mSubmittedTask;

    @GuardedBy("this")
    private DownloadThread mTask;

    private final Context mContext;
    private final SystemFacade mSystemFacade;
    private final StorageManager mStorageManager;
    private final DownloadNotifier mNotifier;
    
    private XLDownloadManager mXlDownloadManager = null;
    private SharedPreferences mPreference;

    private DownloadInfo(Context context, SystemFacade systemFacade, StorageManager storageManager,
            DownloadNotifier notifier, XLDownloadManager dm, SharedPreferences pf) {
        mContext = context;
        mSystemFacade = systemFacade;
        mInsufficientAlreadyPosted = false;
        mStorageManager = storageManager;
        mNotifier = notifier;
        mFuzz = Helpers.sRandom.nextInt(1001);
        mXlDownloadManager = dm;
        mPreference = pf;
    }

    public Collection<Pair<String, String>> getHeaders() {
        return Collections.unmodifiableList(mRequestHeaders);
    }

    public void sendIntentIfRequested() {
        if (mPackage == null) {
            return;
        }

        Intent intent;
        if (mIsPublicApi) {
            intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            intent.setPackage(mPackage);
            intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, mId);
        } else { // legacy behavior
            if (mClass == null) {
                return;
            }
            intent = new Intent(Downloads.Impl.ACTION_DOWNLOAD_COMPLETED);
            intent.setClassName(mPackage, mClass);
            if (mExtras != null) {
                intent.putExtra(Downloads.Impl.COLUMN_NOTIFICATION_EXTRAS, mExtras);
            }
            // We only send the content: URI, for security reasons. Otherwise, malicious
            //     applications would have an easier time spoofing download results by
            //     sending spoofed intents.
            intent.setData(getMyDownloadsUri());
        }
        mSystemFacade.sendBroadcast(intent);
    }
    /**
     * Send download progress update intent if request
     * @hide
     */
    public void sendDownloadProgressUpdateIntent() {
        if (mPackage == null) {
            return;
        }

        Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_UPDATED);
        intent.setPackage(mPackage);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, mId);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_CURRENT_BYTES, mCurrentBytes);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_TOTAL_BYTES, mTotalBytes);
        mSystemFacade.sendBroadcast(intent);
    }
    /**
     * Returns the time when a download should be restarted.
     */
    public long restartTime(long now) {
        if (mNumFailed == 0) {
            return now;
        }
        if (mRetryAfter > 0) {
            return mLastMod + mRetryAfter;
        }
        return mLastMod +
                Constants.RETRY_FIRST_DELAY *
                    (1000 + mFuzz) * (1 << (mNumFailed - 1));
    }

    /**
     * Returns whether this download should be enqueued.
     */
    private boolean isReadyToDownload(boolean isActive) {
        if (TextUtils.isEmpty(mUriDomain)) {
            if (mDestination != Downloads.Impl.DESTINATION_NON_DOWNLOADMANAGER_DOWNLOAD) {
                Log.e(Constants.TAG, "Unknown domain for uri: " + mUri);
            }
            return false;
        }
        if (!isActive && Helpers.sDownloadsDomainCountMap.containsKey(mUriDomain)
                && Helpers.sDownloadsDomainCountMap.get(mUriDomain) >= Helpers.sMaxDownloadsCountPerDomain) {
            return false;
        }
        if (mControl == Downloads.Impl.CONTROL_PAUSED) {
            // the download is paused, so it's not going to start
            return false;
        }
        switch (mStatus) {
            case 0: // status hasn't been initialized yet, this is a new download
            case Downloads.Impl.STATUS_PENDING: // download is explicit marked as ready to start
            case Downloads.Impl.STATUS_RUNNING: // download interrupted (process killed etc) while
                                                // running, without a chance to update the database
                return true;

            case Downloads.Impl.STATUS_WAITING_FOR_NETWORK:
            case Downloads.Impl.STATUS_QUEUED_FOR_WIFI:
                return checkCanUseNetwork(false) == NetworkState.OK;

            case Downloads.Impl.STATUS_WAITING_TO_RETRY:
                // download was waiting for a delayed restart
                final long now = mSystemFacade.currentTimeMillis();
                return restartTime(now) <= now;
            case Downloads.Impl.STATUS_DEVICE_NOT_FOUND_ERROR:
                // is the media mounted?
                return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
            case Downloads.Impl.STATUS_INSUFFICIENT_SPACE_ERROR:
                // should check space to make sure it is worth retrying the download.
                return hasEnoughSpace();
        }
        return false;
    }

    /**
     * Returns whether this download has a visible notification after
     * completion.
     */
    public boolean hasCompletionNotification() {
        if (!Downloads.Impl.isStatusCompleted(mStatus)) {
            return false;
        }
        if (mVisibility == Downloads.Impl.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this download is allowed to use the network.
     */
    public NetworkState checkCanUseNetwork(boolean isTaskRunning) {
        final NetworkInfo info = mSystemFacade.getActiveNetworkInfo(mUid);
        if (info == null || !info.isConnected()) {
            return NetworkState.NO_CONNECTION;
        }
        if (DetailedState.BLOCKED.equals(info.getDetailedState())) {
            return NetworkState.BLOCKED;
        }
        if (mSystemFacade.isNetworkRoaming() && !isRoamingAllowed()) {
            return NetworkState.CANNOT_USE_ROAMING;
        }
        if (mSystemFacade.isActiveNetworkMetered() && !mAllowMetered) {
            return NetworkState.TYPE_DISALLOWED_BY_REQUESTOR;
        }
        return checkIsNetworkTypeAllowed(isTaskRunning, info.getType());
    }
    
    private boolean isRoamingAllowed() {
        if (mIsPublicApi) {
            return mAllowRoaming;
        } else { // legacy behavior
            return mDestination != Downloads.Impl.DESTINATION_CACHE_PARTITION_NOROAMING;
        }
    }

    /**
     * Check if this download can proceed over the given network type.
     * @param networkType a constant from ConnectivityManager.TYPE_*.
     * @return one of the NETWORK_* constants
     */
    private NetworkState checkIsNetworkTypeAllowed(boolean isTaskRunning, int networkType) {
        if (mIsPublicApi) {
            final int flag = translateNetworkTypeToApiFlag(networkType);
            final boolean allowAllNetworkTypes = mAllowedNetworkTypes == ~0;
            if (!allowAllNetworkTypes && (flag & mAllowedNetworkTypes) == 0) {
                return NetworkState.TYPE_DISALLOWED_BY_REQUESTOR;
            }
        }
        return checkSizeAllowedForNetwork(isTaskRunning, networkType);
    }

    /**
     * Translate a ConnectivityManager.TYPE_* constant to the corresponding
     * DownloadManager.Request.NETWORK_* bit flag.
     */
    private int translateNetworkTypeToApiFlag(int networkType) {
        switch (networkType) {
            case ConnectivityManager.TYPE_MOBILE:
                return DownloadManager.Request.NETWORK_MOBILE;

            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_ETHERNET:
                return DownloadManager.Request.NETWORK_WIFI;

            case ConnectivityManager.TYPE_BLUETOOTH:
                return DownloadManager.Request.NETWORK_BLUETOOTH;

            default:
                return 0;
        }
    }

    /**
     * Check if the download's size prohibits it from running over the current network.
     * @return one of the NETWORK_* constants
     */
    private NetworkState checkSizeAllowedForNetwork(boolean isTaskRunning, int networkType) {
        Long recommendedMaxBytesOverMobile = mSystemFacade.getRecommendedMaxBytesOverMobile();
        if ((recommendedMaxBytesOverMobile != null && recommendedMaxBytesOverMobile == MAX_BYTES_OVER_MOBILE) && networkType == ConnectivityManager.TYPE_MOBILE) {
            return NetworkState.OK;
        }

    	if (mTotalBytes <= 0) {
    		if (!isTaskRunning) {
                if (mStatus == Downloads.Impl.STATUS_QUEUED_FOR_WIFI && networkType == ConnectivityManager.TYPE_MOBILE) {
                    return NetworkState.RECOMMENDED_UNUSABLE_DUE_TO_SIZE;
                }
    			return NetworkState.OK; // we don't know the size yet
    		} else {
    			if (networkType == ConnectivityManager.TYPE_MOBILE) {
        			return NetworkState.RECOMMENDED_UNUSABLE_DUE_TO_SIZE; // we don't know the size yet
    			}
    		}
        }

        if (networkType == ConnectivityManager.TYPE_WIFI ||
            networkType == ConnectivityManager.TYPE_ETHERNET) {
            return NetworkState.OK; // anything goes over wifi
        }

        Long maxBytesOverMobile = mSystemFacade.getMaxBytesOverMobile();
        if (maxBytesOverMobile != null && mTotalBytes > maxBytesOverMobile) {
            return NetworkState.UNUSABLE_DUE_TO_SIZE;
        }
        if (mBypassRecommendedSizeLimit == 0) {
            if (recommendedMaxBytesOverMobile != null
                    && mTotalBytes > recommendedMaxBytesOverMobile) {
                return NetworkState.RECOMMENDED_UNUSABLE_DUE_TO_SIZE;
            }
        }
        return NetworkState.OK;
    }

    /**
     * If download is ready to start, and isn't already pending or executing,
     * create a {@link DownloadThread} and enqueue it into given
     * {@link Executor}.
     *
     * @return If actively downloading.
     */
    public boolean startDownloadIfReady(ExecutorService executor) {
        synchronized (this) {
            final boolean isActive = mSubmittedTask != null && !mSubmittedTask.isDone();
            final boolean isReady = isReadyToDownload(isActive);
            if (isReady && !isActive) {
                if (mStatus != Impl.STATUS_RUNNING) {
                    mStatus = Impl.STATUS_RUNNING;
                    ContentValues values = new ContentValues();
                    values.put(Impl.COLUMN_STATUS, mStatus);
                    mContext.getContentResolver().update(getAllDownloadsUri(), values, null, null);
                }
                if (mVisibility != Impl.VISIBILITY_HIDDEN && mStatus == Impl.STATUS_RUNNING) {
                    synchronized (Helpers.sDownloadsDomainCountMap) {
                        Helpers.sDownloadsDomainCountMap.put(mUriDomain, Helpers.sDownloadsDomainCountMap.containsKey(mUriDomain) ?
                            (Helpers.sDownloadsDomainCountMap.get(mUriDomain)+1) : 1);
                    }
                }
                mTask = new DownloadThread(
                        mContext, mSystemFacade, this, mStorageManager, mNotifier, mXlDownloadManager, mPreference);
                mSubmittedTask = executor.submit(mTask);
            }
            return isReady;
        }
    }

    /**
     * If download is ready to be scanned, enqueue it into the given
     * {@link DownloadScanner}.
     *
     * @return If actively scanning.
     */
    public boolean startScanIfReady(DownloadScanner scanner) {
        synchronized (this) {
            final boolean isReady = shouldScanFile();
            if (isReady) {
                scanner.requestScan(this);
            }
            return isReady;
        }
    }

    public boolean isOnCache() {
        return (mDestination == Downloads.Impl.DESTINATION_CACHE_PARTITION
                || mDestination == Downloads.Impl.DESTINATION_SYSTEMCACHE_PARTITION
                || mDestination == Downloads.Impl.DESTINATION_CACHE_PARTITION_NOROAMING
                || mDestination == Downloads.Impl.DESTINATION_CACHE_PARTITION_PURGEABLE);
    }

    public Uri getMyDownloadsUri() {
        return ContentUris.withAppendedId(Downloads.Impl.CONTENT_URI, mId);
    }

    public Uri getAllDownloadsUri() {
        return ContentUris.withAppendedId(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, mId);
    }

    public void dump(IndentingPrintWriter pw) {
        pw.println("DownloadInfo:");
        pw.increaseIndent();

        pw.printPair("mId", mId);
        pw.printPair("mLastMod", mLastMod);
        pw.printPair("mPackage", mPackage);
        pw.printPair("mUid", mUid);
        pw.println();

        pw.printPair("mUri", mUri);
        pw.println();

        pw.printPair("mMimeType", mMimeType);
        pw.printPair("mCookies", (mCookies != null) ? "yes" : "no");
        pw.printPair("mReferer", (mReferer != null) ? "yes" : "no");
        pw.printPair("mUserAgent", mUserAgent);
        pw.println();

        pw.printPair("mFileName", mFileName);
        pw.printPair("mDestination", mDestination);
        pw.println();

        pw.printPair("mStatus", Downloads.Impl.statusToString(mStatus));
        pw.printPair("mCurrentBytes", mCurrentBytes);
        pw.printPair("mTotalBytes", mTotalBytes);
        pw.println();

        pw.printPair("mNumFailed", mNumFailed);
        pw.printPair("mRetryAfter", mRetryAfter);
        pw.printPair("mETag", mETag);
        pw.printPair("mIsPublicApi", mIsPublicApi);
        pw.println();

        pw.printPair("mAllowedNetworkTypes", mAllowedNetworkTypes);
        pw.printPair("mAllowRoaming", mAllowRoaming);
        pw.printPair("mAllowMetered", mAllowMetered);
        pw.println();

        pw.decreaseIndent();
    }

    /**
     * Return time when this download will be ready for its next action, in
     * milliseconds after given time.
     *
     * @return If {@code 0}, download is ready to proceed immediately. If
     *         {@link Long#MAX_VALUE}, then download has no future actions.
     */
    public long nextActionMillis(long now) {
        if (Downloads.Impl.isStatusCompleted(mStatus)) {
            return Long.MAX_VALUE;
        }
        if (mStatus != Downloads.Impl.STATUS_WAITING_TO_RETRY) {
            return 0;
        }
        long when = restartTime(now);
        if (when <= now) {
            return 0;
        }
        return when - now;
    }

    /**
     * Returns whether a file should be scanned
     */
    public boolean shouldScanFile() {
        return (mMediaScanned == 0)
                && (mDestination == Downloads.Impl.DESTINATION_EXTERNAL ||
                        mDestination == Downloads.Impl.DESTINATION_FILE_URI && Helpers.shouldBeScanned(mHint) ||
                        mDestination == Downloads.Impl.DESTINATION_NON_DOWNLOADMANAGER_DOWNLOAD)
                && Downloads.Impl.isStatusSuccess(mStatus);
    }

    boolean hasEnoughSpace() {
        boolean spaceIsEnough = false;
        try {
            mStorageManager.verifySpaceBeforeWritingToFile(mDestination, mFileName, mTotalBytes - mCurrentBytes);
            spaceIsEnough = true;
        } catch (StopRequestException e) {
        } catch (IllegalArgumentException e) {
        }
        return spaceIsEnough;
    }

    void notifyPauseDueToSize(boolean isWifiRequired) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(getAllDownloadsUri());
        intent.setClassName(SizeLimitActivity.class.getPackage().getName(),
                SizeLimitActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_IS_WIFI_REQUIRED, isWifiRequired);
        mContext.startActivity(intent);
    }

    /**
     * Whether Send insufficient space nofification
     * @hide
     */
    boolean hasPostNotificationDueToInsufficientSpace() {
        return mInsufficientAlreadyPosted;
    }

    /**
     * get whether Send insufficient space nofification
     * @hide
     */
    void setPostNotificationDueToInsufficientSpace() {
        mInsufficientAlreadyPosted = true;
    }
    /**
     * get notification string
     * @hide
     */
    String getNotificationStringOfInsufficientSpace() {
        int notifyResId = isOnExternalStorage(getLocalUri()) ?
                R.string.dialog_insufficient_space_on_external :
                R.string.dialog_insufficient_space_on_cache;
        return mContext.getString(notifyResId);
    }

    /**
     * Query and return status of requested download.
     */
    public static int queryDownloadStatus(ContentResolver resolver, long id) {
        final Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI, id),
                new String[] { Downloads.Impl.COLUMN_STATUS }, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                // TODO: increase strictness of value returned for unknown
                // downloads; this is safe default for now.
                return Downloads.Impl.STATUS_PENDING;
            }
        } finally {
            cursor.close();
        }
    }

    /*private*/ boolean isOnExternalStorage(String localUriString) {
        if (localUriString == null) {
            return false;
        }
        Uri localUri = Uri.parse(localUriString);
        if (!localUri.getScheme().equals("file")) {
            return false;
        }
        String path = localUri.getPath();
        String externalRoot = Environment.getExternalStorageDirectory().getPath();
        return path.startsWith(externalRoot);
    }

    /*private*/ String getLocalUri() {
        switch(mDestination) {
            case Downloads.Impl.DESTINATION_FILE_URI:
                return mHint;
            case Downloads.Impl.DESTINATION_EXTERNAL:
            case Downloads.Impl.DESTINATION_NON_DOWNLOADMANAGER_DOWNLOAD:
                return mFileName != null ? Uri.fromFile(new File(mFileName)).toString() : null;
            default:
                return getAllDownloadsUri().toString();
        }
    }
    
    private static boolean isMusicOnline(String pkgName, String appointName) {
        // When listen music online, must download temporary file in the order
        // from the beginning to the end. So, download engine should not be used.
        final String MiuiMusicPackage = "com.miui.player";
        final String MiuiMusicTempSuffix = "tmp";
        if(MiuiMusicPackage.equals(pkgName) && appointName != null) {
            int lastDotPos = appointName.lastIndexOf(".");
            if(lastDotPos != -1) {
                String suffix = appointName.substring(lastDotPos+1);
                if(suffix.equals(MiuiMusicTempSuffix)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static int checkDownloadEngine(String pkgName, String appointName, boolean xlEngineFlag) {
        XLConfig.LOGD(Constants.TAG, "(checkDownloadEngine) ---> pkgName=" + pkgName + 
                ", appointName=" + appointName + ", xlEngineFlag=" + xlEngineFlag);
        int xlTaskOpenMark = 1;
        if (xlEngineFlag) {
            if (pkgName != null && appointName != null) {
                int index = appointName.lastIndexOf("/");
                String name = appointName.substring(index + 1);
                if (isMusicOnline(pkgName, name)) {
                    xlTaskOpenMark = 0;
                }
            }
        } else {
            xlTaskOpenMark = 0;
        }
        XLConfig.LOGD(Constants.TAG, "(checkDownloadEngine) ---> after check, xlTaskOpenMark=" + xlTaskOpenMark);
        return xlTaskOpenMark;
    }
}
