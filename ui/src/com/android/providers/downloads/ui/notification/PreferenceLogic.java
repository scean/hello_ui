package com.android.providers.downloads.ui.notification;

import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.providers.downloads.ui.DownloadList;
import com.android.providers.downloads.ui.DownloadSettingActivity;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.pay.util.XLUtil;

public class PreferenceLogic {
    private static PreferenceLogic instance;
    private SharedPreferences mSharedPreferences;
    private Context mContext;

    private final String REMIND_CYCLE_DATE = "Remind_Cycle_Date";
    private final String CURRENT_DATE_IS_REMINDED = "current_date_is_reminded";
    private final String DOWNLOAD_TIMES_FOR_TODAY = "download_times_for_today";
    private final String CURRENT_TODAY = "current_today";
    private final String STAGE_ONE_IS_TIP = "stage_one_is_tip";
    private final String STAGE_TWO_IS_TIP = "stage_two_is_tip";

    /**
     * 会员到期提醒
     */
    private final String VIP_OUTOF_DATE_TODAY = "vip_outof_date_today";
    private final String VIP_OUTOF_DATE_ONE = "vip_outof_date_one";
    private final String VIP_OUTOF_DATE_FOUR = "vip_outof_date_four";
    private final String VIP_OUTOF_DATE_SEVEN = "vip_outof_date_seven";
    private final String VIP_OUTOF_DATE = "vip_outof_date";

    /**
     * 赠送流量用完前提示
     */
    private final String BEFORE_GIVEN_FLOW_OUT = "before_given_flow_out";
    // 使用迅雷下载引擎
    private final String PREF_KEY_XUNLEI_USAGE_PERMISSION = DownloadSettingActivity.PREF_KEY_XUNLEI_USAGE_PERMISSION;

    /**赠送流量提醒**/
    private final String GIVEN_FLOW = "given_flow";
    
    private PreferenceLogic(Context context) {
        mContext = context;
    }

    public static PreferenceLogic init(Context context) {
        if (null == instance) {
            instance = new PreferenceLogic(context);
        }
        return instance;
    }

    public static PreferenceLogic getInstance() {
        return instance;
    }

    public static PreferenceLogic getInstance(Context context) {
        return init(context);
    }

    @SuppressLint("WorldWriteableFiles")
    private SharedPreferences getSharedPreference() {
        if (null == mSharedPreferences) {
             Context ct = null;
            try {
                ct = mContext.createPackageContext(DownloadList.DOWNLOADPROVIDER_PKG_NAME, Context.CONTEXT_IGNORE_SECURITY);
                mSharedPreferences = ct.getSharedPreferences(
                        DownloadList.PREF_NAME, Context.MODE_MULTI_PROCESS);
            } catch (Exception e) {
            }
        }
        return mSharedPreferences;
    }

    private void saveStringPre(String key, String value) {
        SharedPreferences mPreferces = getSharedPreference();
		if (mPreferces != null) {
			mPreferces.edit().putString(key, value).commit();
		}

    }

    private String getStringPre(String key) {
        SharedPreferences mPreferces = getSharedPreference();
		if (mPreferces != null) {
			return mPreferces.getString(key, "");
		}
		return "";
    }

    private void saveBooleanPre(String key, boolean value) {
        SharedPreferences mPreferces = getSharedPreference();
		if (mPreferces != null) {
			mPreferces.edit().putBoolean(key, value).commit();
		}

    }

    private boolean getBooleanPre(String key) {
        SharedPreferences mPreferces = getSharedPreference();
		if (mPreferces == null) {
			return true;
		} else {
			return mPreferces.getBoolean(key, true);
		}

    }

    private void saveIntPre(String key, int value) {
        SharedPreferences mPreferces = getSharedPreference();
		if (mPreferces != null) {
			mPreferces.edit().putInt(key, value).commit();
		}

    }

    private int getIntPre(String key) {
        SharedPreferences mPreferces = getSharedPreference();
        return mPreferces.getInt(key, 0);
    }

    private void saveLongPre(String key, long value) {
        SharedPreferences mPreferces = getSharedPreference();
        mPreferces.edit().putLong(key, value).commit();
    }

    private long getLongPre(String key) {
        SharedPreferences mPreferces = getSharedPreference();
        return mPreferces.getLong(key, 0);
    }

    public String getToken() {
        return XLUtil.getStringPackagePreference(mContext);
    }


    /**
     * Save remind date
     * key = 1 场景1； key =2 场景2
     * @Title: saveRemindCycleDate
     * @param key
     * @param value
     * @return void
     * @date 2014年6月20日 下午4:58:01
     */
    public void saveRemindCycleDate(int key, String value) {
        saveStringPre(REMIND_CYCLE_DATE + key, value);
    }

    public String getRemindCycleDate(int key) {
        return getStringPre(REMIND_CYCLE_DATE + key);
    }

    // save reminded for today
    public void saveRemindedForToday(boolean value) {
        saveBooleanPre(CURRENT_DATE_IS_REMINDED, value);
    }

    public boolean getRemindedForToday() {
        return getBooleanPre(CURRENT_DATE_IS_REMINDED);
    }

