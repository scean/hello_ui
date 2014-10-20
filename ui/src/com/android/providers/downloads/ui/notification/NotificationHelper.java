package com.android.providers.downloads.ui.notification;

import java.awt.font.TextAttribute;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.R.integer;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.utils.DateUtil;
import com.android.providers.downloads.ui.notification.NotificationLogic.VipExpireStatus;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountListener;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AddFlowInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.FlowInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;

import com.android.providers.downloads.ui.R;

import miui.maml.util.AppIconsHelper;

/**
 * 消息辅助类， VIP, 流量判断， 场景1， 场景2 情况的判断
 * 
 * @author gaogb
 * @Package com.android.providers.downloads.ui.notification
 * @ClassName: NotificationHelper
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月28日 下午9:08:01
 */
public class NotificationHelper {
    private static final String TAG = NotificationHelper.class.getSimpleName();

	private static NotificationHelper instance;
	private Context mContext;
	private final int FLAG_SHOW_STAGETWO_NOTIFICATION = 0;
	private long old_downed_file_size = -1;
	private NotificationManager mNotifManager;
	private final int FLOATING_TIME = 5;
	public static final int DOWNLOAD_NOTIFICATION_ID = 1001;

	/** 是否已领取流量 */
	private static boolean flag = false;
	/** 首月通知 */
	private static boolean firstFlag = false;

	/** 是否使用迅雷下载 ***/
	private static boolean isUsingXunleiDownload = false;

	/** 是否登录 ***/
	private static boolean isLogined = false;

	/** 是否授权 */
	private static boolean isAuthed = false;

	/** 是否是会员 */
	private static boolean isVipAccount = false;


    private PreferenceLogic mPreferenceLogic;
	private NotificationLogic logic ;

	public NotificationHelper(Context context) {
		mContext = context;
        mPreferenceLogic = PreferenceLogic.getInstance(context);
	}

	public static void init(Context context) {
		if (null == instance) {
			instance = new NotificationHelper(context);
		}

		// 屏蔽国际版通知和英文
		if ((miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD)
            && !XLUtil.isLanguageCNorTW()) {

		} else {
			instance.doInit();
		}
	}

	public static NotificationHelper getInstance() {
		return instance;
	}

	private synchronized void doInit() {
		logic = new NotificationLogic(mContext);
		isUsingXunleiDownload = AccountLogic.getInstance().isUsingXunleiDownload(mContext);
		isLogined = AccountLogic.getInstance().isLogined(mContext);
		isAuthed = AccountLogic.getInstance().isAuthed(mContext);
		isVipAccount = AccountLogic.getInstance().isVipXunleiAccount(mContext);
		boolean isUseServe = ConfigJSInstance.getInstance(mContext).getUseOpt() == ConfigJSInstance.Opt_NetALL;

		AppConfig.LOGD(TAG, "NotificationReveiver"+isUsingXunleiDownload+isLogined+isAuthed+isVipAccount);

		// 正在下载的文件是否大于1M,用于判断有没有任务在下载 有网络
		if (logic.isExecFileSize(1) && logic.isShowNotificationInWifi()) {
			// 使用下载引擎，开启了下载服务
			if (isUsingXunleiDownload && isUseServe) {
				// 登录，绑定
				if (isLogined && isAuthed) {
					// 判断会员是否到期
					if (isVipAccount) {
						if (judgeVipExpire()) {
							return;
						}
					} else {
						// 判断流量使用情况
						if (isShowOutFlow()) {
							return;
						}
					}
				}
			}
		}

		// 登录小米 ,WIFI是否显示
		if (logic.isShowNotificationInWifi()) {
			showStageOneNotification();
		}

		// 首页领取通知
		showNotification();

	}

