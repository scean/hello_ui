package com.android.providers.downloads.ui.notification;

import java.util.List;

import android.R.integer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.providers.downloads.ui.DownloadList;
import com.android.providers.downloads.ui.DownloadUtils;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.ConfigJSInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.SpeedupJSInfo;
import com.android.providers.downloads.ui.pay.XLSpeedUpActivity;
import com.android.providers.downloads.ui.pay.util.XLUtil;

/**
 * 消息中转类
 * 
 * @Package com.android.providers.downloads.ui.notification
 * @ClassName: NotificationReveiver
 * @author gaogb
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月23日 下午9:11:49
 */
public class NotificationReveiver extends BroadcastReceiver {
	public static final String PAGE_TYPE = "pageType";
	public static final String ACTION_XIAOMI_LOGIN = "com.downloads.notification.action.xiaomi.login";
	public static final String ACTION_XIAOMI_AUTH = "com.downloads.notification.action.xiaomi.auth";
	public static final String ACTION_DOWNLOAD_NOTIFICATION_INIT = "com.downloads.notification.action.init";
	public static final String ACTION_DOWNLOAD_VIP_EXPIRE_INIT = "com.downloads.notification.action.vip_expire";
	public static final String ACTION_FORWARD_TO_DOWNLOADLIST = "com.downloads.notification.action.goto.downloadlist";
	public static final String ACTION_FORWARD_AUTO_OBTAIN = "com.downloads.notification.action.auto.obtain";
	public static final String ACTION_INTENT_XL_DOWNLOAD_START = "com.process.media.broadcast.xltask.startdownload";