    /**
     * 保存当天日期：记录当天下载次数， 日期发生改变 重置 下载次数； 重置场景一、二提示为false
     *
     * @Title: saveToday
     * @param context
     * @param value
     * @return void
     * @date 2014年6月18日 下午7:04:49
     */
    public void saveToday() {
        String date = DateUtil.getDate();
        String saveValue = getStringPre(CURRENT_TODAY);
        if (null == saveValue || !date.equals(saveValue)) {
            saveStringPre(CURRENT_TODAY, date);
            resetDownloadTimesForToday();
//            saveStageOneIsTip(false);
//            saveStageTwoIsTip(false);
            // 初始化流量使用
//            List<Integer> beforeUsedPercents = ConfigJSInstance.getInstance(mContext).getConfigJSInfo().flow_guide_bar_before_used_percent_config;
//            saveBeforeGivenFlowOut(0, false);
//            for (Integer percent : beforeUsedPercents) {
//                saveBeforeGivenFlowOut(percent, false);
//            }
        }
    }

    public String getToday() {
        return getStringPre(CURRENT_TODAY);
    }

    // save download times for today
    public void saveDownloadTimesForToday() {
        saveIntPre(DOWNLOAD_TIMES_FOR_TODAY, getIntPre(DOWNLOAD_TIMES_FOR_TODAY) + 1);
    }

    // 重置当天下载次数
    public void resetDownloadTimesForToday() {
        saveIntPre(DOWNLOAD_TIMES_FOR_TODAY, 0);
    }

    // 获取当天下载次数
    public int getDownloadTimesForToday() {
        return getIntPre(DOWNLOAD_TIMES_FOR_TODAY);
    }

    // 保存当天场景一提示
    public void saveStageOneIsTip(boolean isHaveTip) {
        saveBooleanPre(STAGE_ONE_IS_TIP, isHaveTip);
    }

    public boolean getStageOneIsTip() {
        return getBooleanPre(STAGE_ONE_IS_TIP);
    }

    // 保存当天场景二提示
    public void saveStageTwoIsTip(boolean isHaveTip) {
        saveBooleanPre(STAGE_TWO_IS_TIP, isHaveTip);
    }

    public boolean getStageTwoIsTip() {
        return getBooleanPre(STAGE_TWO_IS_TIP);
    }

    public void saveBeforeGivenFlowOut(String ext, boolean hasShown) {
        saveBooleanPre(BEFORE_GIVEN_FLOW_OUT + ext, hasShown);
    } 
    public boolean isGivenFlowShown(int ext) {
        return getBooleanPre(GIVEN_FLOW + ext);
    }
    
    public void saveGivenFlow(int ext, boolean hasShown) {
        saveBooleanPre(GIVEN_FLOW + ext, hasShown);
    }

    public boolean isBeforeGivenFlowOutShown(String ext) {
        return getBooleanPre(BEFORE_GIVEN_FLOW_OUT + ext);
    }
    public void saveBeforeGivenFlowDay(int day) {
        saveIntPre(BEFORE_GIVEN_FLOW_OUT , day);
    }
    public int getBeforeGivenFlowDay() {
        return getIntPre(BEFORE_GIVEN_FLOW_OUT);
    }

    public void saveVipExpireOneIsTip(boolean isHaveTip) {
        saveBooleanPre(VIP_OUTOF_DATE_ONE, isHaveTip);
    }

    public boolean getVipExpireOneIsTip() {
        return getBooleanPre(VIP_OUTOF_DATE_ONE);
    }
    public void saveVipExpireTodayIsTip(boolean isHaveTip) {
        saveBooleanPre(VIP_OUTOF_DATE_TODAY, isHaveTip);
    }

    public boolean getVipExpireTodayIsTip() {
        return getBooleanPre(VIP_OUTOF_DATE_TODAY);
    }

    public void saveVipExpireFourIsTip(boolean isHaveTip) {
        saveBooleanPre(VIP_OUTOF_DATE_FOUR, isHaveTip);
    }

    public boolean getVipExpireFourIsTip() {
        return getBooleanPre(VIP_OUTOF_DATE_FOUR);
    }

    public void saveVipExpireSevenIsTip(boolean isHaveTip) {
        saveBooleanPre(VIP_OUTOF_DATE_SEVEN, isHaveTip);
    }

    public boolean getVipExpireSevenIsTip() {
        return getBooleanPre(VIP_OUTOF_DATE_SEVEN);
    }

    public void saveVipExpireIsTip(boolean isHaveTip) {
        saveBooleanPre(VIP_OUTOF_DATE, isHaveTip);
    }

    public boolean getVipExpireIsTip() {
        return getBooleanPre(VIP_OUTOF_DATE);
    }

    
    public void setIsHaveUseXunleiDownload(boolean value){
    	saveBooleanPre(PREF_KEY_XUNLEI_USAGE_PERMISSION, value);
    }
    
    // 是否使用迅雷下载引擎
    public boolean getIsHaveUseXunleiDownload() {
        if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD)
		//if(true)
		{
            return false;
        } else {
            SharedPreferences mPreferces = getSharedPreference();
            return mPreferces.getBoolean(PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
        }
    }

    public void setFirstReceive(boolean isFirst) {
        SharedPreferences mPreferces = getSharedPreference();
        mPreferces.edit().putBoolean(IS_FIRST_GET, isFirst).commit();
    }

    public boolean getFirstReceive() {
        SharedPreferences mPreferces = getSharedPreference();
        return mPreferces.getBoolean(IS_FIRST_GET, true);
    }

    public void setShowNotification(boolean isFirst) {
        SharedPreferences mPreferces = getSharedPreference();
        mPreferces.edit().putBoolean(SHOW_NOTIFICATION, isFirst).commit();
    }

    public boolean getShowNotification() {
        SharedPreferences mPreferces = getSharedPreference();
        return mPreferces.getBoolean(SHOW_NOTIFICATION, true);
    }

    private final String IS_FIRST_GET = "is_first_get";
    private final String SHOW_NOTIFICATION = "show_notification";
}