	public void showLoginNotification() {
		String methodName = XLUtil.getTagString(this.getClass()) + "_"
            + "showLoginNotification";
		AccountInfoInstance mAccountInfoInstance = AccountInfoInstance
            .getInstance(mContext,
                         XLUtil.getStringPackagePreference(mContext));

		mAccountInfoInstance.setAccountListener(new AccountListener() {
                @Override
                public int OnFlowRtn(int ret, FlowInfo flow, String msg) {
                    if (flow != null && flow.historysend == 0) {
                        AppConfig.LOGD(TAG, "NotificationReveiver flow.historysend"
                                       + flow.historysend);
                        showLoginFirstMonth();
                    }
                    return 0;
                }

                @Override
                public int OnAddFlowRtn(int ret, AddFlowInfo addflow, String msg) {

                    return 0;
                }

                @Override
                public int OnAccountRtn(int ret, AccountInfo account, String msg) {
                    return 0;
                }
            });
		mAccountInfoInstance.requestFlow(methodName);

	}

	public void showLoginFirstMonth() {
		if (isLogined
            && !mPreferenceLogic.getStageOneIsTip()
            && logic.isExecFileSize(logic.mSpeedupJSInfo.scene1_min_file_size)) {
			// 不在弹出
            mPreferenceLogic.saveStageOneIsTip(true);

			if (isAuthed && isVipAccount) {// 已绑定迅雷账号
				showSpeedUpNotification();
			} else {
				AppConfig.LOGD(TAG, "未绑定迅雷帐号");
				if (isUsingXunleiDownload) {
					String token = mPreferenceLogic.getToken();
					AccountInfoInstance accoutnInstance = AccountInfoInstance
                        .getInstance(mContext, token);
					PendingIntent pendingIntent = null;
					int flag = 0;
					if (isAuthed) {
						flag = 3;
						pendingIntent = AccountLogic.getInstance()
                            .getToDownloadListPendingIntent(mContext,
                                                            NotificationReveiver.NOTICE_FLOW_SHOW3);
					} else {
						if (accoutnInstance.isFakeAuthed()) {
							flag = 1;
							pendingIntent = AccountLogic
                                .getInstance()
                                .getToDownloadListPendingIntent(
                                                                mContext,
                                                                NotificationReveiver.NOTICE_FLOW_SHOW1);
						} else {
							flag = 2;
							pendingIntent = AccountLogic
                                .getInstance()
                                .getToDownloadListPendingIntent(
                                                                mContext,
                                                                NotificationReveiver.NOTICE_FLOW_SHOW2);
						}
					}
					// 上报
					DownloadUtils.trackBehaviorEvent(mContext,
                                                     "notice_flow_show", 0, flag);

					showXiaomiNotification(
                                           logic.mConfigJSInfo.speed_guide_bar_login_unbind_scene1_title,
                                           logic.mConfigJSInfo.speed_guide_bar_login_unbind_scene1_content,
                                           logic.mConfigJSInfo.speed_guide_bar_login_unbind_scene1_btn,
                                           pendingIntent);
				}
			}
		}
	}

