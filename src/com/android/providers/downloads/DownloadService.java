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
 **
 **
 */

package com.android.providers.downloads;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.media.IMediaScannerListener;
import android.media.IMediaScannerService;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.provider.Downloads;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.IndentingPrintWriter;
import com.android.providers.downloads.TokenHelper.TokenHelperListener;
import com.android.providers.downloads.NotificationHelper;
import com.google.android.collect.Maps;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.xunlei.downloadplatforms.XLDownloadManager;
import com.xunlei.downloadplatforms.entity.GetCdnUrl;
import com.xunlei.downloadplatforms.entity.InitParam;
import com.xunlei.downloadplatforms.entity.XLCdnPara;
import com.xunlei.downloadplatforms.entity.XLCloudControlPara;
import com.xunlei.downloadplatforms.util.XLUtil;
import com.xunlei.speedup.manage.SpeedupHttpEngine;
import com.xunlei.speedup.model.Xlsp;
import com.xunlei.speedup.util.Util;

import miui.accounts.ExtraAccountManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * Performs background downloads as requested by applications that use
 * {@link DownloadManager}. Multiple start commands can be issued at this
 * service, and it will continue running until no downloads are being actively
 * processed. It may schedule alarms to resume downloads in future.
 * <p>
 * Any database updates important enough to initiate tasks should always be
 * delivered through {@link Context#startService(Intent)}.
 */
public class DownloadService extends Service {
    // TODO: migrate WakeLock from individual DownloadThreads out into
    // DownloadReceiver to protect our entire workflow.

    private static final boolean DEBUG_LIFECYCLE = false;

    @VisibleForTesting
    SystemFacade mSystemFacade;

    private AlarmManager mAlarmManager;
    private StorageManager mStorageManager;

    /** Observer to get notified when the content observer's data changes */
    private DownloadManagerContentObserver mObserver;

    /** Class to handle Notification Manager updates */
    private DownloadNotifier mNotifier;

    /** Xunlei DownloadManager */
    private XLDownloadManager mXlDownloadManager = null;
    private SharedPreferences mPreferences = null;
    private boolean mXunleiEngineEnable = false;

    public static final long XUNLEI_VIP_ENABLED = 3;
    public static final String PREF_NAME_IN_UI = "com.android.providers.downloads.ui_preferences";
    public static final String PREF_KEY_XUNLEI_VIP = "xl_optdownload_flag";
    private CdnQueryingThread mCdnThread = null;
    private MobileCloudCheckThread mCloudControlThread = null;
    /** The List query CDN*/
    private static CopyOnWriteArrayList<XLCdnPara> mCDNQueryList = new CopyOnWriteArrayList<XLCdnPara>();
    /** The List check XL Control*/
    private static CopyOnWriteArrayList<XLCloudControlPara> mControlCheckList = new CopyOnWriteArrayList<XLCloudControlPara>();

    private final String sUnknownPackage = "unknown";
    private static final int DOWNLOAD_SERVICE_START = 1;
    private static final int DOWNLOAD_SERVICE_STOP = 2;

    /**
     * The Service's view of the list of downloads, mapping download IDs to the corresponding info
     * object. This is kept independently from the content provider, and the Service only initiates
     * downloads based on this data, so that it can deal with situation where the data in the
     * content provider changes or disappears.
     */
    @GuardedBy("mDownloads")
    private final Map<Long, DownloadInfo> mDownloads = Maps.newHashMap();
/*
    private final ExecutorService mExecutor = buildDownloadExecutor();

    private static ExecutorService buildDownloadExecutor() {
        final int maxConcurrent = Resources.getSystem().getInteger(
                com.android.internal.R.integer.config_MaxConcurrentDownloadsAllowed);

        // Create a bounded thread pool for executing downloads; it creates
        // threads as needed (up to maximum) and reclaims them when finished.
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
*/
    private static class MyExecutor{
        private static ExecutorService mExecutor;
        private static HashMap<String, ExecutorService> executorMap = new HashMap<String, ExecutorService>();

        public static ExecutorService getExecutorInstance(String pkg){
            if (executorMap.containsKey(pkg)) {
                return executorMap.get(pkg);
            }

            new MyExecutor();
            if (mExecutor != null)
                executorMap.put(pkg, mExecutor);
            return mExecutor;
        }

        private MyExecutor(){
            int maxConcurrent = Resources.getSystem().getInteger(
                    com.android.internal.R.integer.config_MaxConcurrentDownloadsAllowed);
            if (maxConcurrent > 5) {
                maxConcurrent = 2;
            }

            // Create a bounded thread pool for executing downloads; it creates
            // threads as needed (up to maximum) and reclaims them when finished.
            final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    maxConcurrent, maxConcurrent, 10, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
            executor.allowCoreThreadTimeOut(true);
            mExecutor = executor;
        }
    }

    private DownloadScanner mScanner;

    private HandlerThread mUpdateThread;
    private Handler mUpdateHandler;

    private volatile int mLastStartId;

    /**
     * Receives notifications when the data in the content provider changes
     */
    private class DownloadManagerContentObserver extends ContentObserver {
        public DownloadManagerContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(final boolean selfChange) {
            enqueueUpdate();
        }
    }

    /**
     * Returns an IBinder instance when someone wants to connect to this
     * service. Binding to this service is not allowed.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public IBinder onBind(Intent i) {
        throw new UnsupportedOperationException("Cannot bind to Download Manager Service");
    }

    /**
     * Initializes the service when it is first created
     */
    @Override
    public void onCreate() {
        super.onCreate();

        XLConfig.LOGD("(onCreate) ---> Service onCreate");

        if (mSystemFacade == null) {
            mSystemFacade = new RealSystemFacade(this);
        }

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mStorageManager = new StorageManager(this);

        mUpdateThread = new HandlerThread(Constants.TAG + "-UpdateThread");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper(), mUpdateCallback);

        mScanner = new DownloadScanner(this);

        mNotifier = new DownloadNotifier(this);
        mNotifier.cancelAll();

        mObserver = new DownloadManagerContentObserver();
        getContentResolver().registerContentObserver(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI,
                true, mObserver);

        // get mXunleiEngineEnable from DB
        mXunleiEngineEnable = getXunleiUsagePermission();
        if (mXunleiEngineEnable) {
            startGetXlTokenEx(false);
            initXunleiEngine();
        }

        if (!miui.os.Build.IS_TABLET) {
        	mCdnThread = new CdnQueryingThread();
            mCdnThread.start();
        }

        if (XLUtil.getNetwrokType(getApplicationContext()) == ConnectivityManager.TYPE_MOBILE) {
            mCloudControlThread = new MobileCloudCheckThread();
            mCloudControlThread.start();
        }

        String pkgName = getApplicationContext().getPackageName();
        Helpers.trackDownloadServiceStatus(this.getApplicationContext(), DOWNLOAD_SERVICE_START, pkgName);
        // do track
//        Context ctx = getApplicationContext();
//        String pkgName = ctx.getPackageName();
//        Helpers.trackOnlineStatus(ctx, 0, 0, mXunleiEngineEnable, "", "", pkgName, PRODUCT_NAME, PRODUCT_VERSION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        XLConfig.LOGD("in DownloadService onStartCommand");
        int mCMD_Param = (int)intent.getIntExtra("CMD_TYPE", 0);
        switch(mCMD_Param) {
        case 0:
            break;
        case 1:
            XLConfig.LOGD("xunlei Service ---> receive broadcast, executive getting token process");
            startGetXlTokenEx(true);
            break;
        }

        int returnValue = super.onStartCommand(intent, flags, startId);
        mLastStartId = startId;
        if (mCMD_Param != 1) {
        	enqueueUpdate();
        }
        return returnValue;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        XLConfig.LOGD("in DownloadService onDestroy");
        getContentResolver().unregisterContentObserver(mObserver);
        mScanner.shutdown();
        mUpdateThread.quit();

        if (mXunleiEngineEnable) {
            uninitXunleiEngine();
        }
        if (mCdnThread != null) {
            mCdnThread.setServiceRunning(false);
            mCdnThread = null;
        }
        if (mCloudControlThread != null) {
            mCloudControlThread.setServiceRunning(false);
            mCloudControlThread = null;
        }

        if (mCDNQueryList.size() > 0) {
            mCDNQueryList.clear();
        }
        if (mControlCheckList.size() > 0) {
            mControlCheckList.clear();
        }

        String pkgName = getApplicationContext().getPackageName();
        Helpers.trackDownloadServiceStatus(this.getApplicationContext(), DOWNLOAD_SERVICE_STOP, pkgName);
        // do track
//        Context ctx = getApplicationContext();
//        String pkgName = ctx.getPackageName();
//        Helpers.trackOnlineStatus(ctx, 1, 0, mXunleiEngineEnable, "", "", pkgName, PRODUCT_NAME, PRODUCT_VERSION);
    }

    /**
     * Enqueue an {@link #updateLocked()} pass to occur in future.
     */
    private void enqueueUpdate() {
        synchronized (this) {
            if (mUpdateThread == null) {
                mUpdateThread = new HandlerThread(Constants.TAG + "-UpdateThread");
                mUpdateThread.start();
                mUpdateHandler = new Handler(mUpdateThread.getLooper(), mUpdateCallback);
            }
            mUpdateHandler.removeMessages(MSG_UPDATE);
            mUpdateHandler.obtainMessage(MSG_UPDATE, mLastStartId, -1).sendToTarget();
        }
    }

    /**
     * Enqueue an {@link #updateLocked()} pass to occur after delay, usually to
     * catch any finished operations that didn't trigger an update pass.
     */
    private void enqueueFinalUpdate() {
        mUpdateHandler.removeMessages(MSG_FINAL_UPDATE);
        mUpdateHandler.sendMessageDelayed(
                mUpdateHandler.obtainMessage(MSG_FINAL_UPDATE, mLastStartId, -1),
                5 * MINUTE_IN_MILLIS);
    }

    private static final int MSG_UPDATE = 1;
    private static final int MSG_FINAL_UPDATE = 2;

    private Handler.Callback mUpdateCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            final int startId = msg.arg1;

            if (DEBUG_LIFECYCLE) {
                XLConfig.LOGD("Updating for startId " + startId);
            }

            // Since database is current source of truth, our "active" status
            // depends on database state. We always get one final update pass
            // once the real actions have finished and persisted their state.

            // TODO: switch to asking real tasks to derive active state
            // TODO: handle media scanner timeouts

            final boolean isActive;
            synchronized (mDownloads) {
                isActive = updateLocked();
            }

            if (msg.what == MSG_FINAL_UPDATE) {
                // Dump thread stacks belonging to pool
                for (Map.Entry<Thread, StackTraceElement[]> entry :
                        Thread.getAllStackTraces().entrySet()) {
                    if (entry.getKey().getName().startsWith("pool")) {
                        XLConfig.LOGD(entry.getKey() + ": " + Arrays.toString(entry.getValue()));
                    }
                }

                // Dump speed and update details
                mNotifier.dumpSpeeds();

                XLConfig.LOGD("Final update pass triggered, isActive=" + isActive
                        + "; someone didn't update correctly.");
                // thread quit
                synchronized (DownloadService.this) {
                    mUpdateThread.quit();
                    mUpdateThread = null;
                }
            }

            if (isActive) {
                // Still doing useful work, keep service alive. These active
                // tasks will trigger another update pass when they're finished.

                // Enqueue delayed update pass to catch finished operations that
                // didn't trigger an update pass; these are bugs.
                enqueueFinalUpdate();

            } else {
                // No active tasks, and any pending update messages can be
                // ignored, since any updates important enough to initiate tasks
                // will always be delivered with a new startId.

                if (stopSelfResult(startId)) {
                    if (DEBUG_LIFECYCLE) {
                        XLConfig.LOGD("Nothing left; stopped");
                    }
                    getContentResolver().unregisterContentObserver(mObserver);
                    mScanner.shutdown();
                    mUpdateThread.quit();
                }
            }

            return true;
        }
    };

    /**
     * Update {@link #mDownloads} to match {@link DownloadProvider} state.
     * Depending on current download state it may enqueue {@link DownloadThread}
     * instances, request {@link DownloadScanner} scans, update user-visible
     * notifications, and/or schedule future actions with {@link AlarmManager}.
     * <p>
     * Should only be called from {@link #mUpdateThread} as after being
     * requested through {@link #enqueueUpdate()}.
     *
     * @return If there are active tasks being processed, as of the database
     *         snapshot taken in this update.
     */
    private boolean updateLocked() {
        final long now = mSystemFacade.currentTimeMillis();
        boolean isActive = false;
        long nextActionMillis = Long.MAX_VALUE;

        final Set<Long> staleIds = Sets.newHashSet(mDownloads.keySet());
        final ContentResolver resolver = getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI,
                    null, null, null, null);
            final DownloadInfo.Reader reader = new DownloadInfo.Reader(resolver, cursor);
            final int idColumn = cursor.getColumnIndexOrThrow(Downloads.Impl._ID);
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(idColumn);
                long currentDownloadNextActionMillis = Long.MAX_VALUE;

                DownloadInfo info = mDownloads.get(id);
                if (info != null) {
                    updateDownload(reader, info, now);
                } else {
                    // Check xunlei engine status when create a new task
                    info = insertDownloadLocked(reader, now);
                }

                if (info.mDeleted) {
                    // Delete download if requested, but only after cleaning up
                    if (!TextUtils.isEmpty(info.mMediaProviderUri)) {
                        resolver.delete(Uri.parse(info.mMediaProviderUri), null, null);
                    }

                    // if download has been completed, delete xxx, else delete xxx.midownload
                    if (info.mStatus == Downloads.Impl.STATUS_SUCCESS) {
                        if (info.mFileName != null) {
                            deleteFileIfExists(info.mFileName);
                        }
                    } else {
                        if (info.mFileName != null) {
                            deleteFileIfExists(info.mFileName + Helpers.sDownloadingExtension);
                         }
                    }
                    resolver.delete(info.getAllDownloadsUri(), null, null);
                } else {
                    staleIds.remove(id);
                    // Kick off download task if ready
                    String pkg = TextUtils.isEmpty(info.mPackage) ? sUnknownPackage: info.mPackage;
                    final boolean activeDownload = info.startDownloadIfReady(MyExecutor.getExecutorInstance(pkg));

                    // Kick off media scan if completed
                    final boolean activeScan = info.startScanIfReady(mScanner);

                    // get current download task's next action millis
                    currentDownloadNextActionMillis = info.nextActionMillis(now);

                    if (DEBUG_LIFECYCLE && (activeDownload || activeScan)) {
                        XLConfig.LOGD("Download " + info.mId + ": activeDownload=" + activeDownload
                                + ", activeScan=" + activeScan);
                    }

                    isActive |= activeDownload;
                    isActive |= activeScan;
                    // if equals 0, keep download service on.
                    isActive |= (currentDownloadNextActionMillis == 0);
                }

                // Keep track of nearest next action
                nextActionMillis = Math.min(currentDownloadNextActionMillis, nextActionMillis);
            }
        } catch(SQLiteDiskIOException e) {
            XLConfig.LOGD("error when updateLocked: ", e);
        } catch (Exception e) {
            XLConfig.LOGD("error when updateLocked: ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Clean up stale downloads that disappeared
        for (Long id : staleIds) {
            deleteDownloadLocked(id);
        }

        // Update notifications visible to user
        mNotifier.updateWith(mDownloads.values());

        // Set alarm when next action is in future. It's okay if the service
        // continues to run in meantime, since it will kick off an update pass.
        if (nextActionMillis > 0 && nextActionMillis < Long.MAX_VALUE) {
            XLConfig.LOGD("scheduling start in " + nextActionMillis + "ms");

            final Intent intent = new Intent(Constants.ACTION_RETRY);
            intent.setClass(this, DownloadReceiver.class);
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, now + nextActionMillis,
                    PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT));
        }

        return isActive;
    }

    /**
     * Keeps a local copy of the info about a download, and initiates the
     * download if appropriate.
     */
    private DownloadInfo insertDownloadLocked(DownloadInfo.Reader reader, long now) {
        checkXunleiEngineStatus();
        final DownloadInfo info = reader.newDownloadInfo(
                this, mSystemFacade, mStorageManager, mNotifier, mXunleiEngineEnable, mXlDownloadManager, mPreferences);
        CheckingXLEngineFlagWrite2DB(info);
        mDownloads.put(info.mId, info);

        XLConfig.LOGD("processing inserted download " + info.mId);

        return info;
    }

    /**
     * Updates the local copy of the info about a download.
     */
    private void updateDownload(DownloadInfo.Reader reader, DownloadInfo info, long now) {
        reader.updateFromDatabase(info);
        XLConfig.LOGD("processing updated download " + info.mId + ", status: " + info.mStatus);

        /**
         * read mXlTaskOpenMark flag from DB
         * // task(xunlei) already exist, but xunlei engine is uninit
         */
        if (info.mXlTaskOpenMark == 1 && null == mXlDownloadManager) {
            XLConfig.LOGD("(updateDownload) ---> xunlei task resume but engine uninit, init xunlei engine again.");
            initXunleiEngine();
        }
    }

    /**
     * Removes the local copy of the info about a download.
     */
    private void deleteDownloadLocked(long id) {
        DownloadInfo info = mDownloads.get(id);
        if (info.mStatus == Downloads.Impl.STATUS_RUNNING) {
            info.mStatus = Downloads.Impl.STATUS_CANCELED;
        }
        if (info.mStatus != Downloads.Impl.STATUS_SUCCESS && info.mFileName != null) {
            Slog.d(Constants.TAG, "deleteDownloadLocked() deleting " + info.mFileName);
            deleteFileIfExists(info.mFileName + Helpers.sDownloadingExtension);
        }
        mDownloads.remove(info.mId);
    }

    private void deleteFileIfExists(String path) {
        if (!TextUtils.isEmpty(path)) {
            XLConfig.LOGD("deleteFileIfExists() deleting " + path);
            final File file = new File(path);
            if (file.exists() && !file.delete()) {
                XLConfig.LOGD("file: '" + path + "' couldn't be deleted");
                return;
             }
            XLConfig.LOGD("DownloadService.deleteFileIfExists: delete file: " + path);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        synchronized (mDownloads) {
            final List<Long> ids = Lists.newArrayList(mDownloads.keySet());
            Collections.sort(ids);
            for (Long id : ids) {
                final DownloadInfo info = mDownloads.get(id);
                info.dump(pw);
            }
        }
    }

    private TokenHelper mTokenHelper;
    public void startGetXlTokenEx(boolean tokenStrategy) {
            XLConfig.LOGD("(startGetXlTokenEx) ---> create get token subThread.");
            if (mTokenHelper instanceof TokenHelper) {
                mTokenHelper.RequestToken(tokenStrategy);
            } else {
                XLConfig.LOGD("(startGetXlTokenEx) ---> init TokenHelper class.");
                mTokenHelper =TokenHelper.getInstance();
                mTokenHelper.setTokenHelperListener(mtokenlisten);
                mTokenHelper.RequestToken(tokenStrategy);
            }

    }

    TokenHelperListener mtokenlisten = new TokenHelperListener() {
        @Override
        public int OnTokenGet(int ret, String token) {
            if (token != null && mXlDownloadManager != null) {
                XLConfig.LOGD("(subThread.run) ---> get token from api success, set it to vip hub");
                try {
                    mXlDownloadManager.XLSetUserAccessToken(token);
                } catch(NullPointerException e) {
                }
            }
            return 0;
        }
    };

    /**
     * get xunlei engine enable flag from xml(modifiled by ui)
     * @return
     */
    private boolean getXunleiUsagePermission() {
        boolean xunlei_usage;
        if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) {
            xunlei_usage = false;
        } else {
            SharedPreferences pf = getApplicationContext().getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_MULTI_PROCESS);
            if (!pf.contains(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION)) {
                SharedPreferences.Editor et = pf.edit();
                et.putBoolean(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
                et.commit();
            }
            xunlei_usage  = pf.getBoolean(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
        }
        XLConfig.LOGD("(getXunleiUsagePermission) ---> get xunlei permission from xml:" + xunlei_usage);
        return xunlei_usage;
    }
    /**
     * init Xunlei service
     */
    private synchronized void initXunleiEngine() {
        XLConfig.LOGD("(initXunleiEngine) ---> init xunlei engine service.");

        mXlDownloadManager = XLDownloadManager.getDownloadManager();
        InitParam para = new InitParam();
        para.mPeerId = getXunleiPeerid();
        para.mPartnerId = "10031";
        para.mAppKey = XLConfig.APP_KEY;
        para.mAppName = XLConfig.PRODUCT_NAME;
        para.mAppVersion = XLConfig.PRODUCT_VERSION;
        para.mGuid = Util.md5(para.mPeerId);
        XLConfig.LOGD("initXunleiEngine  mGuid = " + para.mGuid);

        para.mStatCfgSavePath = Environment.getExternalStorageDirectory().getPath() + "/";
        para.mStatSavePath = Environment.getExternalStorageDirectory().getPath() + "/";
        para.mNetType = XLUtil.getNetwrokType(getApplicationContext()) + 1;
        mXlDownloadManager.XLInit(para);
        if (null == mPreferences) {
            mPreferences = getApplicationContext().getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_WORLD_WRITEABLE);
        }

        if (!miui.os.Build.IS_TABLET) {
        	Thread xlViphubThread = new Thread() {
                @Override
                public void run() {
                    String xml_token = mPreferences.getString(XLConfig.PREF_KEY_XUNLEI_TOKEN, "");
                    XLConfig.LOGD("(xlViphubThread.run) ---> get token from xml:" + xml_token);
                    mXlDownloadManager.XLConnectVipHub(xml_token);
                }
            };
            xlViphubThread.start();
        }
    }

    /**
     * uninit xunlei service
     * how to call this function - if some tasks running(xunlei), create a new task(android)
     */
    private void uninitXunleiEngine() {
        XLConfig.LOGD("(uninitXunleiEngine) ---> uninit xunlei engine service.");
        if (null != mXlDownloadManager) {
            mXlDownloadManager.XLUnInit();
            //mXlDownloadManager.disConnectVipHub();
            mXlDownloadManager = null;

            if (null != mPreferences) {
                mPreferences = null;
            }
        }
    }

    /**
     * check xunlei engine when create a new task
     */
    private void checkXunleiEngineStatus() {
//        XLConfig.LOGD(Constants.TAG, "(checkXunleiEngineStatus) ---> check xunlei engine status.");
        Boolean curFlag = getXunleiUsagePermission();
        if (mXunleiEngineEnable != curFlag) {
            mXunleiEngineEnable = curFlag;
            if (mXunleiEngineEnable) {
                // Android download -> Xunlei download
                initXunleiEngine();
            } else {
                // Xunlei download -> Android download
                //uninitXunleiEngine();
            }
        }
    }

    private String getXunleiPeerid() {
        SharedPreferences pf = getApplicationContext().getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_WORLD_WRITEABLE);
        if (!pf.contains(XLConfig.PREF_KEY_XUNLEI_PEERID)) {
            String curPeerid = XLUtil.getPeerid(getApplicationContext());
            SharedPreferences.Editor et = pf.edit();
            et.putString(XLConfig.PREF_KEY_XUNLEI_PEERID, curPeerid);
            et.commit();
            return curPeerid;

        } else {
            String dbPeerid  = pf.getString(XLConfig.PREF_KEY_XUNLEI_PEERID, XLConfig.DEFAULT_PEERID);
            if (dbPeerid.equals(XLConfig.DEFAULT_PEERID)) {
                dbPeerid = XLUtil.getPeerid(getApplicationContext());
                SharedPreferences.Editor et = pf.edit();
                et.putString(XLConfig.PREF_KEY_XUNLEI_PEERID, dbPeerid);
                et.commit();
            }
            return dbPeerid;
        }
    }

    /**
     * the thread that query vip cdn
     * @author jinghuang
     *
     */
    private class CdnQueryingThread extends Thread {
        private boolean isServiceRunning = true;

        public void setServiceRunning(boolean flag) {
            this.isServiceRunning = flag;
        }

        @Override
        public void run() {
            XLConfig.LOGD("(CdnQueryingThread) ---> running");

            int times;
            while (isServiceRunning) {
                if (mCDNQueryList.size() > 0) {
                    for (XLCdnPara xlCdnPara : mCDNQueryList) {
                        if (!xlCdnPara.getCdnQueryedFlag() && xlCdnPara.getQueryedTimes() < XLConfig.XUNLEI_CDN_QUERY_TIMES) {
                            XLConfig.LOGD("(CdnQueryingThread) ---> query cdn, name=" + xlCdnPara.mName);
                            GetCdnUrl cdnurl = new GetCdnUrl();
                            /*
                            int ret = -1;
                            try {
                                ret = mXlDownloadManager.XLGetCdnUrl(xlCdnPara.mUrl,
                                        xlCdnPara.mName,
                                        xlCdnPara.mCid, 1,
                                        xlCdnPara.mGcid, 1,
                                        xlCdnPara.mFileSize,
                                        xlCdnPara.mReferUrl,
                                        xlCdnPara.mCookie,
                                        cdnurl);
                            } catch (Exception e) {
                                // TODO: handle exception
                                return ;
                            }
                            */
                            int ret;
                            synchronized (TokenHelper.getInstance().mutex) {
                                if (mXlDownloadManager == null) {
                                    return;
                                }
                                ret = mXlDownloadManager.XLGetCdnUrl(xlCdnPara.mUrl,
                                        xlCdnPara.mName,
                                        xlCdnPara.mCid, 1,
                                        xlCdnPara.mGcid, 1,
                                        xlCdnPara.mFileSize,
                                        xlCdnPara.mReferUrl,
                                        xlCdnPara.mCookie,
                                        cdnurl);
                            }

                            times = xlCdnPara.getQueryedTimes() + 1;
                            if (0 == ret || 7003 == ret) {
                                // query return success
                                if (cdnurl.mCdnUrl != null && cdnurl.mCdnCookie != null) {
                                    xlCdnPara.setCdn(cdnurl.mCdnUrl);
                                    xlCdnPara.setCdnCookie(cdnurl.mCdnCookie);
                                    XLConfig.LOGD("(CdnQueryingThread) ---> Queryying return success(name:"
                                            + xlCdnPara.mName + "), queryTimes=" + times);
                                } else {
                                    XLConfig.LOGD("(CdnQueryingThread) ---> Queryying cdn not exist!!!(name:"
                                            + xlCdnPara.mName + "), ret=" + ret + ", queryTimes=" + times);
                                }
                                xlCdnPara.setCdnQueryedFlag(true);
                            } else {
                            	if (707 == ret || 706 == ret) {
                            		TokenHelper.getInstance().RequestToken(true);
                            	}
                                // query return error, 7002
                                XLConfig.LOGD("(CdnQueryingThread) ---> Queryying return error!!!(name:"
                                        + xlCdnPara.mName + "), ret=" + ret + ", queryTimes=" + times);
                                xlCdnPara.setQueryedTimes(times);
                                if (times > 1) {
                                    xlCdnPara.setCdnQueryedFlag(true);
                                }
                            }
                        }
                    }

                } else {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }
            }
            XLConfig.LOGD("(CdnQueryingThread) ---> stopped");
        }
    }

