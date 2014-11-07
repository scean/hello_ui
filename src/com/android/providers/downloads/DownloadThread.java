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

import static android.provider.Downloads.Impl.STATUS_BAD_REQUEST;
import static android.provider.Downloads.Impl.STATUS_CANNOT_RESUME;
import static android.provider.Downloads.Impl.STATUS_FILE_ERROR;
import static android.provider.Downloads.Impl.STATUS_HTTP_DATA_ERROR;
import static android.provider.Downloads.Impl.STATUS_SUCCESS;
import static android.provider.Downloads.Impl.STATUS_TOO_MANY_REDIRECTS;
import static android.provider.Downloads.Impl.STATUS_WAITING_FOR_NETWORK;
import static android.provider.Downloads.Impl.STATUS_WAITING_TO_RETRY;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.app.DownloadManager.ExtraDownloads;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.drm.DrmManagerClient;
import android.drm.DrmOutputStream;
import android.net.ConnectivityManager;
import android.net.INetworkPolicyListener;
import android.net.NetworkInfo;
import android.net.NetworkPolicyManager;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.FileUtils;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.WorkSource;
import android.provider.Downloads;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.net.http.AndroidHttpClient;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.Header;
import org.apache.http.HttpResponse;

import miui.os.Build;

import libcore.io.IoUtils;

import com.android.providers.downloads.NotificationHelper;
import com.android.providers.downloads.DownloadInfo.NetworkState;
import com.xunlei.downloadplatforms.XLDownloadManager;
import com.xunlei.downloadplatforms.XLDownloadConstant.XlErrorCode;
import com.xunlei.downloadplatforms.XLDownloadConstant.XlTaskStatus;
import com.xunlei.downloadplatforms.XLDownloadConstant.XlCreateTaskMode;
import com.xunlei.downloadplatforms.XLDownloadConstant.XlDownloadHeaderState;
import com.xunlei.downloadplatforms.entity.*;
import com.xunlei.downloadplatforms.util.XLUtil;

/**
 * Task which executes a given {@link DownloadInfo}: making network requests,
 * persisting data to disk, and updating {@link DownloadProvider}.
 */
public class DownloadThread implements Runnable {

    // TODO: bind each download to a specific network interface to avoid state
    // checking races once we have ConnectivityManager API

    private static final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    private static final int HTTP_TEMP_REDIRECT = 307;

    private static final int DEFAULT_TIMEOUT = (int) (20 * SECOND_IN_MILLIS);

    private final Context mContext;
    private final DownloadInfo mInfo;
    private final SystemFacade mSystemFacade;
    private final StorageManager mStorageManager;
    private final DownloadNotifier mNotifier;

    private volatile boolean mPolicyDirty;
//    private HttpURLConnection conn = null;

    /* xulei download*/
    private boolean mXunleiEngineEnable;
    private final XLDownloadManager mXlDownloadManager;
    private SharedPreferences mPreference;
    private long mTaskId = -1;
    private boolean mIfMobileFileSizeChecked = false;
    private boolean mHasUpdateFilesizeToDB = false;
    private long mXlVipRecvBytes = 0;

    private boolean mXlVipRequestMark = false;
    private boolean mXlVipAccelerateMark = false;
    private String mXlVipCdnUrl ="";
    private int  mXlVipStatus = -1;
    private boolean hasReportVipSpeed = false;
    private static final String TASK_MISSING = "task_missing";
    private static final String TASK_CONCELED = "task_canceled";

    private XLCdnPara cdnpara;

    private final long MAX_GET_DOWNLOAD_HEADER_DELAY = 60;  //60 second

    public DownloadThread(Context context, SystemFacade systemFacade, DownloadInfo info,
            StorageManager storageManager, DownloadNotifier notifier, XLDownloadManager dm, SharedPreferences pf) {
        mContext = context;
        mSystemFacade = systemFacade;
        mInfo = info;
        mStorageManager = storageManager;
        mNotifier = notifier;
        mXlDownloadManager = dm;
        mPreference = pf;
        XLConfig.LOGD("(DownloadThread) ---> mInfo.mXlTaskOpenMark=" + mInfo.mXlTaskOpenMark);
    }

    /**
     * Returns the user agent provided by the initiating app, or use the default one
     */
    private String userAgent() {
        String userAgent = mInfo.mUserAgent;
        if (userAgent == null) {
            userAgent = Constants.DEFAULT_USER_AGENT;
        }
        return userAgent;
    }

    private String getCookie() {
        String cookie = "";
        for (Pair<String, String> header : mInfo.getHeaders()) {
            if(!TextUtils.isEmpty(header.first)
                    && header.first.equalsIgnoreCase("Cookie")) {
                cookie = header.second;
            }
        }
        return cookie;
    }

    private String getRefUrl() {
        String refurl = "";
        for (Pair<String, String> header : mInfo.getHeaders()) {
            if(!TextUtils.isEmpty(header.first)
                    && header.first.equalsIgnoreCase("Referer")) {
                refurl = header.second;
            }
        }
        return refurl;
    }

    /**
     * State for the entire run() method.
     */
    static class State {
        public String mFilename;
        public String mDownloadingFileName;
        public String mMimeType;
        public int mRetryAfter = 0;
        public boolean mGotData = false;
        public String mRequestUri;
        public long mTotalBytes = -1;
        public long mCurrentBytes = 0;
        public String mHeaderETag;
        public String mHeaderIfRangeId;
        public String mHeaderAcceptRanges;
        public boolean mContinuingDownload = false;
        public long mBytesNotified = 0;
        public long mTimeLastNotification = 0;
        public int mNetworkType = ConnectivityManager.TYPE_NONE;

        /** Historical bytes/second speed of this download. */
        public long mSpeed;
        /** Time when current sample started. */
        public long mSpeedSampleStart;
        /** Bytes transferred since current sample started. */
        public long mSpeedSampleBytes;

        public long mContentLength = -1;
        public String mContentDisposition;
        public String mContentLocation;

        public int mRedirectionCount;
        public URL mUrl;

        public long mFileCreateTime;
        public long mDownloadingCurrentSpeed;
        public long mDownloadSurplustime;
        public long mXlAccelerateSpeed;
        public long mDownloadedTime;
        public int mXlVipStatus;
        public String mXlVipCdnUrl;
        public int mXlTaskOpenMark;
        public long mId;
        public String mPackage;

        public State(DownloadInfo info) {
            mMimeType = Intent.normalizeMimeType(info.mMimeType);
            mRequestUri = info.mUri;
            mFilename = info.mFileName;
            if (!TextUtils.isEmpty(mFilename)) {
                mDownloadingFileName = mFilename + Helpers.sDownloadingExtension;
            }
            mTotalBytes = info.mTotalBytes;
            mCurrentBytes = info.mCurrentBytes;
            mFileCreateTime = info.mFileCreateTime;
            mDownloadingCurrentSpeed = info.mDownloadingCurrentSpeed;
            mDownloadSurplustime = info.mDownloadSurplustime;
            mXlAccelerateSpeed = info.mXlAccelerateSpeed;
            mDownloadedTime = info.mDownloadedTime;
            mXlVipStatus = info.mXlVipStatus;
            mXlVipCdnUrl = info.mXlVipCdnUrl;
            mXlTaskOpenMark = info.mXlTaskOpenMark;
            mId = info.mId;
            mPackage = info.mPackage;
            if(mPackage != null && mPackage.contains("com.google.android.gm")) {
                mXlTaskOpenMark = 0;
            }
        }

        public void resetBeforeExecute() {
            // Reset any state from previous execution
            mContentLength = -1;
            mContentDisposition = null;
            mContentLocation = null;
            mRedirectionCount = 0;
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
            runInternal();
        } finally {
            mNotifier.notifyDownloadSpeed(mInfo.mId, 0);
        }
    }

    private void runInternal() {
        // Skip when download already marked as finished; this download was
        // probably started again while racing with UpdateThread.
        if (DownloadInfo.queryDownloadStatus(mContext.getContentResolver(), mInfo.mId)
                == Downloads.Impl.STATUS_SUCCESS) {
            XLConfig.LOGD("in runInternal, Download " + mInfo.mId + " already finished; skipping");
            return;
        }

        State state = new State(mInfo);
        PowerManager.WakeLock wakeLock = null;
        WifiManager.WifiLock wifiLock = null;
        int finalStatus = Downloads.Impl.STATUS_UNKNOWN_ERROR;
        int numFailed = mInfo.mNumFailed;
        String errorMsg = null;

        final NetworkPolicyManager netPolicy = NetworkPolicyManager.from(mContext);
        final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        final WifiManager  wm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        try {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.TAG);
            wakeLock.setWorkSource(new WorkSource(mInfo.mUid));
            wakeLock.acquire();

            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Constants.TAG);
            wifiLock.acquire();

            // while performing download, register for rules updates
            netPolicy.registerListener(mPolicyListener);

            XLConfig.LOGD("in runInternal, Download " + mInfo.mId + " starting");

            // Remember which network this download started on; used to
            // determine if errors were due to network changes.
            final NetworkInfo info = mSystemFacade.getActiveNetworkInfo(mInfo.mUid);
            if (info != null) {
                state.mNetworkType = info.getType();
            }