	public void showNotification() {
		String methodName = XLUtil.getTagString(this.getClass()) + "_"
            + "initAccountInfo";
		Calendar c = Calendar.getInstance();
		int month = c.get(Calendar.MONTH);
		// 如果单月没有弹出则注册
		if (!mPreferenceLogic.isGivenFlowShown(month) && !flag) {
			flag = true;
			AccountInfoInstance mAccountInfoInstance = AccountInfoInstance
                .getInstance(mContext,
                             XLUtil.getStringPackagePreference(mContext));
			AppConfig.LOGD(TAG, "NotificationHelper.java showNotification() request flow");
			
			mAccountInfoInstance.setAccountListener(new AccountListener() {

                    @Override
                    public int OnFlowRtn(int ret, FlowInfo flow, String msg) {
                        if (flow != null) {
                            Calendar c = Calendar.getInstance();
                            int month = c.get(Calendar.MONTH);
                            boolean flag = ConfigJSInstance.getInstance(
                                                                        mContext.getApplicationContext()).getUseOpt() == ConfigJSInstance.Opt_NetALL;
                            AppConfig.LOGD(TAG, " ret = "
                                           + ret
                                           + "flow.historysend  =  "
                                           + flow.historysend
                                           + "msg = "
                                           + msg
                                           + mPreferenceLogic
                                           .isGivenFlowShown(month));
                            if (ret == 0
								&& flow.autosend == 1
								&& flow.historysend > 1
								&& AccountLogic.getInstance().isUsingXunleiDownload(mContext)
								&& !mPreferenceLogic.isGivenFlowShown(month)
								&& AccountLogic.getInstance().isLogined(mContext)
								&& flag
								&& !AccountLogic.getInstance().isVipXunleiAccount(mContext)) {
                                showXiaomiNotification(logic.mConfigJSInfo.flow_gift_next_month_title,logic.mConfigJSInfo.flow_gift_next_month_content, null,
                                                       AccountLogic.getInstance()
                                                       .getToAutoObtainOneMonth(mContext));
                                mPreferenceLogic.saveGivenFlow(month, true);
                                mPreferenceLogic.saveBeforeGivenFlowDay(-1);// 当前月没有弹出流量即将用完通知
                                mPreferenceLogic.saveBeforeGivenFlowOut("NO_FLOW", false);// 当前月没有弹出流量用完通知
                                mPreferenceLogic.saveBeforeGivenFlowOut("OUT_LIMIT_FLOW", false);// 当前月没有弹出流量用完通知
                            }

                            month = --month < 0 ? 12 : month;
                            mPreferenceLogic.saveGivenFlow(month, false);
                        }
                        return 0;
                    }

                    @Override
                    public int OnAddFlowRtn(int ret, AddFlowInfo addflow, String msg) {

                        return 0;
                    }

                    @Override
                    public int OnAccountRtn(int ret, AccountInfo account, String msg) {
                        return 0;
                    }
                });
			mAccountInfoInstance.requestFlow(methodName);
		}

	}

	private boolean getXunleiUsagePermission() {
		return mPreferenceLogic.getIsHaveUseXunleiDownload();
	}

	/**
	 * 当日下载次数超过3次 并 当前下载文件>30M 并且当天没有提示过
	 * 
	 * @param logic
	 * @return void
	 * @Title: showStage1Notification
	 * @date 2014年6月20日 下午5:29:22
	 */
	private void showStageOneNotification() {
		
		// 场景一 ： 当前下载文件>60M 并且当天没有提示过
		if (!mPreferenceLogic.getStageOneIsTip()
            && logic.isExecFileSize(logic.mSpeedupJSInfo.scene1_min_file_size)) {
			
			if (isLogined ) {// 已登录
				if (!firstFlag) {
					if (isAuthed) {
						showLoginNotification();
					}
					else {
						showLoginFirstMonth();
					}
					firstFlag = true;//保证只注册一个请求流量回调
				}
			} else {
				if (isUsingXunleiDownload) {
                    //					不再弹出
					mPreferenceLogic.saveStageOneIsTip(true);
					// 上报
					DownloadUtils.trackBehaviorEvent(mContext,
                                                     "notice_flow_show", 0, 0);
                    //					弹出绑定通知
					showXiaomiNotification(
                                           logic.mConfigJSInfo.speed_guide_bar_unlogin_scene1_title,
                                           logic.mConfigJSInfo.speed_guide_bar_unlogin_scene1_content,
                                           logic.mConfigJSInfo.speed_guide_bar_unlogin_scene1_btn,
                                           AccountLogic
                                           .getInstance()
                                           .getGoToXiaomiLoginPendingIntent(
                                                                            mContext,
                                                                            NotificationReveiver.NOTICE_FLOW_SHOW0));
				}
			}

			// 记录周期的提醒日期
			mPreferenceLogic.saveRemindCycleDate(NotificationLogic.NOTIFICATION_SCENCE_1, DateUtil.getDate());
		}
	}

