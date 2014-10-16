package com.android.providers.downloads.ui.notification;

import android.net.Uri;
import android.provider.Downloads;
import android.util.Log;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;

import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.utils.DateUtil;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.FlowInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.ConfigJSInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.SpeedupJSInfo;

/**
 * 此处写类描述
 *
 * @Package com.android.providers.downloads.notification
 * @ClassName: NotificationLogic
 * @author gaogb
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月17日 下午8:09:59
 */
public class NotificationLogic {
    private static String TAG = NotificationLogic.class.getSimpleName();

    private Context mContext;
    // 下载次数
    private int min_download_times = 3;

    // 下载文件大小
    private int min_file_size = 30 * 1024; // 30M *1024

    // 加速提醒周期：N 天
    private int remind_cycle = 1;

    private long currentDownloadFileSize = -1;
    /*
     * config speedup account flow instance
     */
    ConfigJSInstance mConfigJSInstance;
    AccountInfoInstance mAccountJSInstance;
    // 提示信息
    public ConfigJSInfo mConfigJSInfo;
    // 配置信息
    public SpeedupJSInfo mSpeedupJSInfo;
    // 帐号信息
    public AccountInfo mAccountInfo;
    // 流量信息
    public FlowInfo mFlowInfo;
    // 场景1
    public static final int NOTIFICATION_SCENCE_1 = 1;
    // 场景2
    public static final int NOTIFICATION_SCENCE_2 = 2;

    public NotificationLogic(Context ctx) {
        this.mContext = ctx;

        mConfigJSInstance = ConfigJSInstance.getInstance(ctx);
        String token = PreferenceLogic.getInstance(ctx).getToken();
        mAccountJSInstance = AccountInfoInstance.getInstance(ctx, token);
        refreshInfo();
        AppConfig.LOGD(TAG, "NotificationLogic.token=" + token);
        AppConfig.LOGD(TAG, "NotificationLogic.mConfigJSInfo=" + (mConfigJSInfo == null));
        AppConfig.LOGD(TAG, "NotificationLogic.mSpeedupJSInfo=" + (mSpeedupJSInfo == null));
        AppConfig.LOGD(TAG, "NotificationLogic.mAccountInfo=" + (mAccountInfo == null));
        AppConfig.LOGD(TAG, "NotificationLogic.mFlowInfo=" + (mFlowInfo == null));
    }

    private void refreshInfo() {
        if (mConfigJSInstance == null)
            return;
        if (mAccountJSInstance == null)
            return;
        mConfigJSInfo = mConfigJSInstance.getConfigJSInfo();
        mSpeedupJSInfo = mConfigJSInstance.getSpeedJSInfo();
        mAccountInfo = mAccountJSInstance.getAccountInfo();
        mFlowInfo = mAccountJSInstance.getFlowInfo();
    }

    public void setCurrentDownloadFileSize(long currentDownloadFileSize) {
        this.currentDownloadFileSize = currentDownloadFileSize;
    }