    /***加速唤醒上报**/
    public static final int NOTICE_WAKEUP_SHOW = 1;
    /***会员到期前第一个阶段通知**/
    public static final int NOTICE_VIPSTARTUS_SHOW0 = 2;
    /***会员到期第二个阶段通知**/
    public static final int NOTICE_VIPSTARTUS_SHOW1 = 3;
    /***会员到期第三个阶段通知**/
    public static final int NOTICE_VIPSTARTUS_SHOW2 = 4;
    /***会员到期通知**/
    public static final int NOTICE_VIPSTARTUS_SHOW3 = 5;
    /***赠送流量使用前80%**/
    public static final int NOTICE_FLOWSTARTUS_SHOW0 = 6;
    /***赠送流量用完**/
    public static final int NOTICE_FLOWSTARTUS_SHOW1 = 7;
    /***领取流量通知条展示,未登录小米账号**/
    public static final int NOTICE_FLOW_SHOW0 = 8;
    /***领取流量通知条展示,登录小米账号未绑定**/
    public static final int NOTICE_FLOW_SHOW1 = 9;
    /***领取流量通知条展示,登录小米账号绑定后台帐号**/
    public static final int NOTICE_FLOW_SHOW2 = 10;
    /***领取流量通知条展示,登录小米账号绑定迅雷非会员账号**/
    public static final int NOTICE_FLOW_SHOW3 = 11;
    /***领取流量通知条展示,登录小米账号绑定迅雷会员账号**/
    public static final int NOTICE_FLOW_SHOW4 = 12;
    
    
    @Override
    public void onReceive(Context context, Intent intent) {
        /*
         * 初始化配置信息，检查配置信息是否已经获取，如果未获取，先获取配置信息， 其他处理逻辑先搁浅
         */
        ConfigJSInstance configInstance = ConfigJSInstance.getInstance(context.getApplicationContext());
        ConfigJSInfo configJsInfo = configInstance.getConfigJSInfo();
        SpeedupJSInfo speedupJsInfo = configInstance.getSpeedJSInfo();
        LogUtil.debugLog("NotificationReveiver 初始化配置信息 configJsInfo is null " + (configJsInfo == null) + ",speedJsInfo is null " + (speedupJsInfo == null));
        if (null == configJsInfo || null == speedupJsInfo) {
            return;
        }
        String token = PreferenceLogic.init(context).getToken();
        if (null != token) {
            AccountInfoInstance.getInstance(context.getApplicationContext(), token);
        }
        String action = intent.getAction();
        LogUtil.debugLog("NotificationReveiver.action=" + action);
        if (ACTION_DOWNLOAD_NOTIFICATION_INIT.equals(action)) {
            PreferenceLogic.init(context).saveToday();
            NotificationHelper.init(context.getApplicationContext());
        } else if (ACTION_XIAOMI_LOGIN.equals(action)) {
        	report(context,intent);
            intent = new Intent(context, DownloadList.class);
            intent.putExtra(PAGE_TYPE, ACTION_XIAOMI_LOGIN);
//          intent = new Intent(context, XLSpeedUpActivity.class);
//          intent.putExtra(XLSpeedUpActivity.INTENT_FLAG, XLSpeedUpActivity.INTENT_FLAG_LET_LOGIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            cancelNotification();
        } else if (ACTION_XIAOMI_AUTH.equals(action)) {
            intent = new Intent(context, DownloadList.class);
            intent.putExtra(PAGE_TYPE, ACTION_XIAOMI_AUTH);
//          intent = new Intent(context, XLSpeedUpActivity.class);
//          intent.putExtra(XLSpeedUpActivity.INTENT_FLAG, XLSpeedUpActivity.INTENT_FLAG_LET_AUTH);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            cancelNotification();
        } else if (ACTION_DOWNLOAD_VIP_EXPIRE_INIT.equals(action)) {
        	report(context ,intent);
            intent = new Intent(context, XLSpeedUpActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("cmd", 1);
            context.startActivity(intent);
            cancelNotification();
        } else if (ACTION_FORWARD_TO_DOWNLOADLIST.equals(action)) {
        	report(context ,intent);
            intent = new Intent(context, DownloadList.class);
            intent.putExtra(PAGE_TYPE, ACTION_FORWARD_TO_DOWNLOADLIST);
//          intent = new Intent(context, XLSpeedUpActivity.class);
//          intent.putExtra(XLSpeedUpActivity.INTENT_FLAG, XLSpeedUpActivity.INTENT_FLAG_LET_AUTH);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            cancelNotification();
        } else if (ACTION_FORWARD_AUTO_OBTAIN.equals(action)) {
            intent = new Intent(context, XLSpeedUpActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("cmd", 1);
            context.startActivity(intent);
            cancelNotification();
        }
    }
    
    /***
     * 上报
     * @param context
     * @param intent
     */
    private void report(Context context, Intent intent)
    {
     int flag = intent.getIntExtra("flag", -1);
//      上报
      switch (flag) {
		//case NOTICE_WAKEUP_SHOW:
			//DownloadUtils.trackBehaviorEvent(context, "notice_wakeup_click", 0, 0);
		case NOTICE_VIPSTARTUS_SHOW0:
			DownloadUtils.trackBehaviorEvent(context, "notice_VIPstatus_click", 0, 0);
			break;
		case NOTICE_VIPSTARTUS_SHOW1:
			DownloadUtils.trackBehaviorEvent(context, "notice_VIPstatus_click", 1, 0);
			break;
		case NOTICE_VIPSTARTUS_SHOW2:
			DownloadUtils.trackBehaviorEvent(context, "notice_VIPstatus_click", 2, 0);
			break;
		case NOTICE_VIPSTARTUS_SHOW3:
			DownloadUtils.trackBehaviorEvent(context, "notice_VIPstatus_click", 3, 0);
			break;
		case NOTICE_FLOWSTARTUS_SHOW0:
			DownloadUtils.trackBehaviorEvent(context, "notice_flowstatus_click", 0, 0);
			break;
		case NOTICE_FLOWSTARTUS_SHOW1:
			DownloadUtils.trackBehaviorEvent(context, "notice_flowstatus_click", 1, 0);
			break;
		case NOTICE_FLOW_SHOW0:
			AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY);
			DownloadUtils.trackBehaviorEvent(context, "notice_flow_click", 0, 0);
			break;
		case NOTICE_FLOW_SHOW1:
			AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY);
			DownloadUtils.trackBehaviorEvent(context, "notice_flow_click", 0, 1);
			break;
		case NOTICE_FLOW_SHOW2:
			AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY);
			DownloadUtils.trackBehaviorEvent(context, "notice_flow_click", 0, 2);
			break;
		case NOTICE_FLOW_SHOW3:
			AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY);
			DownloadUtils.trackBehaviorEvent(context, "notice_flow_click", 0, 3);
			break;
		case NOTICE_FLOW_SHOW4:
			AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY);
			DownloadUtils.trackBehaviorEvent(context, "notice_flow_click", 0, 4);
			break;
		default:
			break;
		}
	}

	/**
	 * 点击之后关闭提示
	 */
	private void cancelNotification() {
		try {
			NotificationHelper.getInstance().cancelDownLoadNotification();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 初始化通知栏
	 * 
	 * @param context
	 */
	public static void initNotificationService(Context context) {
		Intent intent = new Intent(ACTION_DOWNLOAD_NOTIFICATION_INIT);
		context.sendBroadcast(intent);
	}
}