	/**
	 * 开启迅雷加速及未加速提醒
	 * 
	 * @param logic
	 * @return void
	 * @Title: showSpeedUpNotification
	 * @date 2014年6月20日 下午3:11:25
	 */
	private void showSpeedUpNotification() {
		if (logic == null && null == logic.mFlowInfo)
        return;

		// 开启迅雷加速
		if (isUsingXunleiDownload) {
			if (mPreferenceLogic.getFirstReceive()) {
				AppConfig.LOGD(TAG, "开启迅雷加速， 赠送流量提示:尊敬的迅雷会员，已为您开启手机下载加速服务！");
				DownloadUtils.trackBehaviorEvent(mContext, "notice_flow_show",
                                                 0, 4);
				showXiaomiNotification(
                                       logic.mConfigJSInfo.speed_guide_bar_login_bind_scene1_title,
                                       logic.mConfigJSInfo.speed_guide_bar_login_bind_scene1_content,
                                       "",
                                       AccountLogic.getInstance()
                                       .getToDownloadListPendingIntent(mContext,
                                                                       NotificationReveiver.NOTICE_FLOW_SHOW4));
				mPreferenceLogic.setFirstReceive(false);
			}
		}
	}


	/**
	 * 判断账号流量使用情况，没有流量，或者到达限定值，返回true
	 * 
	 * @param logic
	 * @return
	 */
	private boolean isShowOutFlow() {
		NotificationLogic logic = new NotificationLogic(mContext);
		// 表示上班，为流量用完
		PendingIntent mPendingIntent = null;
		switch (logic.getShowBeforeGivenFlowOutState()) {
		case NO_FLOW:
			AppConfig.LOGD(TAG, "判断流量使用: 无流量");
			mPendingIntent = AccountLogic.getInstance()
                .getToVipExpirePendingIntent(mContext,
                                             NotificationReveiver.NOTICE_FLOWSTARTUS_SHOW1);
			DownloadUtils.trackBehaviorEvent(mContext,
                                             "notice_flowstatus_show", 1, 0);
			showXiaomiNotification(logic.mConfigJSInfo.gift_flow_use_up_tile,
                                   logic.mConfigJSInfo.gift_flow_use_up_content,
                                   null, mPendingIntent);
			return true;
            /*	
                case OUT_LIMIT_FLOW:
                AppConfig.LOGD(TAG, "判断流量使用: 已80%或90%流量");
                mPendingIntent = AccountLogic.getInstance()
                .getToVipExpirePendingIntent(mContext,
                NotificationReveiver.NOTICE_FLOWSTARTUS_SHOW0);
                DownloadUtils.trackBehaviorEvent(mContext,
                "notice_flowstatus_show", 0, 0);
                showXiaomiNotification(
                logic.mConfigJSInfo.gift_flow_before_use_up_title,
                logic.mConfigJSInfo.gift_flow_before_use_up_content,null,
                mPendingIntent);
                return true;
            //*/	
		default:
			return false;
		}
	}