            // Network traffic on this thread should be counted against the
            // requesting UID, and is tagged with well-known value.
            TrafficStats.setThreadStatsTag(TrafficStats.TAG_SYSTEM_DOWNLOAD);
            TrafficStats.setThreadStatsUid(mInfo.mUid);

            try {
                // TODO: migrate URL sanity checking into client side of API
                state.mUrl = new URL(state.mRequestUri);
            } catch (MalformedURLException e) {
                XLConfig.LOGD("in runInternal, throw status_bad_request exception.", e);
                throw new StopRequestException(STATUS_BAD_REQUEST, e);
            }

            if (state.mXlTaskOpenMark == 0) {
                executeDownload(state);
            } else {
                executeDownload_xl(state);
            }

            finalizeDestinationFile(state);
            finalStatus = Downloads.Impl.STATUS_SUCCESS;

            // do start track
            int network = XLUtil.getNetwrokType(mContext);
            if (state.mFileCreateTime != 0) {
                state.mDownloadedTime = (System.currentTimeMillis() - state.mFileCreateTime) / 1000;
            }
            Helpers.trackDownloadStop(mContext, 105, (int) mTaskId,
                !(1 == state.mXlTaskOpenMark), "", "", state.mPackage,
                            XLConfig.PRODUCT_NAME,
                            XLConfig.PRODUCT_VERSION, network,
                    state.mDownloadedTime, state.mTotalBytes, 0,
                    mXlVipRecvBytes, 0, 0, 30);

        } catch (StopRequestException error) {
            // remove the cause before printing, in case it contains PII
            errorMsg = error.getMessage();
            String msg = "Aborting request for download " + mInfo.mId + ": " + errorMsg;
            finalStatus = error.getFinalStatus();
            XLConfig.LOGD("in runInternal catch StopRequestException: finalStatus=" + finalStatus + ", msg=" + msg, error);

            if (1 == state.mXlTaskOpenMark) {
                if (mXlDownloadManager != null && mTaskId != -1) {
                    XLConfig.LOGD("xunlei(DownloadThread.run) ---> pause/cancel/network_changing happened, stop task, id=" + mTaskId);
                    mXlDownloadManager.XLStopTask(mTaskId);
                    mXlDownloadManager.XLReleaseTask(mTaskId);
                }
            }

            // Nobody below our level should request retries, since we handle
            // failure counts at this level.
            if (finalStatus == STATUS_WAITING_TO_RETRY) {
                XLConfig.LOGD("in runInternal, throw IllegalStateException");
                throw new IllegalStateException("Execution should always throw final error codes");
            }

            reportDownloadZeroSpeed();

            // Some errors should be retryable, unless we fail too many times.
            if (isStatusRetryable(finalStatus)) {
                // if (state.mGotData) {
                //     numFailed = 1;
                // } else {
                //     numFailed += 1;
                // }
                numFailed += 1;

                if (numFailed < Constants.MAX_RETRIES) {
                    final NetworkInfo info = mSystemFacade.getActiveNetworkInfo(mInfo.mUid);
                    if (info != null && info.getType() == state.mNetworkType
                            && info.isConnected()) {
                        // Underlying network is still intact, use normal backoff
                        finalStatus = STATUS_WAITING_TO_RETRY;
                    } else {
                        // Network changed, retry on any next available
                        finalStatus = STATUS_WAITING_FOR_NETWORK;
                    }
                }
            }

            int status = -1;
            int reason = -1;
            switch (finalStatus) {
            case Downloads.Impl.STATUS_PAUSED_BY_APP:
                status = 102;
                reason = 0;
                break;
            case Downloads.Impl.STATUS_CANCELED:
                if (error.getMessage().equals(TASK_CONCELED) || error.getMessage().equals(TASK_MISSING)) {
                    status = 104;
                    reason = 20;
                } else {
                    status = 106;
                    reason = 40;
                }
                break;
            case Downloads.Impl.STATUS_QUEUED_FOR_WIFI:
            case Downloads.Impl.STATUS_WAITING_FOR_NETWORK:
                status = 102;
                reason = 1;
                break;
            default:
                status = 106;
                reason = 40;
                break;
            }

            // do stop track
            int network = XLUtil.getNetwrokType(mContext);
            if (state.mFileCreateTime != 0) {
                state.mDownloadedTime = (System.currentTimeMillis() - state.mFileCreateTime) / 1000;
            }
            Helpers.trackDownloadStop(mContext, status, (int) mTaskId,
                !(1 == state.mXlTaskOpenMark), "", "", state.mPackage,
                            XLConfig.PRODUCT_NAME,
                            XLConfig.PRODUCT_VERSION, network,
                    state.mDownloadedTime, state.mCurrentBytes, 0,
                    mXlVipRecvBytes, 0, 0, reason);

            // fall through to finally block
        } catch (Throwable ex) {
            errorMsg = ex.getMessage();
            String msg = "Exception for id " + mInfo.mId + ": " + errorMsg;
            XLConfig.LOGD(msg, ex);
            finalStatus = Downloads.Impl.STATUS_UNKNOWN_ERROR;
            // falls through to the code that reports an error
            if (1 == state.mXlTaskOpenMark) {
                if (mXlDownloadManager != null && mTaskId != -1) {
                    XLConfig.LOGD("xunlei(DownloadThread.run) ---> Exception happened, stop task, id=" + mTaskId);
                    mXlDownloadManager.XLStopTask(mTaskId);
                    mXlDownloadManager.XLReleaseTask(mTaskId);
                }
            }

            // do stop track
            int network = XLUtil.getNetwrokType(mContext);
            if (state.mFileCreateTime != 0) {
                state.mDownloadedTime = (System.currentTimeMillis() - state.mFileCreateTime) / 1000;
            }
            Helpers.trackDownloadStop(mContext, 106, (int) mTaskId,
                    !(1 == state.mXlTaskOpenMark), "", "", state.mPackage,
                            XLConfig.PRODUCT_NAME,
                            XLConfig.PRODUCT_VERSION, network,
                    state.mDownloadedTime, state.mCurrentBytes, 0,
                    mXlVipRecvBytes, 0, 0, 40);

        } finally {
            if (finalStatus == STATUS_SUCCESS) {
                TrafficStats.incrementOperationCount(1);
            }

            TrafficStats.clearThreadStatsTag();
            TrafficStats.clearThreadStatsUid();

            cleanupDestination(state, finalStatus);

            notifyDownloadCompleted(state, finalStatus, errorMsg, numFailed);

            XLConfig.LOGD("Download " + mInfo.mId + " finished with status " + Downloads.Impl.statusToString(finalStatus));

            netPolicy.unregisterListener(mPolicyListener);

            if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }

            if (wifiLock != null) {
                wifiLock.release();
                wifiLock = null;
            }

            if (cdnpara != null) {
                deleteXlCdnQueryTask(cdnpara);
            }
        }

        mStorageManager.incrementNumDownloadsSoFar();
        synchronized (Helpers.sDownloadsDomainCountMap) {
            if (Helpers.sDownloadsDomainCountMap.containsKey(mInfo.mUriDomain)) {
                int count = Helpers.sDownloadsDomainCountMap.get(mInfo.mUriDomain);
                if (count > 1) {
                    Helpers.sDownloadsDomainCountMap.put(mInfo.mUriDomain, --count);
                    Intent i = new Intent();
                    i.setClassName("com.android.providers.downloads", "com.android.providers.downloads.DownloadService");
                    mContext.startService(i);
                } else {
                    Helpers.sDownloadsDomainCountMap.remove(mInfo.mUriDomain);
                }
            }
        }
    }

    private void executeDownload_xl(State state) throws StopRequestException {
        XLConfig.LOGD(Constants.TAG, "executeDownload_xl ---> executeDownload_xl");
        state.resetBeforeExecute();
        setupDestinationFile(state);

        if (state.mCurrentBytes == state.mTotalBytes) {
            XLConfig.LOGD("Skipping initiating request for download " +
                  mInfo.mId + "; already completed");
            return;
        }

        NetworkInfo info = mSystemFacade.getActiveNetworkInfo(mInfo.mUid);
        // only do this proc in mobile network and new task
        if (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE && !state.mContinuingDownload) {
            Long recommendedMaxBytesOverMobile = mSystemFacade.getRecommendedMaxBytesOverMobile();
            if (!((recommendedMaxBytesOverMobile != null && recommendedMaxBytesOverMobile == DownloadInfo.MAX_BYTES_OVER_MOBILE))) {
                Log.e(Constants.TAG, "jinghuang ---> checkFileSizeinMobile fun called!");
                mIfMobileFileSizeChecked = true;
                checkFileSizeinMobile(state);
            }
        }

        transferData_xl(state);
    }

    /**
     * Fully execute a single download request. Setup and send the request,
     * handle the response, and transfer the data to the destination file.
     */
    private void executeDownload(State state) throws StopRequestException {
        XLConfig.LOGD("jinghuang4 ---> executeDownload");
        state.resetBeforeExecute();
        setupDestinationFile(state);

        // do start track
        /*
          int network = XLUtil.getNetwrokType(mContext);
          int status = 101;
          if (!mIsNewTask) {
          status = 107;
          }

          Helpers.trackDownloadStart(mContext, status, (int) mTaskId,
          1 == state.mXlTaskOpenMark,!getVipSwitchStatus(), "", "", state.mPackage,
          XLConfig.PRODUCT_NAME, XLConfig.PRODUCT_VERSION,
          network, 0, 0, state.mTotalBytes, state.mRequestUri,
          state.mFilename, 0);
        */
        // skip when already finished; remove after fixing race in 5217390
        if (state.mCurrentBytes == state.mTotalBytes) {
            XLConfig.LOGD("Skipping initiating request for download " +
                  mInfo.mId + "; already completed");
            return;
        }

        NetworkInfo info = mSystemFacade.getActiveNetworkInfo(mInfo.mUid);
        // only do this proc in mobile network and new task
        if (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE && !state.mContinuingDownload) {
            Long recommendedMaxBytesOverMobile = mSystemFacade.getRecommendedMaxBytesOverMobile();
            if (!((recommendedMaxBytesOverMobile != null && recommendedMaxBytesOverMobile == DownloadInfo.MAX_BYTES_OVER_MOBILE))) {
                checkFileSizeinMobile(state);
            }
        }

        while (state.mRedirectionCount++ < Constants.MAX_REDIRECTS) {
            // Open connection and follow any redirects until we have a useful
            // response with body.
            HttpURLConnection conn = null;
            try {
                checkConnectivity(false);
                conn = (HttpURLConnection) state.mUrl.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(DEFAULT_TIMEOUT);
                conn.setReadTimeout(DEFAULT_TIMEOUT);

                addRequestHeaders(state, conn);

                final int responseCode = conn.getResponseCode();
                XLConfig.LOGD("responseCode = " + responseCode + ", continuingDownload=" + state.mContinuingDownload);

                switch (responseCode) {
                    case HTTP_OK:
                        if (state.mContinuingDownload) {
                            throw new StopRequestException(
                                    STATUS_CANNOT_RESUME, "Expected partial, but received OK");
                        }

                        processResponseHeaders(state, conn);

                        transferData(state, conn);
                        return;
                    case HTTP_PARTIAL:
                        if (!state.mContinuingDownload) {
                            throw new StopRequestException(
                                    STATUS_CANNOT_RESUME, "Expected OK, but received partial");
                        }
                        transferData(state, conn);
                        return;
                    case HTTP_MOVED_PERM:
                    case HTTP_MOVED_TEMP:
                    case HTTP_SEE_OTHER:
                    case HTTP_TEMP_REDIRECT:
                        final String location = conn.getHeaderField("Location");
                        state.mUrl = new URL(state.mUrl, location);
                        if (responseCode == HTTP_MOVED_PERM) {
                            // Push updated URL back to database
                            state.mRequestUri = state.mUrl.toString();
                        }
                        continue;

                    case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                        throw new StopRequestException(
                                STATUS_CANNOT_RESUME, "Requested range not satisfiable");

                    case HTTP_UNAVAILABLE:
                        parseRetryAfterHeaders(state, conn);
                        throw new StopRequestException(
                                HTTP_UNAVAILABLE, conn.getResponseMessage());

                    case HTTP_INTERNAL_ERROR:
                        throw new StopRequestException(
                                HTTP_INTERNAL_ERROR, conn.getResponseMessage());

                    default:
                        StopRequestException.throwUnhandledHttpError(
                                responseCode, conn.getResponseMessage());
                }
           } catch (IOException e) {
               XLConfig.LOGD("error when executeDownload: ", e);
               throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);
           } finally {
               if (conn != null) {
                   try {
                       InputStream xn = conn.getInputStream();
                       if(xn !=null) xn.close();
                   } catch (IOException e2) {
                   } catch (Exception e3) {
                   }
                   conn.disconnect();
               }
           }
        }

        throw new StopRequestException(STATUS_TOO_MANY_REDIRECTS, "Too many redirects");
    }

    /**
     * Transfer data from the given connection to the destination file.
     */
    private void transferData(State state, HttpURLConnection conn) throws StopRequestException {
        DrmManagerClient drmClient = null;
        InputStream in = null;
        OutputStream out = null;
        FileDescriptor outFd = null;

        try {
            try {
                in = conn.getInputStream();
            } catch (IOException e) {
                XLConfig.LOGD("error when transferData: ", e);
                throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);
            }

            try {
                if (DownloadDrmHelper.isDrmConvertNeeded(state.mMimeType)) {
                    drmClient = new DrmManagerClient(mContext);
                    final RandomAccessFile file = new RandomAccessFile(
                            new File(state.mDownloadingFileName), "rw");
                    out = new DrmOutputStream(drmClient, file, state.mMimeType);
                    outFd = file.getFD();
                } else {
                    out = new FileOutputStream(state.mDownloadingFileName, true);
                    outFd = ((FileOutputStream) out).getFD();
                }
            } catch (IOException e) {
                throw new StopRequestException(STATUS_FILE_ERROR, e);
            }

            // Start streaming data, periodically watch for pause/cancel
            // commands and checking disk space as needed.
            transferData(state, in, out);
            try {
                if (out instanceof DrmOutputStream) {
                    ((DrmOutputStream) out).finish();
                }
            } catch (IOException e) {
                throw new StopRequestException(STATUS_FILE_ERROR, e);
            }
        } finally {
            XLConfig.LOGD(Constants.TAG, "transferData ---> finally in transferData!");
            if (0 == state.mXlTaskOpenMark) {
                if (drmClient != null) {
                    drmClient.release();
                }

                IoUtils.closeQuietly(in);

                try {
                    if (out != null) out.flush();
                    if (outFd != null) outFd.sync();
                } catch (IOException e) {
                } finally {
                    IoUtils.closeQuietly(out);
                }
            }
        }
    }

    /**
     * Check if current connectivity is valid for this request.
     */
    private void checkConnectivity(boolean isTaskRunning) throws StopRequestException {
        // checking connectivity will apply current policy
        //Log.i("DownloadManager-jinghuang", "---> checkConnectivity called, isTaskRunning=" + isTaskRunning);
        mPolicyDirty = false;

        final NetworkState networkUsable = mInfo.checkCanUseNetwork(isTaskRunning);
        if (networkUsable != NetworkState.OK) {
            int status = Downloads.Impl.STATUS_WAITING_FOR_NETWORK;
            if (networkUsable == NetworkState.UNUSABLE_DUE_TO_SIZE) {
                status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
                mInfo.notifyPauseDueToSize(true);
            } else if (networkUsable == NetworkState.RECOMMENDED_UNUSABLE_DUE_TO_SIZE) {
                status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
            } else if (networkUsable == NetworkState.TYPE_DISALLOWED_BY_REQUESTOR) {
                status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
            }
            throw new StopRequestException(status, networkUsable.name());
        }
    }

    private int getXlDownloadQueryMode() {
        int queryMode = 0;

        if (Build.IS_TABLET) {
            return queryMode;
        }

        String token = "";
        if (mPreference != null) {
            token = mPreference.getString("xunlei_token", "");
            XLConfig.LOGD("xunlei(checkXLDownloadQueryMode) ---> get token from xml:" + token);
        }

        if (token != null && token != "") {
            queryMode = 1;
            try {
                Context otherAppsContext = mContext.createPackageContext("com.android.providers.downloads.ui",
                        Context.CONTEXT_IGNORE_SECURITY);
                //SharedPreferences sharedPreferences = otherAppsContext
                  //      .getSharedPreferences(XLConfig.PREF_NAME_IN_UI, Context.MODE_WORLD_READABLE);
                SharedPreferences sharedPreferences = otherAppsContext
                              .getSharedPreferences(XLConfig.PREF_NAME_IN_UI, Context.MODE_MULTI_PROCESS);
                long vipflag = sharedPreferences.getLong(XLConfig.PREF_KEY_XUNLEI_VIP, XLConfig.XUNLEI_VIP_ENABLED);
                if (vipflag != XLConfig.XUNLEI_VIP_ENABLED) {
                    XLConfig.LOGD("xunlei(checkXLDownloadQueryMode) ---> vip is forbidden, set mQueryMode 0.");
                    queryMode = 0;
                }
            } catch (Exception e) {
                XLConfig.LOGD("xunlei(checkXLDownloadQueryMode) ---> no vip flag in ui db.", e);
            }
        }

        if (queryMode == 1 && mXlDownloadManager != null) {
            XLConfig.LOGD("xunlei(checkXLDownloadQueryMode) ---> vip is enabled, set token to viphub");
            mXlDownloadManager.XLSetUserAccessToken(token);
        }
        return queryMode;
    }

    private void addXlCloudCheckTask(XLCloudControlPara para) {
        DownloadService.XLAddCloudCheckTask(para);
    }

    private int queryXlCloudCheck(String url) {
        // if return 1, can use vip hub
        return DownloadService.XLQueryCloudCheck(url);
    }

    private void addXlCdnQueryTask(XLCdnPara para) {
        DownloadService.XLAddCdnQueryTask(para);
    }

    private XLCdnPara queryXlCdn(String url) {
        return DownloadService.XLQueryCdn(url);
    }

    private XLCdnPara queryXlCdn(XLCdnPara para) {
        return DownloadService.XLQueryCdn(para);
    }

    private void deleteXlCdnQueryTask(XLCdnPara para) {
        DownloadService.XLDeleteCdnQueryTask(para);
    }

    /**
     * transfer data with Xunlei engine.
     */
    private void transferData_xl(State state) throws StopRequestException {
        XLConfig.LOGD("(transferData_xl) ---> enter transferData_xl function!");

        int queryMode = getXlDownloadQueryMode();
        boolean hasAddedXlCdnQueryTask = false;
        boolean hasGetCdn = false;
        boolean isMobileNetTask = false;
        boolean isResourceTypeCheckOver = false;
        String fileName = null;
        String path = null;

        int taskmode;
        if (!state.mContinuingDownload) {
            XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> new task!");
            taskmode = XlCreateTaskMode.NEW_TASK.ordinal();
        } else {
            XLConfig.LOGD("(transferData_xl) ---> continue task!");
            taskmode = XlCreateTaskMode.CONTINUE_TASK.ordinal();
        }

        DownloadParam para = null;
        if (state.mRequestUri != null) {
            XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> mIfMobileFileSizeChecked = "
                    + mIfMobileFileSizeChecked + ", state.mContinuingDownload= "
                    + state.mContinuingDownload);

            if (mIfMobileFileSizeChecked || state.mContinuingDownload) {
                int index = state.mDownloadingFileName.lastIndexOf("/");
                fileName = state.mDownloadingFileName.substring(index + 1);
                path = state.mDownloadingFileName.substring(0, index);
                para = new DownloadParam(fileName, path,
                        state.mRequestUri.toString(), getCookie(), getRefUrl(), "", "",
                        taskmode, (int)state.mId);
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> filename = " + fileName);
            } else {
                para = new DownloadParam("", "",
                        state.mRequestUri.toString(), getCookie(), getRefUrl(), "", "",
                        taskmode, (int)state.mId);
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> filename = null");
            }
        } else {
            // throw exception
            throw new StopRequestException(
                    Downloads.Impl.STATUS_CANCELED, "xunlei - Download url is null.");
        }

        // cloud check
        if (XLUtil.getNetwrokType(mContext) == ConnectivityManager.TYPE_MOBILE) {
            XLCloudControlPara xlccpara = new XLCloudControlPara(state.mRequestUri);
            addXlCloudCheckTask(xlccpara);
            isMobileNetTask = true;
            queryMode = 0;
            XLConfig.LOGD("(transferData_xl) ---> Not wifi task, set queryMode = 0");
        }

        int ret;
        GetTaskId cTaskId = new GetTaskId();
        if (mXlDownloadManager != null) {
            ret = mXlDownloadManager.XLCreateP2SPTask(para, cTaskId);
            if (ret != XlErrorCode.NO_ERROR) {
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> XLCreateP2SPTask error.");
                throw new StopRequestException(
                        Downloads.Impl.STATUS_CANCELED, "xunlei - XLCreateP2SPTask error.");
            }

            if (!Build.IS_TABLET) {
                if (NotificationHelper.getNotificationSwitch(mContext.getApplicationContext())){
                    NotificationHelper.initNotificationService(mContext.getApplicationContext());
                }
            }

            mTaskId = cTaskId.getTaskId();
        } else {
            // throw exception
            throw new StopRequestException(
                    Downloads.Impl.STATUS_CANCELED, "xunlei - mXlDownloadManager is null.");
        }
        if (mTaskId == -1) {
            // throw exception
            throw new StopRequestException(
                    Downloads.Impl.STATUS_CANCELED, "xunlei - XLCreateP2SPTask return mTaskId -1.");
        }else{
            // do start track
            int network = XLUtil.getNetwrokType(mContext);
            int status = 101;
            if (state.mContinuingDownload) {
                status = 107;
            }

            Helpers.trackDownloadStart(mContext, status, (int) mTaskId,
                !(1 == state.mXlTaskOpenMark),!getVipSwitchStatus(), "", "", state.mPackage,
                XLConfig.PRODUCT_NAME, XLConfig.PRODUCT_VERSION,
                network, 0, 0, state.mTotalBytes, state.mRequestUri,
                fileName, 0);
        }

        // cloud check first place
        int queryRet;
        if (isMobileNetTask) {
            mXlDownloadManager.XLSetTaskAllowUseResource(mTaskId, 1);    //original resource
            queryRet = queryXlCloudCheck(state.mRequestUri);
            if (queryRet != -1) { // check already over
                if (queryRet == 1) {
                    mXlDownloadManager.XLSetTaskAllowUseResource(mTaskId, -1); // multi resource
                    queryMode = 1;
                    XLConfig.LOGD("(transferData_xl) ---> " + 
                            "Cloud Control check over before task start, using multi resource!!! set queryMode = 1");
                }
                isResourceTypeCheckOver = true;
            }
        }

        mXlDownloadManager.XLSetTaskAppInfo(mTaskId, XLConfig.APP_KEY, XLConfig.PRODUCT_NAME, XLConfig.PRODUCT_VERSION);
        mXlDownloadManager.XLSetOriginUserAgent(mTaskId, userAgent(), userAgent().length());
        mXlDownloadManager.XLStartTask(mTaskId);
        sendXLTaskStartBroadcast();

        if (!state.mContinuingDownload && !mIfMobileFileSizeChecked) {
            Log.e(Constants.TAG, "jinghuang ---> getDownloadHeader called");
            getDownloadHeader(mTaskId, state);
            fileName = state.mFilename.substring(state.mFilename.lastIndexOf("/") + 1);
            XLConfig.LOGD(Constants.TAG, "(transferData_xl) --->  getDownloadHeaderName=" + state.mDownloadingFileName);
            ret = mXlDownloadManager.XLSetFileName(mTaskId, state.mDownloadingFileName, state.mDownloadingFileName.length());
            if (XlErrorCode.NO_ERROR != ret) {
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> XLSetFileName return error, ret = " + ret);
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED, TASK_CONCELED);
            }
        }

        //XLTaskInfoEX taskInfo = new XLTaskInfoEX();
        XLTaskInfo taskInfo = new XLTaskInfo();
        for (;;) {
            if (!isResourceTypeCheckOver && isMobileNetTask) {
                queryRet = queryXlCloudCheck(state.mRequestUri);
                if (queryRet != -1) { // check already over
                    if (1 == queryRet) {
                        mXlDownloadManager.XLSwitchOriginToAllResDownload(mTaskId); // multi resource
                        queryMode = 1;
                        XLConfig.LOGD("(transferData_xl) ---> " + 
                                "Cloud Control check over after task start, using multi resource!!! set queryMode = 1");
                    }
                    isResourceTypeCheckOver = true;
                }
            }

            ret = mXlDownloadManager.XLGetTaskInfo(mTaskId, queryMode, taskInfo);

            if (XlErrorCode.NO_ERROR != ret) {
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> XLGetTaskInfo return error, ret = " + ret);
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED, TASK_CONCELED);
            }

            state.mDownloadingCurrentSpeed  = taskInfo.mDownloadSpeed;
            state.mCurrentBytes = taskInfo.mDownloadSize;
            state.mTotalBytes = taskInfo.mFileSize;

            XLConfig.LOGD("(transferData_xl) ---> taskid:" + mTaskId + ", filesize:" + state.mTotalBytes
                    + ", curSpeed:" + state.mDownloadingCurrentSpeed + ", taskstatus:" + taskInfo.mTaskStatus
                    + ", errorcode:" + taskInfo.mErrorCode 
                    + ", QueryMode:" + queryMode 
                    + ", Query_index_status:" + taskInfo.mQueryIndexStatus);

            if (queryMode == 1) {
                state.mXlAccelerateSpeed = taskInfo.mAdditionalResVipSpeed;
                mXlVipRecvBytes = taskInfo.mAdditionalResVipRecvBytes;     // this value should be written into db
                XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> VipSpeed: "
                        + "mAdditionalRes.speed=" + taskInfo.mAdditionalResVipSpeed
                        + ", mAdditionalRes.recebytes=" + taskInfo.mAdditionalResVipRecvBytes);
                if (!hasReportVipSpeed && state.mXlAccelerateSpeed > 0) {
                    hasReportVipSpeed = true;
                    int network = XLUtil.getNetwrokType(mContext);
                    Helpers.trackDownloadStart(mContext, 202, (int) mTaskId,
                            !(1 == state.mXlTaskOpenMark), !getVipSwitchStatus(), "", "",
                            state.mPackage, XLConfig.PRODUCT_NAME,
                            XLConfig.PRODUCT_VERSION, network, 0, 0,
                            state.mTotalBytes, state.mRequestUri, fileName, 0);
                }
            } else {
                state.mXlAccelerateSpeed = 0;
            }

            // add cdn query task
            if (!hasAddedXlCdnQueryTask && queryMode == 1 && taskInfo.mQueryIndexStatus == 2) {
                XLConfig.LOGD("(transferData_xl) ---> taskid=" + mTaskId
                        + ", mCid=" + taskInfo.mCid
                        + ", mGcid=" + taskInfo.mGcid);
                cdnpara = new XLCdnPara(state.mRequestUri.toString(), fileName, taskInfo.mCid,
                        taskInfo.mGcid, taskInfo.mFileSize, getRefUrl(), getCookie());
                addXlCdnQueryTask(cdnpara);
                hasAddedXlCdnQueryTask = true;
            }

            if (queryMode == 1 && taskInfo.mQueryIndexStatus == 3) {
                XLConfig.LOGD("(transferData_xl) ---> taskid="
                        + mTaskId + ", name=" + fileName
                        + ", can't query index from xunlei so");
                synchronized (TokenHelper.getInstance().mutex) {
                    ret = mXlDownloadManager.XLCommitCollectTask(state.mRequestUri.toString(), fileName, taskInfo.mFileSize, getRefUrl(), getCookie());
                }
                queryMode = 0;
            }

            // query cdn
            if (hasAddedXlCdnQueryTask && queryMode == 1 && !hasGetCdn ) {
                XLCdnPara cdnPara = queryXlCdn(cdnpara);
                if (cdnPara != null) {
                    hasGetCdn = true;
                    if (cdnPara.getCdn() != null && cdnPara.getCdnCookie() != null) {
                        int network = XLUtil.getNetwrokType(mContext);
                        Helpers.trackDownloadStart(mContext, 200, (int) mTaskId,
                                !(1 == state.mXlTaskOpenMark), !getVipSwitchStatus(), "", "",
                                state.mPackage, XLConfig.PRODUCT_NAME,
                                XLConfig.PRODUCT_VERSION, network, 0, 0,
                                state.mTotalBytes, state.mRequestUri, fileName, 0);
                         ServerResourceParam respara = new ServerResourceParam(cdnPara.getCdn(), "", cdnPara.getCdnCookie(), 20, 0);
                         ret = mXlDownloadManager.XLAddTaskServerResource(mTaskId, respara);
                         if (ret == 9000) {
                             // do start track
                             int network1 = XLUtil.getNetwrokType(mContext);
                             Helpers.trackDownloadStart(mContext, 201, (int) mTaskId,
                                     !(1 == state.mXlTaskOpenMark), !getVipSwitchStatus(), "", "",
                                     state.mPackage, XLConfig.PRODUCT_NAME,
                                     XLConfig.PRODUCT_VERSION, network1, 0, 0,
                                     state.mTotalBytes, state.mRequestUri, fileName, 0);
                         }
                        // if error happened
                    } else {
                        queryMode = 0;
                    }
                }
            }

            checkPausedOrCanceled(state);

            if (taskInfo.mTaskStatus == XlTaskStatus.TASK_FAILED) {
                throw new StopRequestException(
                        Downloads.Impl.STATUS_CANCELED, "xunlei - XLGetTaskInfo return taskstatus failed");
            }

            reportProgress(state);

            if (taskInfo.mTaskStatus == XlTaskStatus.TASK_SUCCESS
                    || taskInfo.mTaskStatus == XlTaskStatus.TASK_STOPPED) {

                if (mXlDownloadManager != null && mTaskId != -1) {
                    XLConfig.LOGD(Constants.TAG, "(transferData_xl) ---> if task success/stop/fail, then call stopTask, id=" + mTaskId);
                    mXlDownloadManager.XLStopTask(mTaskId);
                    mXlDownloadManager.XLReleaseTask(mTaskId);
                }
                return;
            }

            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }

    /**
     * prase field value from http response
     * @param field
     * @param target
     * @return
     */
    private String parseFieldValue(String field, final String target) {
        int begin_index = target.indexOf(field);
        int end_index = target.indexOf("\n", begin_index);
        return target.substring(begin_index + field.length() + 1, end_index);
    }

    /**
     * @param state
     * @param response
     * @return
     * @throws StopRequestException
     */
    private int processDownloadHeader(State state, final String response) throws StopRequestException {
        int responseCode = -1;
        if (response.startsWith("HTTP")) {
            int index = response.indexOf(" ");
            String code = response.substring(index + 1, index + 1 + 3);
            try {
                responseCode = Integer.parseInt(code);
            } catch (Exception e) {
                // TODO: handle exception
                XLConfig.LOGD(Constants.TAG, "(processDownloadHeader) ---> NumberFormatException happen in parse response code = " + code);
            }

            XLConfig.LOGD(Constants.TAG, "(processDownloadHeader) ---> int responseCode=" + responseCode);

            if (responseCode == HTTP_OK ||
                    responseCode == HTTP_PARTIAL ||
                    responseCode == HTTP_REQUESTED_RANGE_NOT_SATISFIABLE) {
                if (response.contains("Content-Disposition:")) {
                    state.mContentDisposition = parseFieldValue("Content-Disposition:", response);
                }

                if (response.contains("Content-Location:")) {
                    state.mContentLocation = parseFieldValue("Content-Location:", response);
                }

                if (response.contains("Content-Type:")) {
                    state.mMimeType = parseFieldValue("Content-Type:", response);
                    XLConfig.LOGD("(processDownloadHeader) ---> mMimeType=" + state.mMimeType);
                }

                if (response.contains("ETag:")) {
                    state.mHeaderETag = parseFieldValue("ETag:", response);
                }

                if (response.contains("Last-Modified:")) {
                    state.mHeaderIfRangeId = parseFieldValue("Last-Modified:", response);
                    XLConfig.LOGD("(processDownloadHeader) ---> mHeaderIfRangeId=" + state.mHeaderIfRangeId);
                }

                if (response.contains("Accept-Ranges:")) {
                    state.mHeaderAcceptRanges = parseFieldValue("Accept-Ranges:", response);
                }

                if (response.contains("Retry-After:")) {
                    String retryfield = parseFieldValue("Retry-After:", response);
                    try {
                        state.mRetryAfter = Integer.parseInt(retryfield);
                    } catch (Exception e) {
                        XLConfig.LOGD("(processDownloadHeader) ---> NumberFormatException happen in parse Retry-After field = " + retryfield);
                        state.mRetryAfter = -1;
                    }
                } else {
                    state.mRetryAfter = -1;
                }

                String transferEncoding = null;
                state.mContentLength = -1;
                if (response.contains("Transfer-Encoding:")) {
                    transferEncoding = parseFieldValue("Transfer-Encoding:", response);;
                } else {
                    String lengthfield = "";
                    if (responseCode == HTTP_PARTIAL) {
                        if (response.contains("Content-Range:")) {
                            String value = parseFieldValue("Content-Range:", response);
                            int begin_index = value.indexOf("/");
                            int end_index = value.indexOf("\n", begin_index);
                            lengthfield = value.substring(begin_index + 1, end_index);
                            XLConfig.LOGD("(processDownloadHeader) ---> 206 lengthfield=" + lengthfield);
                        }
                    } else {
                        if (response.contains("Content-Length:")) {
                            lengthfield = parseFieldValue("Content-Length:", response);
                        }
                    }

                    try {
                        state.mContentLength = Long.parseLong(lengthfield.trim());
                    } catch (NumberFormatException e) {
                        XLConfig.LOGD("(processDownloadHeader) ---> NumberFormatException happen in parse length field = " + lengthfield);
                    }
                }

                state.mTotalBytes = state.mContentLength;
                mInfo.mTotalBytes = state.mContentLength;

                XLConfig.LOGD("(processDownloadHeader) ---> filesize=" + state.mTotalBytes);

                final boolean noSizeInfo = state.mContentLength == -1
                        && (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked"));
                if (!mInfo.mNoIntegrity && noSizeInfo) {
                    throw new StopRequestException(STATUS_CANNOT_RESUME,
                            "can't know size of download, giving up");
                }
            }
        }

        return responseCode;
    }

    /**
     * used by xunlei engine
     * return filename
     * @param taskid
     * @return
     * @throws StopRequestException
     */
    private void getDownloadHeader(long taskid, State state) throws StopRequestException {
        int ret;
        GetDownloadHead header = new GetDownloadHead();
        int time = 0;
        long timeStart = XLUtil.getCurrentUnixTime();
        while(XLUtil.getCurrentUnixTime() < timeStart + MAX_GET_DOWNLOAD_HEADER_DELAY) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                // TODO: handle exception
            }

            ret = mXlDownloadManager.XLGetDownloadHeader(taskid, header);
            if (XlErrorCode.NO_ERROR != ret) {
                XLConfig.LOGD(Constants.TAG, "(getDownloadHeader) ---> XLGetDownloadHeader return error, ret = " + ret);
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED, TASK_CONCELED);
            }

            XLConfig.LOGD("(getDownloadHeader) ---> xunlei return state=" + header.mHttpState + ", times=" + ++time);
            if (header.mHttpState == XlDownloadHeaderState.GDHS_SUCCESS.ordinal()) {
                XLConfig.LOGD("(getDownloadHeader) ---> response=" + header.mHttpResponse);
                final int responseCode = processDownloadHeader(state, header.mHttpResponse);
                switch (responseCode) {
                    case HTTP_OK:
                    case HTTP_PARTIAL:
                    case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                        processResponseHeaders(state, null);
                        return;
                    case HTTP_UNAVAILABLE:
                        parseRetryAfterHeaders(state, null);
                        throw new StopRequestException(
                                HTTP_UNAVAILABLE, "xunlei - http return 503 when getting download header.");
                    case HTTP_INTERNAL_ERROR:
                        throw new StopRequestException(
                                HTTP_INTERNAL_ERROR, "xunlei - http return 500 when getting download header.");
                    default:
                        StopRequestException.throwUnhandledHttpError(
                                responseCode, "xunlei - http return " + responseCode + " when getting download header.");
                }

                break;
            } else if (header.mHttpState == XlDownloadHeaderState.GDHS_ERROR.ordinal()) {
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED, "XLGetDownloadHeader http error, header.mHttpState=" + header.mHttpState);
            }

            checkPausedOrCanceled(state);
        }

        throw new StopRequestException(STATUS_TOO_MANY_REDIRECTS, "xunlei - Too many redirects to get header.");
    }

    /**
     * Transfer as much data as possible from the HTTP response to the destination file.
     * @param data buffer to use to read data
     * @param entityStream stream for reading the HTTP response entity
     */
    private void transferData(State state, InputStream in, OutputStream out)
            throws StopRequestException {
        final byte data[] = new byte[Constants.BUFFER_SIZE];
        long countTime = 0;
        long countBytes = 0;
        for (;;) {
            long begintime = mSystemFacade.currentTimeMillis();
            int bytesRead = readFromResponse(state, data, in);
            if (bytesRead == -1) { // success, end of stream already reached
                handleEndOfStream(state);
                return;
            }

            state.mGotData = true;
            writeDataToDestination(state, data, bytesRead, out);
            state.mCurrentBytes += bytesRead;
            countBytes += bytesRead;

            long endtime = mSystemFacade.currentTimeMillis();
            long time = endtime - begintime;
            if (0 == time) {
                continue;
            }

            countTime += time;
            // state.mDownloadingCurrentSpeed = 1000 * bytesRead / time ;
            //   Log.i(Constants.TAG, "Android-download ---> bytesRead=" + bytesRead +
            //         ", time=" + time + ", speed=" + state.mDownloadingCurrentSpeed);
            if (countTime >= Constants.MIN_PROGRESS_TIME) {
                state.mDownloadingCurrentSpeed = countBytes;
                XLConfig.LOGD("(transferData) ---> Android-download ---> bytesRead=" + countBytes +
                     ", time=" + countTime + ", speed=" + state.mDownloadingCurrentSpeed +
                     " currentBytes = " + state.mCurrentBytes);
                reportProgress(state);
                countTime = 0;
                countBytes = 0;
            }

            XLConfig.LOGD("downloaded " + state.mCurrentBytes + " for " + mInfo.mUri);

            checkPausedOrCanceled(state);
        }
    }

    /**
     * Called after a successful completion to take any necessary action on the downloaded file.
     */
    private void finalizeDestinationFile(State state) {
        if (state.mFilename != null) {
            // move file xxx.midownload to xxx
            File oldFile = new File(state.mDownloadingFileName);
            if (oldFile.exists()) {
                File newFile = new File(state.mFilename);
                if (newFile.exists()) {
                    newFile.delete();
                }
                newFile.getParentFile().mkdirs();
                oldFile.renameTo(newFile);
                // make sure the file is readable
                FileUtils.setPermissions(state.mFilename, 0644, -1, -1);
            }
        }
    }

    /**
     * Called just before the thread finishes, regardless of status, to take any necessary action on
     * the downloaded file.
     */
    private void cleanupDestination(State state, int finalStatus) {
        if (state.mFilename != null && Downloads.Impl.isStatusError(finalStatus)) {
            XLConfig.LOGD("cleanupDestination() deleting " + state.mFilename);
            new File(state.mDownloadingFileName).delete();
            XLConfig.LOGD("DownloadThread.cleanupDestination: delete file: " + state.mDownloadingFileName);
            state.mFilename = null;
        }
    }

    /**
     * Check if the download has been paused or canceled, stopping the request appropriately if it
     * has been.
     */
    private void checkPausedOrCanceled(State state) throws StopRequestException {
        synchronized (mInfo) {
            if (mInfo.mControl == Downloads.Impl.CONTROL_PAUSED) {
                throw new StopRequestException(
                        Downloads.Impl.STATUS_PAUSED_BY_APP, "download paused by owner");
            }
            if (mInfo.mStatus == Downloads.Impl.STATUS_CANCELED || mInfo.mDeleted) {
                throw new StopRequestException(Downloads.Impl.STATUS_CANCELED, TASK_CONCELED);
            }
        }

        // if policy has been changed, trigger connectivity check
        if (mPolicyDirty) {
            checkConnectivity(true);
        }
    }

    /**
     * Report download progress through the database if necessary.
     */
    private void reportProgress(State state) {
        final long now = SystemClock.elapsedRealtime();

        final long sampleDelta = now - state.mSpeedSampleStart;
        if (sampleDelta > 500) {
            final long sampleSpeed = ((state.mCurrentBytes - state.mSpeedSampleBytes) * 1000)
                    / sampleDelta;

            if (state.mSpeed == 0) {
                state.mSpeed = sampleSpeed;
            } else {
                state.mSpeed = ((state.mSpeed * 3) + sampleSpeed) / 4;
            }

            // Only notify once we have a full sample window
            if (state.mSpeedSampleStart != 0) {
                mNotifier.notifyDownloadSpeed(mInfo.mId, state.mSpeed);
            }

            state.mSpeedSampleStart = now;
            state.mSpeedSampleBytes = state.mCurrentBytes;
        }

        if (state.mCurrentBytes - state.mBytesNotified > Constants.MIN_PROGRESS_STEP &&
            now - state.mTimeLastNotification > Constants.MIN_PROGRESS_TIME) {
            ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
            values.put(ExtraDownloads.COLUMN_DOWNLOADING_CURRENT_SPEED, state.mDownloadingCurrentSpeed);
            //values.put(ExtraDownloads.COLUMN_XL_TASK_OPEN_MARK, state.mXlTaskOpenMark);
            if (1 == state.mXlTaskOpenMark) {
                values.put(ExtraDownloads.COLUMN_XL_ACCELERATE_SPEED, state.mXlAccelerateSpeed);
            }
            if (!mHasUpdateFilesizeToDB && state.mTotalBytes > 0) {
                mHasUpdateFilesizeToDB = true;
                values.put(Downloads.Impl.COLUMN_TOTAL_BYTES, state.mTotalBytes);
            }

            mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
            state.mBytesNotified = state.mCurrentBytes;
            state.mTimeLastNotification = now;
            if (Constants.sDownloadSetNeedToUpdateProgress.contains(mInfo.mId)) {
                mInfo.sendDownloadProgressUpdateIntent();
            }
        }
    }

    /**
     * Report download zero speed when task stoped
     */
    private void reportDownloadZeroSpeed() {
        ContentValues values = new ContentValues();
        values.put(ExtraDownloads.COLUMN_DOWNLOADING_CURRENT_SPEED, 0);
        mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
    }

    /**
     * Write a data buffer to the destination file.
     * @param data buffer containing the data to write
     * @param bytesRead how many bytes to write from the buffer
     */
    private void writeDataToDestination(State state, byte[] data, int bytesRead, OutputStream out)
            throws StopRequestException {
        mStorageManager.verifySpaceBeforeWritingToFile(
                mInfo.mDestination, state.mDownloadingFileName, bytesRead);

        boolean forceVerified = false;
        while (true) {
            try {
                out.write(data, 0, bytesRead);
                return;
            } catch (IOException ex) {
                // TODO: better differentiate between DRM and disk failures
                if (!forceVerified) {
                    // couldn't write to file. are we out of space? check.
                    mStorageManager.verifySpace(mInfo.mDestination, state.mDownloadingFileName, bytesRead);
                    forceVerified = true;
                } else {
                    throw new StopRequestException(Downloads.Impl.STATUS_FILE_ERROR,
                            "Failed to write data: " + ex);
                }
            }
        }
    }

    /**
     * Called when we've reached the end of the HTTP response stream, to update the database and
     * check for consistency.
     */
    private void handleEndOfStream(State state) throws StopRequestException {
        ContentValues values = new ContentValues();
        values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
        if (state.mContentLength == -1) {
            values.put(Downloads.Impl.COLUMN_TOTAL_BYTES, state.mCurrentBytes);
        }
        mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);

        final boolean lengthMismatched = (state.mContentLength != -1)
                && (state.mCurrentBytes != state.mContentLength);
        if (lengthMismatched) {
            if (cannotResume(state)) {
                throw new StopRequestException(STATUS_CANNOT_RESUME,
                        "mismatched content length; unable to resume");
            } else {
                throw new StopRequestException(STATUS_HTTP_DATA_ERROR,
                        "closed socket before end of file");
            }
        }
    }

    private boolean cannotResume(State state) {
        XLConfig.LOGD("cannotResume state=" + state + ", mCurrentBytes=" + state.mCurrentBytes + ", mNoIntegrity=" + mInfo.mNoIntegrity
                + ", mHeaderEtag=" + state.mHeaderETag + ", mHeaderIfRangeId=" + state.mHeaderIfRangeId + ", mMimeType=" + state.mMimeType);
        return (state.mCurrentBytes > 0 && !mInfo.mNoIntegrity && state.mHeaderETag == null
                && state.mHeaderIfRangeId == null) || DownloadDrmHelper.isDrmConvertNeeded(state.mMimeType);
    }

    /**
     * Read some data from the HTTP response stream, handling I/O errors.
     * @param data buffer to use to read data
     * @param entityStream stream for reading the HTTP response entity
     * @return the number of bytes actually read or -1 if the end of the stream has been reached
     */
    private int readFromResponse(State state, byte[] data, InputStream entityStream)
            throws StopRequestException {
        try {
            return entityStream.read(data);
        } catch (IOException ex) {
            // TODO: handle stream errors the same as other retries
            if ("unexpected end of stream".equals(ex.getMessage())) {
                return -1;
            }

            ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
            mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
            if (cannotResume(state)) {
                throw new StopRequestException(STATUS_CANNOT_RESUME,
                        "Failed reading response: " + ex + "; unable to resume", ex);
            } else {
                throw new StopRequestException(STATUS_HTTP_DATA_ERROR,
                        "Failed reading response: " + ex, ex);
            }
        }
    }

    /**
     * Prepare target file based on given network response. Derives filename and
     * target size as needed.
     */
    private void processResponseHeaders(State state, HttpURLConnection conn)
            throws StopRequestException {
        // TODO: fallocate the entire file if header gave us specific length

        if (conn != null) {
            readResponseHeaders(state, conn); 
        }
        // update header values into database
        updateDatabaseFromHeaders(state);
        state.mFilename = Helpers.generateSaveFile(
                mContext,
                mInfo.mUri,
                mInfo.mHint,
                state.mContentDisposition,
                state.mContentLocation,
                state.mMimeType,
                mInfo.mDestination,
                state.mContentLength,
                mStorageManager);
        state.mDownloadingFileName = state.mFilename + Helpers.sDownloadingExtension;
        // correct mimetype
        correctMimeType(state);
        // now filename is generated, and update in into database
        updateFilenameIntoDatabase(state);
        // now we get filename, and check space.
        mStorageManager.verifySpace(mInfo.mDestination, state.mFilename, state.mTotalBytes);
        // check connectivity again now that we know the total size
        checkConnectivity(true);
    }

    /**
     * Update necessary database fields based on values of HTTP response headers that have been
     * read.
     */
    private void updateDatabaseFromHeaders(State state) {
        ContentValues values = new ContentValues();
        values.put(Downloads.Impl._DATA, state.mFilename);
        if (state.mHeaderETag != null) {
            values.put(Constants.ETAG, state.mHeaderETag);
        }
        if (state.mHeaderAcceptRanges != null && state.mHeaderAcceptRanges.equalsIgnoreCase("bytes")
            && !TextUtils.isEmpty(state.mHeaderIfRangeId)) {
            values.put(ExtraDownloads.COLUMN_IF_RANGE_ID, state.mHeaderIfRangeId);
        }
        if (state.mMimeType != null) {
            values.put(Downloads.Impl.COLUMN_MIME_TYPE, state.mMimeType);
        }
        values.put(Downloads.Impl.COLUMN_TOTAL_BYTES, mInfo.mTotalBytes);
        mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
    }
    /**
     * Update generated file name into database.
     */
    private void updateFilenameIntoDatabase(State state) {
        ContentValues values = new ContentValues();
        values.put(Downloads.Impl._DATA, state.mFilename);
        if (state.mMimeType != null) {
            values.put(Downloads.Impl.COLUMN_MIME_TYPE, state.mMimeType);
        }
        mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
    }

    /**
     * Read headers from the HTTP response and store them into local state.
     */
    private void readResponseHeaders(State state, HttpURLConnection conn)
            throws StopRequestException {
        state.mContentDisposition = conn.getHeaderField("Content-Disposition");
        state.mContentLocation = conn.getHeaderField("Content-Location");

        if (state.mMimeType == null) {
            state.mMimeType = Intent.normalizeMimeType(conn.getContentType());
        }

        state.mHeaderETag = conn.getHeaderField("ETag");
        state.mHeaderIfRangeId = conn.getHeaderField("Last-Modified");    // be careful in here - added by xunlei
        state.mHeaderAcceptRanges = conn.getHeaderField("Accept-Ranges");
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            state.mContentLength = getHeaderFieldLong(conn, "Content-Length", -1);    // get file size form http content
        } else {
            XLConfig.LOGD("Ignoring Content-Length since Transfer-Encoding is also defined");
            state.mContentLength = -1;
        }

        state.mTotalBytes = state.mContentLength;
        mInfo.mTotalBytes = state.mContentLength;

        final boolean noSizeInfo = state.mContentLength == -1
                && (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked"));
        if (!mInfo.mNoIntegrity && noSizeInfo) {
            throw new StopRequestException(STATUS_CANNOT_RESUME,
                    "can't know size of download, giving up");
        }
    }

    private void parseRetryAfterHeaders(State state, HttpURLConnection conn) {
        if (conn != null) {
            state.mRetryAfter = conn.getHeaderFieldInt("Retry-After", -1);
        }
        if (state.mRetryAfter < 0) {
            state.mRetryAfter = 0;
        } else {
            if (state.mRetryAfter < Constants.MIN_RETRY_AFTER) {
                state.mRetryAfter = Constants.MIN_RETRY_AFTER;
            } else if (state.mRetryAfter > Constants.MAX_RETRY_AFTER) {
                state.mRetryAfter = Constants.MAX_RETRY_AFTER;
            }
            state.mRetryAfter += Helpers.sRandom.nextInt(Constants.MIN_RETRY_AFTER + 1);
            state.mRetryAfter *= 1000;
        }
    }

    /**
     * Prepare the destination file to receive data.  If the file already exists, we'll set up
     * appropriately for resumption.
     */
    private void setupDestinationFile(State state) throws StopRequestException {
        if (!TextUtils.isEmpty(state.mFilename)) { // only true if we've already run a thread for this download
            XLConfig.LOGD("have run thread before for id: " + mInfo.mId +
                        ", and state.mFilename: " + state.mFilename);
            if (!Helpers.isFilenameValid(state.mFilename,
                    mStorageManager.getDownloadDataDirectory())) {
                // this should never happen
                throw new StopRequestException(Downloads.Impl.STATUS_FILE_ERROR,
                        "found invalid internal destination filename");
            }
            // We're resuming a download that got interrupted
            File f = new File(state.mDownloadingFileName);
            if (f.exists()) {
                XLConfig.LOGD("resuming download for id: " + mInfo.mId +
                            ", and state.mFilename: " + state.mFilename);
                long fileLength = f.length();
                if (fileLength == 0) {
                    // The download hadn't actually started, we can restart from scratch
                    XLConfig.LOGD("setupDestinationFile() found fileLength=0, deleting " + state.mFilename);
                    f.delete();
                    XLConfig.LOGD("DownloadThread.setupDestinationFile: delete file: " + state.mFilename +
                            ", because file length is 0.");
                    state.mFilename = null;
                    XLConfig.LOGD("resuming download for id: " + mInfo.mId +
                                ", BUT starting from scratch again: ");
                // Etag often is null, so we abandon it.
                /*} else if (mInfo.mETag == null && !mInfo.mNoIntegrity) {
                    // This should've been caught upon failure
                    if (Constants.LOGVV) {
                        Log.d(TAG, "setupDestinationFile() unable to resume download, deleting "
                                + state.mFilename);
                    }
                    f.delete();
                    Log.v(Constants.TAG, "DownloadThread.setupDestinationFile: delete file: " + state.mFilename +
                            ", because cannot resume.");
                    throw new StopRequestException(Downloads.Impl.STATUS_CANNOT_RESUME,
                            "Trying to resume a download that can't be resumed");*/
                } else {
                    // All right, we'll be able to resume this download
                    XLConfig.LOGD("resuming download for id: " + mInfo.mId +
                              ", and starting with file of length: " + fileLength);
                    state.mCurrentBytes = (int) fileLength;
                    if (mInfo.mTotalBytes != -1) {
                        state.mContentLength = mInfo.mTotalBytes;
                    }
                    state.mHeaderETag = mInfo.mETag;
                    state.mHeaderIfRangeId = mInfo.mIfRange;
                    state.mContinuingDownload = true;

                    XLConfig.LOGD("resuming download for id: " + mInfo.mId +
                                  ", state.mCurrentBytes: " + state.mCurrentBytes +
                                  ", and setting mContinuingDownload to true: ");
                }
            } else {
                // if file does not exist, means it was deleted before it is finished.
                state.mCurrentBytes = mInfo.mCurrentBytes = 0;
            }
        }
    }

    /**
     * Add custom headers for this download to the HTTP request.
     */
    private void addRequestHeaders(State state, HttpURLConnection conn) {
        for (Pair<String, String> header : mInfo.getHeaders()) {
            conn.addRequestProperty(header.first, header.second);
        }

        // Only splice in user agent when not already defined
        if (conn.getRequestProperty("User-Agent") == null) {
            conn.addRequestProperty("User-Agent", userAgent());
        }

        // Defeat transparent gzip compression, since it doesn't allow us to
        // easily resume partial downloads.
        if (0 == state.mXlTaskOpenMark) {
            conn.setRequestProperty("Accept-Encoding", "identity");
        }

        if (state.mContinuingDownload) {
            if (state.mHeaderETag != null) {
                conn.addRequestProperty("If-Match", state.mHeaderETag);
            }
            if (state.mHeaderIfRangeId != null) {
                conn.addRequestProperty("If-Range", state.mHeaderIfRangeId);
            }
            conn.addRequestProperty("Range", "bytes=" + state.mCurrentBytes + "-");
        }
    }

    /**
     * Stores information about the completed download, and notifies the initiating application.
     */
    private void notifyDownloadCompleted(
            State state, int finalStatus, String errorMsg, int numFailed) {
        notifyThroughDatabase(state, finalStatus, errorMsg, numFailed);
        if (Downloads.Impl.isStatusCompleted(finalStatus)) {
            Constants.sDownloadSetNeedToUpdateProgress.remove(mInfo.mId);
            mInfo.sendDownloadProgressUpdateIntent();
            mInfo.sendIntentIfRequested();
        }
    }

    private void notifyThroughDatabase(
            State state, int finalStatus, String errorMsg, int numFailed) {
        ContentValues values = new ContentValues();
        values.put(Downloads.Impl.COLUMN_STATUS, finalStatus);
        values.put(Downloads.Impl._DATA, state.mFilename);
        values.put(Downloads.Impl.COLUMN_MIME_TYPE, state.mMimeType);
        values.put(Downloads.Impl.COLUMN_LAST_MODIFICATION, mSystemFacade.currentTimeMillis());
        values.put(Downloads.Impl.COLUMN_FAILED_CONNECTIONS, numFailed);
        values.put(Constants.RETRY_AFTER_X_REDIRECT_COUNT, state.mRetryAfter);

        if (!TextUtils.equals(mInfo.mUri, state.mRequestUri)) {
            values.put(Downloads.Impl.COLUMN_URI, state.mRequestUri);
        }

        // save the error message. could be useful to developers.
        if (!TextUtils.isEmpty(errorMsg)) {
            values.put(Downloads.Impl.COLUMN_ERROR_MSG, errorMsg);
        }

        mContext.getContentResolver().update(mInfo.getAllDownloadsUri(), values, null, null);
    }

    private INetworkPolicyListener mPolicyListener = new INetworkPolicyListener.Stub() {
        @Override
        public void onUidRulesChanged(int uid, int uidRules) {
            // caller is NPMS, since we only register with them
            if (uid == mInfo.mUid) {
                mPolicyDirty = true;
            }
            XLConfig.LOGD("onUidRulesChanged uid=" + uid + ", mInfo.uid=" + mInfo.mUid);
        }

        @Override
        public void onMeteredIfacesChanged(String[] meteredIfaces) {
            XLConfig.LOGD("onMeteredIfacesChanged");
            // caller is NPMS, since we only register with them
            mPolicyDirty = true;
        }

        @Override
        public void onRestrictBackgroundChanged(boolean restrictBackground) {
            XLConfig.LOGD("onRestrictBackgroundChanged");
            // caller is NPMS, since we only register with them
            mPolicyDirty = true;
        }
    };

    public static long getHeaderFieldLong(URLConnection conn, String field, long defaultValue) {
        try {
            return Long.parseLong(conn.getHeaderField(field));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Return if given status is eligible to be treated as
     * {@link android.provider.Downloads.Impl#STATUS_WAITING_TO_RETRY}.
     */
    public static boolean isStatusRetryable(int status) {
        switch (status) {
            case STATUS_HTTP_DATA_ERROR:
            case HTTP_UNAVAILABLE:
            case HTTP_INTERNAL_ERROR:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get right MIME type
     * @param state
     */
    private void correctMimeType(State state) {
        // do not change mime type of drm
        if (DownloadDrmHelper.isDrmConvertNeeded(state.mMimeType)) {
            return;
        }
        if (TextUtils.isEmpty(state.mFilename)) {
            return;
        }

        int dotIndex = state.mFilename.lastIndexOf('.');
        boolean missingExtension = dotIndex < 0 || dotIndex < state.mFilename.lastIndexOf('/')
                || dotIndex == (state.mFilename.length() - 1);
        if (missingExtension) {
            return;
        }
        String extension = state.mFilename.substring(dotIndex + 1);

        // get MIME type according to extension name
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if(!TextUtils.isEmpty(mimeType) && !mimeType.equals(state.mMimeType)) {
            state.mMimeType = mimeType;
        }
    }

    private void sendXLTaskStartBroadcast() {
        if (mContext != null) {
            Intent intent = new Intent();
            intent.setAction(XLConfig.ACTION_INTENT_XL_DOWNLOAD_START);
            mContext.sendBroadcast(intent);
        }
    }

    public boolean getVipSwitchStatus() {
         long vipflag = DownloadService.XUNLEI_VIP_ENABLED;
            try {
                Context otherAppsContext = mContext.createPackageContext("com.android.providers.downloads.ui",
                        Context.CONTEXT_IGNORE_SECURITY);
                SharedPreferences sharedPreferences = otherAppsContext
                    .getSharedPreferences(DownloadService.PREF_NAME_IN_UI, Context.MODE_MULTI_PROCESS);
                vipflag = sharedPreferences.getLong(DownloadService.PREF_KEY_XUNLEI_VIP, DownloadService.XUNLEI_VIP_ENABLED);
            } catch (Exception e) {
                XLConfig.LOGD("xunlei(getVipSwitchStatus) ---> no vip flag in ui db.", e);
            }

            return vipflag == DownloadService.XUNLEI_VIP_ENABLED;
     }

    private void checkFileSizeinMobile(State state) throws StopRequestException {
        AndroidHttpClient client = null;
        HttpGet request = null;
        HttpResponse response = null;
        long fileSize = -1;
        String headerTransferEncoding = null;
        Header header = null;

        int index = 0;
        while (index++ < Constants.MAX_REDIRECTS) {
            try {
                client = AndroidHttpClient.newInstance(userAgent(), mContext);
                request = new HttpGet(state.mRequestUri);

                for (Pair<String, String> headers : mInfo.getHeaders()) {
                    request.addHeader(headers.first, headers.second);
                }

                response = client.execute(request);

                header = response.getFirstHeader("Content-Disposition");
                if (header != null) {
                    state.mContentDisposition = header.getValue();
                }
                header = response.getFirstHeader("Content-Location");
                if (header != null) {
                    state.mContentLocation = header.getValue();
                }

                if (state.mMimeType == null) {
                    header = response.getFirstHeader("Content-Type");
                    if (header != null) {
                        state.mMimeType = Intent.normalizeMimeType(header.getValue());
                    }
                }
                header = response.getFirstHeader("ETag");
                if (header != null) {
                    state.mHeaderETag = header.getValue();
                }
                header = response.getFirstHeader("Last-Modified");
                if (header != null) {
                    state.mHeaderIfRangeId = header.getValue();    // be careful in here - added by xunlei
                }
                header = response.getFirstHeader("Accept-Ranges");
                if (header != null) {
                    state.mHeaderAcceptRanges = header.getValue();
                }

                header = response.getFirstHeader("Transfer-Encoding");
                if (header != null) {
                    headerTransferEncoding = header.getValue();
                }

                if (headerTransferEncoding == null) {
                    header = response.getFirstHeader("Content-Length");
                    if (header != null) {
                        String len = header.getValue();
                        state.mContentLength = Long.parseLong(len);    // get file size form http content
                        fileSize = state.mContentLength;
                        state.mTotalBytes = state.mContentLength;
                        mInfo.mTotalBytes = state.mContentLength;

                        // update header values into database
                        updateDatabaseFromHeaders(state);
                        state.mFilename = Helpers.generateSaveFile(
                                                                   mContext,
                                                                   mInfo.mUri,
                                                                   mInfo.mHint,
                                                                   state.mContentDisposition,
                                                                   state.mContentLocation,
                                                                   state.mMimeType,
                                                                   mInfo.mDestination,
                                                                   state.mContentLength,
                                                                   mStorageManager);
                        state.mDownloadingFileName = state.mFilename + Helpers.sDownloadingExtension;
                        // correct mimetype
                        correctMimeType(state);
                        // now filename is generated, and update in into database
                        updateFilenameIntoDatabase(state);
                        // now we get filename, and check space.
                        mStorageManager.verifySpace(mInfo.mDestination, state.mFilename, state.mTotalBytes);
                        break;
                    }
                } else {
                    throw new StopRequestException(Downloads.Impl.STATUS_QUEUED_FOR_WIFI, "No Content-Length");
                }
            } catch (IOException ex) {
                throw new StopRequestException(
                                               Downloads.Impl.STATUS_HTTP_DATA_ERROR,
                                               "while trying to execute request: " + ex.toString(), ex);
            } finally {
                if (client != null) {
                    client.close();
                    client = null;
                }
            }
        } /* End of While */

        int status = Downloads.Impl.STATUS_WAITING_FOR_NETWORK;

        if (index >= Constants.MAX_REDIRECTS) {
            throw new StopRequestException(STATUS_TOO_MANY_REDIRECTS, "Too many redirects");
        }

        Long maxBytesOverMobile = mSystemFacade.getMaxBytesOverMobile();
        if (maxBytesOverMobile != null && fileSize > maxBytesOverMobile) {
            status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
            mInfo.notifyPauseDueToSize(true);
            throw new StopRequestException(status, "download size exceeds limit for mobile network");
        }

        if (mInfo.mBypassRecommendedSizeLimit == 0) {
            Long recommendedMaxBytesOverMobile = mSystemFacade.getRecommendedMaxBytesOverMobile();
            if (recommendedMaxBytesOverMobile != null
                && fileSize > recommendedMaxBytesOverMobile) {
                status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
                throw new StopRequestException(status, "download size exceeds recommended limit for mobile network");
            }
        }
    }
}