    /**
     * 判断第一次提醒的日期和今天的相隔天数是否在给定周期内， in : true, out : false (提醒)
     *
     * @Title: isInRemindPeriod
     * @param context
     * @param remind_cycle
     * @return
     * @return boolean
     * @date 2014年6月17日 下午8:59:46
     */
    public boolean isInRemindCycleDate(int scence) {
        int remind_cycle = mSpeedupJSInfo.scene2_remind_cycle;
        if (scence == NOTIFICATION_SCENCE_1) {
            remind_cycle = mSpeedupJSInfo.scene1_remind_cycle;
        }

        boolean bool = false;
        try {
            String curDate = DateUtil.getDate();
            String remindDate = PreferenceLogic.getInstance().getRemindCycleDate(scence);
            if (!remindDate.equals("") && DateUtil.getDiffDays(remindDate, curDate) < remind_cycle) {
                bool = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
// TODO:7.10 全部返回true 细节待处理
        return false;
    }

    public boolean isExecDownloadTimesForToday() {
        return isExecDownloadTimesForToday(mSpeedupJSInfo.scene1_min_download_times);
    }

    /**
     * 判断是否 超出今天的下载次数
     *
     * @Title: isExecDownloadTimesForToday
     * @param context
     * @param min_download_times
     * @return
     * @return boolean
     * @date 2014年6月17日 下午9:36:55
     */
    public boolean isExecDownloadTimesForToday(int min_download_times) {
        // TODO 暂不支持每天下载次数
        /*
         * if (PreferenceLogic.getInstance().getDownloadTimesForToday() >
         * min_download_times) { return true; }
         */

        return true;
    }

    /**
     * 下载列表任务大小是否大于配置大小
     *
     * @Title: isExecFileSize
     * @param context
     * @return
     * @return boolean
     * @date 2014年6月18日 下午5:29:54
     */
    public boolean isExecFileSize(int sceneMinFileSize) {
        boolean bool = false;
        Cursor translator = null;
        // DownloadManager mDownloadManager = (DownloadManager)
        // mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        // Cursor translator = mDownloadManager.query(new
        // DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING));
        //name=?", // selection
        //       new String[] {name}, //selectionArgs

        long currentDownloadSize = 0;
        try{
        	translator = this.mContext.getContentResolver().query(mBaseUri, UNDERLYING_COLUMNS, Downloads.Impl.COLUMN_STATUS + "=?", new String[] { Downloads.Impl.STATUS_RUNNING + "" }, "");
        }catch(Exception e){
        	e.printStackTrace();
        }

        if (translator != null) {
            while (translator.moveToNext()) {
                // long speed =
                // translator.getLong(translator.getColumnIndexOrThrow(ExtraDownloads.Impl.COLUMN_XL_ACCELERATE_SPEED));
                long fileSize = translator.getLong(translator.getColumnIndexOrThrow(Downloads.Impl.COLUMN_TOTAL_BYTES));
                
                //sceneMinFileSize有可能取不到数据
                if(sceneMinFileSize<1)
                {
                    return false;
                }
                
                if (fileSize / 1024 / 1024 > sceneMinFileSize) {
                    bool = true;
                    break;
                }
            }
            translator.close();
        }
        return bool;
    }

    /**
     * 是否处于低速下载
     *
     * @Title: isContinueLowDown
     * @return
     * @return boolean
     * @date 2014年6月18日 下午6:16:17
     */
    public boolean isContinueLowDown() {
        int slow_second = mSpeedupJSInfo.scene2_slow_second;
        int min_slow_speed = mSpeedupJSInfo.scene2_min_slow_speed;
        if ((getCurrentDownloadFileSize() - currentDownloadFileSize) / slow_second <= min_slow_speed) {
            return true;
        }

        return false;
    }

    // columns to request from DownloadProvider
    // this array must contain all public columns
    // columns to request from DownloadProvider
    private static final String[] UNDERLYING_COLUMNS = new String[] { "total_bytes" };
    private Uri mBaseUri = Downloads.Impl.ALL_DOWNLOADS_CONTENT_URI;

    /**
     * 获取当前下载任务文件大小总和
     *
     * @Title: getCurrentDownloadFileSize
     * @return
     * @return long
     * @date 2014年6月18日 下午5:59:14
     */
    public long getCurrentDownloadFileSize() {
        long currentDownloadSize = 0;
        try {
        	DownloadManager mDownloadManager = (DownloadManager) mContext
                    .getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor translator = mDownloadManager.query(new DownloadManager.Query()
                    .setFilterByStatus(DownloadManager.STATUS_RUNNING));
            if (translator != null) {

                while (translator.moveToNext()) {
                    // long speed =
                    // translator.getLong(translator.getColumnIndexOrThrow(ExtraDownloads.Impl.COLUMN_XL_ACCELERATE_SPEED));
                    long downloadSize = translator.getLong(translator
                            .getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    currentDownloadSize += downloadSize;
                }
                translator.close();
            }
		} catch (Exception e) {
		}

        return currentDownloadSize;
    }

    /**
     * 是否在 WIFI活全网 下提示信息
     *
     * @Title: isShowNotificationInWifi
     * @return
     * @return boolean
     * @date 2014年6月18日 下午5:45:03
     */
    public boolean isShowNotificationInWifi() {
        refreshInfo();
        int opt = ConfigJSInstance.getInstance(mContext).getUseOpt();
        if (opt == ConfigJSInstance.Opt_WifiOnly && DownloadUtils.isWifiAvailable(mContext)) {
            return true;
        } else if (opt == ConfigJSInstance.Opt_NetALL && DownloadUtils.isNetworkAvailable(mContext)) {
            return true;
        } else {
            return false;
        }
        /*
         * boolean setIsWifi = true; // 配置中读取， 没有配置 一期 只考虑wifi if (setIsWifi &&
         * DownloadUtils.isWifiAvailable(mContext)) return true;
         *
         * return false;
         */
    }

    /**
     * 剩余流量在80%或90%或用完提醒（可配）
     *
     * @Title: getShowBeforeGivenFlowOutState
     * @return
     * @return GiveFlowUsedState
     * @date 2014年6月20日 下午3:26:14
     */
    public GiveFlowUsedState getShowBeforeGivenFlowOutState() {
        refreshInfo();
        List<Integer> beforeUsedPercents = mConfigJSInfo.flow_guide_bar_before_used_percent_config;
	    if (beforeUsedPercents == null) {
		    return GiveFlowUsedState.NONE;
	    }
		    Collections.sort(beforeUsedPercents);

        if (null == mFlowInfo) {
            return GiveFlowUsedState.NONE;
        }
        long accountTotalFreeFlow = mFlowInfo.org_total_capacity;
        if (accountTotalFreeFlow == 0) {
            return GiveFlowUsedState.NONE;
        }
        long accountUsedFlow = mFlowInfo.org_used_capacity;// 从会员信息中获取(接口获取)
        
//      获得当前月是否弹流量用完通知
        boolean isShown = PreferenceLogic.getInstance().isBeforeGivenFlowOutShown("NO_FLOW");
        if (isShown) {
        	return GiveFlowUsedState.NONE;
		}
        
//      获取当前日期，因为同一天不可以同时弹出，流量OUT_LIMIT_FLOW和NO_FLOW通知
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_MONTH);
//      获得当前月弹流量即将用户通知是那天
        int saveDay = PreferenceLogic.getInstance().getBeforeGivenFlowDay();
//      当前月是否弹流量即将用完通知
        boolean isShown1 = PreferenceLogic.getInstance().isBeforeGivenFlowOutShown("OUT_LIMIT_FLOW");
        
//      如果本月本天已弹出通知则直接返回
        if (isShown1 && day == saveDay) {
        	return GiveFlowUsedState.NONE;
		}
        
        XLUtil.logDebug(TAG, "getShowBeforeGivenFlowOutState isShown=" + isShown+isShown1+ saveDay);
        
        if (accountUsedFlow >= accountTotalFreeFlow) {
            PreferenceLogic.getInstance().saveBeforeGivenFlowOut("NO_FLOW", true);
            XLUtil.logDebug(TAG, "NO_FLOW");
            return GiveFlowUsedState.NO_FLOW;
        }
        
        long usedPercent = (accountUsedFlow * 100 / accountTotalFreeFlow);
        
        XLUtil.logDebug(TAG, "getShowBeforeGivenFlowOutState accountTotalFreeFlow=" + accountTotalFreeFlow + "accountUsedFlow=" + accountUsedFlow + "accountUsedFlow / accountTotalFreeFlow="
                + usedPercent);
        
        for (int i = beforeUsedPercents.size() - 1; i >= 0; i--) {
            if ((!isShown1) && usedPercent >= beforeUsedPercents.get(i)) {
                PreferenceLogic.getInstance().saveBeforeGivenFlowOut("OUT_LIMIT_FLOW", true);
                PreferenceLogic.getInstance().saveBeforeGivenFlowDay(day);
                XLUtil.logDebug(TAG, "OUT_LIMIT_FLOW");
                return GiveFlowUsedState.OUT_LIMIT_FLOW;
            }
        }
        return GiveFlowUsedState.NONE;
    }

    /**
     * 剩余流量在80%或90%或用完提醒（可配） no isShown
     *
     * @Title: getShowBeforeGivenFlowOutStateWithoutIsShown
     * @return
     * @return GiveFlowUsedState
     * @date 2014年6月20日 下午3:26:14
     */
    public GiveFlowUsedState getShowBeforeGivenFlowOutStateWithoutIsShown() {
        refreshInfo();
        List<Integer> beforeUsedPercents = mConfigJSInfo.flow_guide_bar_before_used_percent_config;
        
        if (beforeUsedPercents == null) {
        	 return GiveFlowUsedState.NONE;
		}
        
        Collections.sort(beforeUsedPercents);
        if (null == mFlowInfo) {
            return GiveFlowUsedState.NONE;
        }
        long accountTotalFreeFlow = mFlowInfo.org_total_capacity;
        if (accountTotalFreeFlow == 0) {
            return GiveFlowUsedState.NONE;
        }
        long accountUsedFlow = mFlowInfo.org_used_capacity;// 从会员信息中获取(接口获取)
        // 80% 是否提醒

        // 无流量情况
        if (accountUsedFlow >= accountTotalFreeFlow) {
//        	PreferenceLogic.getInstance().saveBeforeGivenFlowOut(0, true);
            return GiveFlowUsedState.NO_FLOW;
        }
        long usedPercent = (accountUsedFlow * 100 / accountTotalFreeFlow);
        XLUtil.logDebug(TAG, "getShowBeforeGivenFlowOutStateWithoutIsShown accountTotalFreeFlow=" + accountTotalFreeFlow + "accountUsedFlow=" + accountUsedFlow
                + "accountUsedFlow / accountTotalFreeFlow=" + usedPercent);
        for (int i = beforeUsedPercents.size() - 1; i >= 0; i--) {

            if (usedPercent >= beforeUsedPercents.get(i)) {
//                PreferenceLogic.getInstance().saveBeforeGivenFlowOut(beforeUsedPercents.get(i), true);
                return GiveFlowUsedState.OUT_LIMIT_FLOW;
            }
        }
        return GiveFlowUsedState.NONE;
    }

    public enum GiveFlowUsedState {
        NONE, NO_FLOW, OUT_LIMIT_FLOW
    }

    /**
     * 会员是否到期
     *
     * @Title: isOutDateVip
     * @return
     * @return boolean
     * @date 2014年6月19日 下午6:34:17
     */

    public static enum VipExpireStatus {
        NOACCOUNT,OUTDATE, TODAY,ONEDAY, FOURDAY, SEVENDAY, MOREDAY;
    }

    public VipExpireStatus isOutDateVip() {
        refreshInfo();
        try {
            String curDate = DateUtil.getDate();
            if(mAccountInfo.result !=200){
                return VipExpireStatus.NOACCOUNT;
            }
            String outofDate = XLUtil.convertDateToShowDate(mAccountInfo.expire);
            double diffDay = DateUtil.getDiffDays(curDate, outofDate) ;
            AppConfig.LOGD(TAG, "isOutDateVip curdata=" + curDate + "outofDate=" + outofDate + "diffday=" + diffDay);
            List<Integer> vip_expire_config = mConfigJSInfo.vip_guide_bar_before_expire_expire_config;
            AppConfig.LOGD(TAG, "isOutDateVip vip_expire_config=" + vip_expire_config);
            if (vip_expire_config == null || vip_expire_config.size() != 3) {
                return VipExpireStatus.MOREDAY;
            }

            if (diffDay == vip_expire_config.get(0)) {
                return VipExpireStatus.SEVENDAY;
            } else if (vip_expire_config.get(1) <= diffDay && diffDay < vip_expire_config.get(0)) {
                return VipExpireStatus.FOURDAY;
            } else if (vip_expire_config.get(2) <= diffDay && diffDay < vip_expire_config.get(1)) {
                return VipExpireStatus.ONEDAY;
            } else if (diffDay == 0.0) {
                return VipExpireStatus.TODAY;
            } else if (diffDay < vip_expire_config.get(2)) {
                return VipExpireStatus.OUTDATE;
            } else {
                return VipExpireStatus.MOREDAY;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return VipExpireStatus.MOREDAY;
        }
    }
}