	/**
	 * 此处写方法描述
	 * 
	 * @return void
	 * @Title: judgeVipExpire
	 * @date 2014年6月19日 下午8:18:51
	 */
	private boolean judgeVipExpire() {
		NotificationLogic logic = new NotificationLogic(mContext);
		if (null == logic.mAccountInfo)
        return false;
		// oneDay, fourDay, sevenDay, moreDay;
		switch (logic.isOutDateVip()) {

		case TODAY:
			AppConfig.LOGD(TAG, "时间到期了 ，是今天");
			if (!mPreferenceLogic.getVipExpireTodayIsTip()) {
				showXiaomiNotification(
                                       logic.mConfigJSInfo.vip_guide_bar_expire_title,
                                       logic.mConfigJSInfo.vip_guide_bar_expire_content,
                                       "",
                                       AccountLogic.getInstance().getToVipExpirePendingIntent(
                                                                                              mContext));
				mPreferenceLogic.saveVipExpireTodayIsTip(true);
				DownloadUtils.trackBehaviorEvent(mContext,
                                                 "notice_VIPstatus_show", 2, 0);
			}
			return true;
            /*	
                case OUTDATE:
                if (!PreferenceLogic.getInstance().getVipExpireIsTip()) {
				AppConfig.LOGD(TAG, "判断Vip过期: 已过期");
				DownloadUtils.trackBehaviorEvent(mContext,
                "notice_VIPstatus_show", 3, 0);
				showXiaomiNotification(
                logic.mConfigJSInfo.vip_guide_bar_expire_title,
                logic.mConfigJSInfo.vip_guide_bar_expire_content,
                "",
                AccountLogic.getInstance().getToVipExpirePendingIntent(
                mContext,
                NotificationReveiver.NOTICE_VIPSTARTUS_SHOW3));
				PreferenceLogic.getInstance().saveVipExpireIsTip(true);
                }

                return true;
                case ONEDAY:
                if (!PreferenceLogic.getInstance().getVipExpireOneIsTip()) {
				AppConfig.LOGD(TAG, "判断Vip过期: 1-3天");
				DownloadUtils.trackBehaviorEvent(mContext,
                "notice_VIPstatus_show", 2, 0);
				showXiaomiNotification(
                logic.mConfigJSInfo.vip_guide_bar_before_expire_title,
                logic.mConfigJSInfo.vip_guide_bar_before_expire_content,
                "",
                AccountLogic.getInstance().getToVipExpirePendingIntent(
                mContext,
                NotificationReveiver.NOTICE_VIPSTARTUS_SHOW2));
				PreferenceLogic.getInstance().saveVipExpireOneIsTip(true);
                }

                return true;
                case FOURDAY:
                if (!PreferenceLogic.getInstance().getVipExpireFourIsTip()) {
				AppConfig.LOGD(TAG, "判断Vip过期: 4-6天");
				DownloadUtils.trackBehaviorEvent(mContext,
                "notice_VIPstatus_show", 1, 0);
				showXiaomiNotification(
                logic.mConfigJSInfo.vip_guide_bar_before_expire_title,
                logic.mConfigJSInfo.vip_guide_bar_before_expire_content,
                "",
                AccountLogic.getInstance().getToVipExpirePendingIntent(
                mContext,
                NotificationReveiver.NOTICE_VIPSTARTUS_SHOW1));
				PreferenceLogic.getInstance().saveVipExpireFourIsTip(true);
                }

                return true;
                case SEVENDAY:
                if (!PreferenceLogic.getInstance().getVipExpireSevenIsTip()) {
				AppConfig.LOGD(TAG, "判断Vip过期: 7天");
				DownloadUtils.trackBehaviorEvent(mContext,
                "notice_VIPstatus_show", 0, 0);
				showXiaomiNotification(
                logic.mConfigJSInfo.vip_guide_bar_before_expire_title,
                logic.mConfigJSInfo.vip_guide_bar_before_expire_content,
                "",
                AccountLogic.getInstance().getToVipExpirePendingIntent(
                mContext,
                NotificationReveiver.NOTICE_VIPSTARTUS_SHOW0));
				PreferenceLogic.getInstance().saveVipExpireSevenIsTip(true);
                }

                return true;
                case MOREDAY:
                AppConfig.LOGD(TAG, "判断Vip过期: > 7天");
                PreferenceLogic.getInstance().saveVipExpireIsTip(false);
                PreferenceLogic.getInstance().saveVipExpireOneIsTip(false);
                PreferenceLogic.getInstance().saveVipExpireFourIsTip(false);
                PreferenceLogic.getInstance().saveVipExpireSevenIsTip(false);
                return false;
            //*/	
		default:
			return false;
		}

	}