/*
 * Add interface: XLDeleteCdnQueryTask(XLCdnPara para);
 */
    public static void XLDeleteCdnQueryTask(XLCdnPara para) {
        for (XLCdnPara xlCdnPara : mCDNQueryList) {
            if (xlCdnPara.mUrl.equals(para.mUrl)) {
                XLConfig.LOGD("(XLAddCdnQueryTask) ---> task already removed, stop cdn query!");
                mCDNQueryList.remove(xlCdnPara);
            }
        }
    }

    public static void XLAddCdnQueryTask(XLCdnPara para) {
        for (XLCdnPara xlCdnPara : mCDNQueryList) {
            if (xlCdnPara.mUrl.equals(para.mUrl))
                XLConfig.LOGD("(XLAddCdnQueryTask) ---> task already exist, CurrentSize="
                        + mCDNQueryList.size());
                return;
        }
        mCDNQueryList.add(para);
        XLConfig.LOGD("(XLAddCdnQueryTask) ---> add new task success, CurrentSize=" + mCDNQueryList.size());
    }

    public static XLCdnPara XLQueryCdn(String url) {
        for (XLCdnPara xlCdnPara : mCDNQueryList) {
            if (xlCdnPara.mUrl.equals(url)) {
                if (xlCdnPara.getCdnQueryedFlag()) {
                    XLConfig.LOGD("(XLQueryCdn) ---> Querying CDN task success! Remove it");
                    mCDNQueryList.remove(xlCdnPara);
                    return xlCdnPara;
                }
            }
        }
        XLConfig.LOGD("(XLQueryCdn) ---> Querying CDN operation did not completed");
        return null;
    }

    public static XLCdnPara XLQueryCdn(XLCdnPara para) {
        boolean has = false;
        for (XLCdnPara xlCdnPara : mCDNQueryList) {
            if (xlCdnPara.mUrl.equals(para.mUrl)) {
                has = true;
                if (xlCdnPara.getCdnQueryedFlag()) {
                    XLConfig.LOGD("(XLQueryCdn) ---> Querying CDN task success! Remove it");
                    mCDNQueryList.remove(xlCdnPara);
                    return xlCdnPara;
                }
            }
        }
        if (!has){
            mCDNQueryList.add(para);
            XLConfig.LOGD("(XLAddCdnQueryTask) ---> retry to add new task success, CurrentSize=" + mCDNQueryList.size());
        }
        XLConfig.LOGD("(XLQueryCdn) ---> Querying CDN operation did not completed!");
        return null;
    }

    private class MobileCloudCheckThread extends Thread {
        private boolean isServiceRunning = true;

        public void setServiceRunning(boolean flag) {
            this.isServiceRunning = flag;
        }

        @Override
        public void run() {
            XLConfig.LOGD("(MobileCloudControlThread) ---> running");
            SpeedupHttpEngine engine = new SpeedupHttpEngine();
            while (isServiceRunning) {
                if (mControlCheckList.size() > 0) {
                    for (XLCloudControlPara xlCheckPara : mControlCheckList) {
                        if (!xlCheckPara.getCheckedFlag()) {
                            Xlsp xl = new Xlsp();
                            xl.setDownUrl(xlCheckPara.mUrl);
                            xl.setAppId("12");
                            xl.setAppVersion("1.0.1");
                            xl.setMiuiId("2");
                            xl.setMiuiVersion("1.0.2");
                            //xl.setPhoneNum("19262162555");
                            boolean flag = engine.reqPushTask(xl, null);
                            XLConfig.LOGD("(MobileCloudControlThread) ---> flag = " + flag);
                            xlCheckPara.setCloudControlFlag(flag);
                            xlCheckPara.setCheckedFlag(true);
                        }
                    }
                } else {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }
            }
            XLConfig.LOGD("(MobileCloudControlThread) ---> stopped");
        }
    }

    public static void XLAddCloudCheckTask(XLCloudControlPara para) {
        for (XLCloudControlPara xlCheckPara : mControlCheckList) {
            if (xlCheckPara.mUrl.equals(para.mUrl))
                XLConfig.LOGD("(XLAddCloudCheckTask) ---> task already exist, CurrentSize="
                                + mControlCheckList.size());
                return;
        }
        mControlCheckList.add(para);
        XLConfig.LOGD("(XLAddCloudCheckTask) ---> add new task success, CurrentSize=" + mControlCheckList.size());
    }

    public static int XLQueryCloudCheck(String url) {
        int queryRet = -1;
        for (XLCloudControlPara xlCheckPara : mControlCheckList) {
            if (xlCheckPara.mUrl.equals(url)) {
                if (xlCheckPara.getCheckedFlag()) {
                    boolean ccFlag = xlCheckPara.getCloudControlFlag();
                    XLConfig.LOGD("(XLQueryCloudCheck) ---> Querying CC task success! Remove it, CloudCrontol = " + ccFlag);
                    mCDNQueryList.remove(xlCheckPara);
                    return ccFlag ? 1 : 0;
                }
            }
        }
        XLConfig.LOGD("(XLQueryCloudCheck) ---> Querying CC operation did not completed");
        return queryRet;
    }

    private void CheckingXLEngineFlagWrite2DB(DownloadInfo info) {
        if (1 == info.mXlTaskOpenMark) {
            ContentValues values = new ContentValues();
            values.put(DownloadManager.ExtraDownloads.COLUMN_XL_TASK_OPEN_MARK, info.mXlTaskOpenMark);
            getContentResolver().update(info.getAllDownloadsUri(), values, null, null);
        }
    }
}