	public void showXiaomiNotification(String textTip, String contextTip,
                                       String btnText, PendingIntent pendingIntent) {
		AppConfig.LOGD(TAG, textTip);
		if (null == textTip)
        return;

		if (null == mNotifManager)
        mNotifManager = (NotificationManager) mContext
            .getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification = new Notification();

		RemoteViews remoteView = new RemoteViews(mContext.getPackageName(),
                                                 R.layout.download_notification);

		Bitmap bitmap = getIconByPackageName(mContext);
		if (null != bitmap)
        remoteView.setImageViewBitmap(R.id.notification_iv_icon, bitmap);

		remoteView.setTextViewText(R.id.notification_tv_tip, textTip);
		remoteView.setTextViewText(R.id.notification_tv_content, contextTip);
		remoteView.setTextViewText(R.id.notification_button_forward, btnText);
		remoteView.setViewVisibility(R.id.notification_button_forward,
                                     TextUtils.isEmpty(btnText) ? View.GONE : View.VISIBLE);
		if (null != pendingIntent) {
			remoteView.setOnClickPendingIntent(
                                               R.id.notification_button_forward, pendingIntent);
		}

		notification.icon = com.android.providers.downloads.ui.R.drawable.stat_sys_download_anim5;
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		notification.defaults = Notification.DEFAULT_SOUND;

		notification.extraNotification.setEnableFloat(true);
		notification.extraNotification.setFloatTime(FLOATING_TIME * 1000);

		notification.contentView = remoteView;
		if (null != pendingIntent) {
			notification.contentIntent = pendingIntent;
		}

		mNotifManager.notify(DOWNLOAD_NOTIFICATION_ID, notification);

	}

	/**
	 * 取消通知
	 * 
	 * @return void
	 * @Title: cancelDownLoadNotification
	 * @date 2014年6月23日 下午9:09:44
	 */
	public void cancelDownLoadNotification() {
		AppConfig.LOGD(TAG, "取消通知");
		if (null == mNotifManager)
        mNotifManager = (NotificationManager) mContext
            .getSystemService(Context.NOTIFICATION_SERVICE);
		mNotifManager.cancel(DOWNLOAD_NOTIFICATION_ID);
		collapseStatusBar(mContext);
	}

	/**
	 * 获取程序图标
	 * 
	 * @param context
	 * @return Bitmap
	 * @Title: getIconByPackageName
	 * @date 2014年6月23日 下午9:09:57
	 */
	private Bitmap getIconByPackageName(Context context) {
		final PackageManager pm = context.getPackageManager();
		try {
			ApplicationInfo appinfo = pm.getApplicationInfo(
                                                            context.getPackageName(), 0);
			Drawable icon = AppIconsHelper.getIconDrawable(
                                                           context.getApplicationContext(), appinfo, pm,
                                                           AppIconsHelper.TIME_MIN);
			if (icon == null) {
				icon = appinfo.loadIcon(pm);
			}
			return drawableToBitmap(icon);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Drawable 转 Bitmap
	 * 
	 * @param drawable
	 * @return Bitmap
	 * @Title: drawableToBitmap
	 * @date 2014年6月23日 下午9:10:14
	 */
	private Bitmap drawableToBitmap(Drawable drawable) {
		Bitmap bitmap = Bitmap
            .createBitmap(
                          drawable.getIntrinsicWidth(),
                          drawable.getIntrinsicHeight(),
                          drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                          : Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		// canvas.setBitmap(bitmap);
		drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                           drawable.getIntrinsicHeight());
		drawable.draw(canvas);

		return bitmap;
	}

	/**
	 * 回收下拉通知栏菜单
	 * 
	 * @param context
	 * @return void
	 * @Title: collapseStatusBar
	 * @date 2014年6月23日 下午6:00:11
	 */
	public void collapseStatusBar(Context context) {
		try {
			Object statusBarManager = context.getSystemService("statusbar");
			Method collapse;

			if (Build.VERSION.SDK_INT <= 16) {
				collapse = statusBarManager.getClass().getMethod("collapse");
			} else {
				collapse = statusBarManager.getClass().getMethod(
                                                                 "collapsePanels");
			}
			collapse.invoke(statusBarManager);
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
