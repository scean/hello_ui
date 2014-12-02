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

package com.android.providers.downloads.ui;

import android.util.Log;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.Tab;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.DownloadManager;
//import android.app.ProgressDialog;
import android.content.*;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import com.android.providers.downloads.ui.activity.BaseActivity;
import com.android.providers.downloads.ui.activity.XLSpeedUpActivity;
import com.android.providers.downloads.ui.activity.DownloadSettingActivity;
import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.notification.NotificationLogic;
import com.android.providers.downloads.ui.notification.NotificationReveiver;
import com.android.providers.downloads.ui.notification.PreferenceLogic;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountListener;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AddFlowInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.FlowInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.activity.XLSpeedUpActivity;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.fragment.DownloadListFragment;

import com.android.providers.downloads.ui.R;

import com.xiaomi.account.openauth.AuthorizeActivity;

import com.android.providers.downloads.ui.auth.AuthManager;
import com.android.providers.downloads.ui.auth.Constants;
import com.android.providers.downloads.ui.auth.OnAuthResultListener;

import miui.accounts.ExtraAccountManager;
import miui.app.ActionBar;
import miui.app.Activity;
import miui.content.ExtraIntent;
import miui.os.Build;
import miui.view.ViewPager;
import miui.app.ProgressDialog;

//import miui.widget.GuidePopupView;

/**
 * View showing a list of all downloads the Download Manager knows about.
 */
public class DownloadList extends BaseActivity implements RadioGroup.OnCheckedChangeListener {
    private static final String TAG = "DownloadList";

    private ViewPager mViewPager;
    private final int[] TAB_TEXTS = { R.string.download_in_process, R.string.download_complete };
    private final int[] FILTER_IDS = { R.id.filter_all, R.id.filter_apk, R.id.filter_media, R.id.filter_doc, R.id.filter_package, R.id.filter_other };
    private final int[] FILTER_STRING_IDS = { R.string.all_fileter, R.string.apk_filter, R.string.media_filter, R.string.doc_filter, R.string.package_filter, R.string.other_filter };
    private int mCurrentFilterIndex = 0;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_APK = 1;
    public static final int FILTER_MEDIA = 2;
    public static final int FILTER_DOC = 3;
    public static final int FILTER_PACKAGE = 4;
    public static final int FILTER_OTHER = 5;
    public static final int FILTER_SUM = 6;
    private RadioGroup mGroup;
    public static final String PREF_KEY_FILTER_BAR_VISIBILITY = "filter_bar_visible";
    private boolean mFilterBarVisible;
    private View mGroupContainer;

    private View mImgXlSmall;
    private int mRunningStatus = 0;
    private int mClosingStatus = 0;
    private boolean mRunningMark = false;
    private boolean mClosingMark = false;
    private boolean mLoginMark = false;
    private boolean mTrueAuthMark = false;
    private NotificationLogic mNotificationLogic;

    private int mResuestType = 0;
    private Handler mHandler = new Handler();

    private String mTokenType = "";
    public static final String ACTION_INTENT_DOWNLOADLIST_BROADCAST = "com.process.media.broadcast.downloadlist";
    private LoginSucessReceiver mLoginReceiver;

    private View titleBar;
    private TextView mHeaderTitle;

    private boolean shouldDisabled=false;
	private static boolean mThisActivityIsShowing =false;

    @Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        setFinishOnTouchOutside(true);
        setOnLineEventId(DownloadUtils.DOWNLOADLIST_ONLINE_EVENT);
        setupViewPager();
        setupTabs();
        initConfigJS();
        if (!Build.IS_TABLET){
           registerXiaomiLoginReceiver(); 
        }

        checkFromNotificationData();
        if (Build.IS_TABLET) {
			this.setImmersionMenuEnabled(false);
            setupWindowSize();
        }
        AuthManager.getInstance().addAuthListener(mOnAuthResultListener);
        clearToken();
        
        String appPkgName = getIntent().getStringExtra(DownloadManager.INTENT_EXTRA_APPLICATION_PACKAGENAME);
        //do track
          if (TextUtils.isEmpty(appPkgName)) {
          	String action = getIntent().getAction();
          	if (action != null && action.equals(android.content.Intent.ACTION_MAIN)) {
          		appPkgName = "android_home";
          	}
          }
          DownloadUtils.trackEventStatus(DownloadList.this, "download_manager_show", appPkgName == null ? "" : appPkgName, 0, 0);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig); 
    }

    /**
     * 妫�鏌ョ敤鎴峰鏋滄病鏈夌櫥褰曟�侊紝閭ｄ箞鎶婁笂涓�娆＄敤鎴风櫥褰曠殑token娓呮帀锛屼互鍏嶅綋鍓嶇敤鎴疯幏鍙栧厛鍓嶇敤鎴风殑璐﹀彿淇℃伅
     *
     */
    private void clearToken() {
        Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
        XLUtil.logDebug(TAG, "clearToken --->account = " + account);
        String mi_id  = XLUtil.getStringXiaomiIdPreference(getApplicationContext()); 
        
        // XLUtil.logDebug(TAG, "mid: " + mi_id + " name:"+account.name);
        // 褰撳墠娌″皬绫宠处鍙风櫥褰曟�侊紝鎴栬�呭皬绫宠处鍙蜂笌涓婁竴娆＄櫥褰曡处鍙蜂笉涓�鑷存椂锛屾竻闄oken

        if (account == null /*||!account.name.equals(mi_id)*/) {
            //XLUtil.logDebug(TAG, "in mid ");
            XLUtil.saveStringPackagePreferenc(getApplicationContext(), ""); // 澧欏埛token
            XLUtil.saveStringPackagePreferenc(getApplicationContext(), "xiaomi_id", "");
        }
    }

    private void initConfigJS() {
        // TODO Auto-generated method stub
        ConfigJSInstance.getInstance(getApplication());
        if (mNotificationLogic == null) {
            mNotificationLogic = new NotificationLogic(getApplicationContext());
        }
    }

    private void registerXiaomiLoginReceiver() {
        mLoginReceiver = new LoginSucessReceiver();
        IntentFilter filter = new IntentFilter(ExtraAccountManager.LOGIN_ACCOUNTS_POST_CHANGED_ACTION);
        registerReceiver(mLoginReceiver, filter);
    }

    /**
     * get intent extra data
     *
     * @return
     */
    private String getAction() {
        String action = null;
        Intent intent = getIntent();
        if (null != intent) {
            action = intent.getStringExtra(NotificationReveiver.PAGE_TYPE);
        }
        return action;
    }

    /**
     * if from notification
     *
     * if to login if to auth
     */
    private void checkFromNotificationData() {
        String action = getAction();
        if (null == action) {
            return;
        }

        AppConfig.LOGD(TAG, "NotificationAcitivity.initData.action=" + action);
        if (NotificationReveiver.ACTION_XIAOMI_LOGIN.equals(action)) {
            mLoginMark = true;
            new ParseThemeFileTask().execute("get miui account");
        } else if (NotificationReveiver.ACTION_XIAOMI_AUTH.equals(action)) {
            AuthManager.getInstance().startProcessAuth(this, Constants.AUTH_TYPE_FAKE);
        } else if (NotificationReveiver.ACTION_FORWARD_TO_DOWNLOADLIST.equals(action)) {
            AuthManager.getInstance().startProcessAuth(this, Constants.AUTH_TYPE_FAKE);
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // reset button's text, when locale language changed.
        if (mGroup != null) {
            for (int i = 0; i < FILTER_IDS.length; i++) {
                RadioButton button = (RadioButton) mGroup.findViewById(FILTER_IDS[i]);
                if (button != null) {
                    button.setText(FILTER_STRING_IDS[i]);
                }
            }
        }
    }

    public void showAlertOpenXunleiDialog() {
        boolean isPrivacyTipShown = DownloadUtils.isPrivacyTipShown(this);
        if (isPrivacyTipShown) {
            Builder builder = new AlertDialog.Builder(DownloadList.this);
            builder.setTitle(DownloadList.this.getResources().getString(R.string.download_list_open_xl_title));
            builder.setMessage(DownloadList.this.getResources().getString(R.string.download_list_open_xl_message));
            builder.setPositiveButton(DownloadList.this.getResources().getString(R.string.download_list_open_xl_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setXunleiUsagePermission(true);
                    mImgXlSmall.setVisibility(View.VISIBLE);
                    updateVipIconDisplay();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(DownloadList.this.getResources().getString(R.string.download_list_open_xl_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
        } else {
            Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.privacy_tip_title).setMessage(R.string.privacy_tip_content).setNegativeButton(R.string.privacy_tip_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            }).setPositiveButton(R.string.privacy_tip_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setXunleiUsagePermission(true);
                    mImgXlSmall.setVisibility(View.VISIBLE);
                    updateVipIconDisplay();
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                }
            });
            dialog.show();
            DownloadUtils.setPrivacyTipShown(this);
        }
    }

    public void OnMiuiLoginDeal() {
        // open xunlei mark
        boolean xunlei_usage = getXunleiUsagePermission();
        XLUtil.logDebug(TAG, "OnMiuiLoginDeal --->xunlei_usage = " + xunlei_usage);
        if (xunlei_usage == false) {
            // Toast.makeText(getApplicationContext(),
            // R.string.turn_off_xunlei_tips, Toast.LENGTH_SHORT).show();
            showAlertOpenXunleiDialog();
            return;
        }
        // network isn't ok
        boolean netStatus = DownloadUtils.isNetworkAvailable(getApplicationContext());
        if (netStatus == false) {
            Toast.makeText(getApplicationContext(), R.string.retry_after_network_available, Toast.LENGTH_SHORT).show();
            return;
        }
        XLUtil.logDebug(TAG, "OnMiuiLoginDeal --->netStatus = " + netStatus);
        // if get xl_token fail pop AUTH workflow
        Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
        XLUtil.logDebug(TAG, "OnMiuiLoginDeal --->account = " + account);
        if (account != null) {
            // MIUI API get Xl_token
            String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
            XLUtil.logDebug(TAG, "OnMiuiLoginDeal --->xunlei_token = " + xunlei_token);
            if (xunlei_token.equals("") == false) {
                XLUtil.logDebug(TAG, "OnMiuiLoginDeal + xl_token ===  " + xunlei_token);
                DownloadUtils.trackBehaviorEvent(getApplicationContext(), "speedup_show", 1, 0);
                // Intent intent = new Intent(getApplication(), XLSpeedUpActivity.class);
                // startActivityForResult(intent, 50);
            } else {
                mTokenType = "AUTH_TYPE_FAKE";
                mResuestType = Constants.AUTH_TYPE_FAKE; // Constants.AUTH_TYPE_FAKE;
                AuthManager.getInstance().startProcessAuth(DownloadList.this, mResuestType);
                XLUtil.logDebug(TAG, "OnMiuiLoginDeal ---> xl_token = null");
            }

        } else {
            XLUtil.logDebug(TAG, "OnMiuiLoginDeal login fail ---> xl_token = null");
        }
        updateVipIconDisplay();

    }

    public void updateVipIconDisplay() {
	    boolean xunlei_usage = getXunleiUsagePermission();
        boolean netStatus = DownloadUtils.isNetworkAvailable(getApplicationContext());
        int nVipMark = ConfigJSInstance.getInstance(getApplication()).getUseOpt();
        String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
        Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
        NotificationLogic.VipExpireStatus vipstatus = mNotificationLogic.isOutDateVip();
        XLUtil.logDebug(TAG, "hsh data: netStatus,xunlei_usage,xunlei_token,nVipMark,(vipstatus != NotificationLogic.VipExpireStatus.OUTDATE)" + netStatus + "," + xunlei_usage + ","
                + xunlei_token + "," + nVipMark + "," + vipstatus);
        if (netStatus && xunlei_usage && xunlei_token.equals("") == false && account != null && nVipMark == 3 && (vipstatus != NotificationLogic.VipExpireStatus.OUTDATE)) {
            XLUtil.logDebug(TAG, "hsh R.drawable.fire");
            setSpeedUpIcon(getActionBar());
        } else {
            XLUtil.logDebug(TAG, "hsh R.drawable.no_login");
            setNoSpeedUpIcon(getActionBar());
        }
    }

    // auth listen for get token info
    private OnAuthResultListener mOnAuthResultListener = new OnAuthResultListener() {
        @Override
        public void onAuthResult(final int result, final String token) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    XLUtil.logDebug(TAG, "DownloadList result :  " + result);
                    XLUtil.logDebug(TAG, "DownloadList auth res " + token);
                    if (result == AuthorizeActivity.RESULT_SUCCESS) {

                        XLUtil.logDebug(TAG, "DownloadList auth = AuthorizeActivity.RESULT_SUCCESS" + token);
                        XLUtil.saveStringPackagePreferenc(getApplicationContext(), token);
                        XLUtil.logDebug(TAG, "jinghuang --->send getting token broadcast, mOnAuthResultListener test3");
                        Intent intent = new Intent(ACTION_INTENT_DOWNLOADLIST_BROADCAST);
                        DownloadList.this.sendBroadcast(intent);
                        AccountInfoInstance mAccount = AccountInfoInstance.getInstance(getApplicationContext(), XLUtil.getStringPackagePreference(getApplicationContext()));
                        mAccount.setAccountListener(mlisten);
                        mAccount.requestAccount();
	                    //if(mTokenType.equals("AUTH_TYPE_FAKE") ) {
		                    //DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow", 0, 0);
		                mAccount.RequestAddFlowInfo(XLUtil.getStringPackagePreference(getApplicationContext()), true);
	                   // }
                    }

                }
            }, 0);
        }
    };

    private AccountListener mlisten = new AccountListener() {

        @Override
        public int OnFlowRtn(int ret, FlowInfo flow, String msg) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public int OnAddFlowRtn(int ret, final AddFlowInfo addflow, String msg) {
            AppConfig.LOGD(TAG, "list rtn =" + addflow.ret);
	        if (mTrueAuthMark) {
		        mTrueAuthMark = false;
		        XLUtil.logDebug(TAG, "downloadlist  OnAddFlowRtn mTrueAuthMark, ret ,msg, addflow.ret =" + mTrueAuthMark + "," + ret + "," + msg + "," + addflow.ret);
		        return 0;
	        }

	        XLUtil.logDebug(TAG, "downloadlist  OnAddFlowRtn  ret ,msg, addflow.ret =" + ret + "," + msg + "," + addflow.ret);
	        if (ret == 0 && addflow.ret == 0) {
		       // DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow_succ", 0, 0);

		        if (addflow.need_auth == 1) {
		        }

		        mHandler.post(new Runnable() {
			        @Override
			        public void run() {
				        String size = ConfigJSInstance.getInstance(getApplication()).getSpeedJSInfo().free_flow + "MB";
				        String src = String.format(DownloadList.this.getResources().getString(R.string.add_flow_success_msg), size);
				        showPopWindowsByText(src);
				        setSpeedUpIcon(getActionBar());
			        }
		        });

	        } else if (addflow.ret ==20){
		       mHandler.post(new Runnable() {
                  @Override
                  public void run() {
//	                  showPopWindowsByText(DownloadList.this.getResources().getString(R.string.add_flow_fail_vip_msg));
	                  setSpeedUpIcon(getActionBar());
                  }
              });
             }else if (addflow.ret == 22) {
		        mHandler.post(new Runnable() {
			        @Override
			        public void run() {
//				        showPopWindowsByText(DownloadList.this.getResources().getString(R.string.add_flow_fail_haveget_msg));
				        setSpeedUpIcon(getActionBar());
			        }
		        });

            } else if (addflow.ret != 0) {
                if (addflow.ret == 5 || addflow.ret == 10 || addflow.ret == 500) {
	                mHandler.post(new Runnable() {
		                @Override
		                public void run() {
			                showPopWindowsByText(addflow.msg);

		                }
	                });
                }
            }
            return 0;
        }

        @Override
        public int OnAccountRtn(int ret, AccountInfo account, String msg) {
            // TODO Auto-generated method stub
            return 0;
        }
    };

    private class ParseThemeFileTask extends AsyncTask<String, Integer, String> {
        @Override
        protected void onPreExecute() {

        }

        @Override
        protected String doInBackground(String... params) {
            uploadApplyInformation();
            return null;
        }

    }

    private void uploadApplyInformation() {
        Bundle bundle=new Bundle();
        bundle.putBoolean(ExtraAccountManager.EXTRA_SHOW_SYNC_SETTINGS, false);
        AccountManager accountManager = AccountManager.get(DownloadList.this);
        accountManager.addAccount(ExtraIntent.XIAOMI_ACCOUNT_TYPE, null, null, bundle, DownloadList.this, null, null);
    }

    private void execTokenGetOperation() {
        XLUtil.logDebug(TAG, "execTokenGetOperation ---> jinghuang 1");
        boolean xunlei_usage = getXunleiUsagePermission();
        if (xunlei_usage) {
            XLUtil.logDebug(TAG, "execTokenGetOperation ---> jinghuang 2");
            Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
            if (account != null) {
                XLUtil.logDebug(TAG, "execTokenGetOperation ---> jinghuang 3");
                String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
                if (xunlei_token == null || xunlei_token.equals("")) {
                    XLUtil.logDebug(TAG, "execTokenGetOperation ---> Account login but no token!");
                    askRequestToken();
                }
            }
        }
    }

    private ProgressDialog m_ProgressDialog = null;

    public void OnClickXlIcon() {
		if (Build.IS_TABLET) {
		 return;			            		
       }
        DownloadUtils.trackBehaviorEvent(DownloadList.this, "download_manager_speedup_click", 0, 0);
        // open xunlei mark
        boolean xunlei_usage = getXunleiUsagePermission();
        if (xunlei_usage == false) {
            // Toast.makeText(getApplicationContext(),
            // R.string.turn_off_xunlei_tips, Toast.LENGTH_SHORT).show();
            showAlertOpenXunleiDialog();
            return;
        }
        // network isn't ok
        boolean netStatus = DownloadUtils.isNetworkAvailable(getApplicationContext());
        if (netStatus == false) {
            Toast.makeText(getApplicationContext(), R.string.retry_after_network_available, Toast.LENGTH_SHORT).show();
            return;
        }
        // if get xl_token fail pop AUTH workflow
        Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
        XLUtil.logDebug(TAG, "ExtraAccountManager.getXiaomiAccount =" + account);
        NotificationLogic.VipExpireStatus vipstatus = mNotificationLogic.isOutDateVip();
        if (account != null) {
            // MIUI API get Xl_token
            String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
            if (xunlei_token.equals("") == false) {
                XLUtil.logDebug(TAG, "DOWNLIST SHOW PAY PAGE + xl_token ===  " + xunlei_token);
                DownloadUtils.trackBehaviorEvent(getApplicationContext(), "speedup_show", 0, 0);
                Intent intent = new Intent(getApplication(), XLSpeedUpActivity.class);
                startActivityForResult(intent, 50);
            } else if (vipstatus == NotificationLogic.VipExpireStatus.OUTDATE) {
                XLUtil.logDebug(TAG, "request add flow " + xunlei_token);
                AccountInfoInstance mAccount = AccountInfoInstance.getInstance(getApplicationContext(), XLUtil.getStringPackagePreference(getApplicationContext()));
                mAccount.setAccountListener(mlisten);
               // DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow", 0, 0);
                mAccount.RequestAddFlowInfo(XLUtil.getStringPackagePreference(getApplicationContext()), true);
            } else {
                mTokenType = "AUTH_TYPE_FAKE";
                mResuestType = Constants.AUTH_TYPE_FAKE; // Constants.AUTH_TYPE_FAKE;
                // Constants.AUTH_TYPE_REAL
                showXLDiolag();
                AuthManager.getInstance().startProcessAuth(DownloadList.this, mResuestType);
                XLUtil.logDebug(TAG, "AUTH_TYPE_FAKE ---> xl_token = null");
            }

        } else {
            showXLDiolag();
            mLoginMark = true;
            new ParseThemeFileTask().execute("get miui account");
        }
    }

    private void showXLDiolag() {
        m_ProgressDialog = ProgressDialog.show(DownloadList.this, DownloadList.this.getResources().getString(R.string.download_list_intoAuthAlert),
                DownloadList.this.getResources().getString(R.string.download_list_intoAuth), true, true);
        this.mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (m_ProgressDialog.isShowing()) {
                    m_ProgressDialog.dismiss();
                }
            }
        }, 80000);
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, android.view.Menu frameworkMenu) {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	if (Build.IS_TABLET) {
		return false;
	}
	else
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (Build.IS_TABLET) {
			return true;
    	}
		menu.clear();
		menu.add(0,Menu.FIRST,0,R.string.menu_title_resume_all);
		menu.add(0,Menu.FIRST+1,0,R.string.menu_title_pause_all);
		menu.add(0,Menu.FIRST+2,0,R.string.menu_title_settings);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	     super.onOptionsItemSelected(item);
		 switch(item.getItemId()){
		 	case Menu.FIRST:
				//SshowPopWindowsByText("String.menu_title_resume_all");
				resumeAllTask();
				break;
			case Menu.FIRST+1:
				//showPopWindowsByText("String.menu_title_pause_all");
				pauseAllTask();
				break;
			case Menu.FIRST+2:
				//showPopWindowsByText("String.menu_title_settings");
				if(!shouldDisabled)
                {
                     Intent intent = new Intent(DownloadList.this, DownloadSettingActivity.class);
                     startActivity(intent);
                }
				break;
		 	}
		 return true;
	}

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkFromNotificationData();
    }

    private void setFilterPackageName(String pkgName) {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            ((DownloadListFragment) actionBar.getFragmentAt(0)).setFilterPackageName(pkgName);
        }
    }
	public void resumeAllTask()
	{
		ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            ((DownloadListFragment) actionBar.getFragmentAt(0)).resumeAllTask();
        }
	}
	public void pauseAllTask()
	{
		ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            ((DownloadListFragment) actionBar.getFragmentAt(0)).pauseAllTask();
        }
	}	
    @Override
    protected void onResume() {
        super.onResume();
	    mThisActivityIsShowing =true;
        String headTitle = null;
        if (m_ProgressDialog != null && m_ProgressDialog.isShowing()) {
            m_ProgressDialog.dismiss();
        }
        // AuthManager.getInstance().addAuthListener(mOnAuthResultListener);
        //String appPkgName = getIntent().getStringExtra(DownloadManager.INTENT_EXTRA_APPLICATION_PACKAGENAME);
        //android.app.ActionBar actionBar = getActionBar();
        boolean xunlei_usage = getXunleiUsagePermission();
        boolean isPrivacyTipShown = DownloadUtils.isPrivacyTipShown(this);
        boolean netStatus = DownloadUtils.isNetworkAvailable(getApplicationContext());

		if (Build.IS_TABLET || miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) 
		//if(true)
		{
        }
		else
		{
			int nVipMark = ConfigJSInstance.getInstance(getApplication()).getUseOpt();
	        String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
	        Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
	        NotificationLogic.VipExpireStatus vipstatus = mNotificationLogic.isOutDateVip();

            AppConfig.LOGD("netStatus=" + netStatus + ", xunlei_usage=" + xunlei_usage + ", xunlei_token=" + xunlei_token + ", vipMark=" + nVipMark + ", vipStatus=" + vipstatus);

	        if (netStatus && xunlei_usage && xunlei_token.equals("") == false && account != null && nVipMark == 3 && (vipstatus != NotificationLogic.VipExpireStatus.OUTDATE)) {
	            setSpeedUpIcon(getActionBar());
	        } else {
	            setNoSpeedUpIcon(getActionBar());
	        }
		}
        // set brand visibility
        if (!xunlei_usage) {
            mImgXlSmall.setVisibility(View.GONE);
        } else {
            mImgXlSmall.setVisibility(View.VISIBLE);
        }

        if (!isPrivacyTipShown && xunlei_usage) {
            Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.privacy_tip_title).setMessage(R.string.privacy_tip_content).setNegativeButton(R.string.privacy_tip_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    PreferenceLogic.getInstance(DownloadList.this).setIsHaveUseXunleiDownload(false);
                    setNoSpeedUpIcon(getActionBar());
                    mImgXlSmall.setVisibility(View.GONE);
                }
            }).setPositiveButton(R.string.privacy_tip_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                }
            });
            dialog.show();
            DownloadUtils.setPrivacyTipShown(this);
        }
    }

	@Override
	protected void onPause(){
		super.onPause();
		mThisActivityIsShowing =false;
	}

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // saveFilterBarStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // AuthManager.getInstance().processAuthComplete(data, requestCode,
        // resultCode,Constants.AUTH_TYPE_REAL);
        XLUtil.logDebug(TAG, "onActivityResult ---requestCode, requestCode ,  mTrueAuthMark" + requestCode + "," + resultCode + "," + mTrueAuthMark);
        if (requestCode == 50) {
            if (resultCode == 110) {
//                AccountInfoInstance mAccount = AccountInfoInstance.getInstance(getApplicationContext(), XLUtil.getStringPackagePreference(getApplicationContext()));
                mTrueAuthMark = true;
//                mAccount.setAccountListener(mlisten);
//                XLUtil.logDebug(TAG, "onActivityResult --- requestCode 110  mTrueAuthMark" + mTrueAuthMark);
//                //DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow", 0, 0);
//                mAccount.RequestAddFlowInfo(XLUtil.getStringPackagePreference(getApplicationContext()), true);
            } else if (resultCode == 120) {
                Message msg = mUiHandler.obtainMessage();
                msg.what = 24;
                mUiHandler.sendMessage(msg);
            }
        }
        if (requestCode > 9999) {
            AuthManager.getInstance().processAuthComplete(data, requestCode, resultCode, Constants.WAP_TYPE_V6);
            XLUtil.logDebug(TAG, "onActivityResult --- requestCode > 9999 ");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // saveFilterBarStatus();
        AuthManager.getInstance().removeAuthListener(mOnAuthResultListener);
	    AccountInfoInstance.getInstance(getApplicationContext(),XLUtil.getStringPackagePreference(getApplicationContext())).removeAccountListener(mlisten);
        if (!Build.IS_TABLET){
            unregisterReceiver(mLoginReceiver);
        }
    }

    public static final int ANIMATION_OVER = 21;
    public static final int ANIMATION_START = ANIMATION_OVER + 1;
    private boolean animFlag = false;

    private Handler mUiHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    // animation logic
				/*
				 * String tSpeed = (String) msg.obj; int nRunningcount =
				 * msg.arg1; int speed = msg.arg2; Message newmsg; boolean
				 * xunlei_toggle = getXunleiUsagePermission();
				 * LogUtil.errorLog("xunlei_usage=" + xunlei_toggle +
				 * " nRunningcount = " + nRunningcount + " speed = " + speed);
				 * if (nRunningcount == 0) { newmsg =
				 * mUiHandler.obtainMessage(); newmsg.what = ANIMATION_OVER;
				 * mUiHandler.sendMessage(newmsg); } else if (nRunningcount > 0
				 * && speed > 0 && xunlei_toggle) { newmsg =
				 * mUiHandler.obtainMessage(); newmsg.what = ANIMATION_START;
				 * mUiHandler.sendMessage(newmsg); }
				 */
                    break;
                case 8:

                    break;
                case 9:

                    break;

                case 23:// UI must MIUI account
                    XLUtil.logDebug(TAG, "UI handler OnMiuiLoginDeal --->");
                    OnMiuiLoginDeal();
                    break;
                case 25:// have get vip service

                    break;
                case ANIMATION_OVER:

                    break;
                case ANIMATION_START:

                    break;
            }
        }
    };

    @Override
    public void onActionModeStarted(ActionMode mode) {
        // setFilterBarVisible(false);
        mViewPager.setDraggable(false);
        //澶勪簬缂栬緫妯″紡鏃讹紝灞忚斀鎺夐棯鐢靛浘鏍囧拰璁剧疆鍥炬爣
        shouldDisabled=true;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        mViewPager.setDraggable(true);
        shouldDisabled=false;
    }

    private Bundle getFragmentBundleFromTabPos(int pos) {
        Bundle bundle = new Bundle();
        switch (pos) {
            case 0:
                bundle.putBoolean(DownloadListFragment.ARGUMENT_DOWNLOAD_IN_PROCESS, true);
                return bundle;

            case 1:
            default:
                return Bundle.EMPTY;
        }
    }

    private void setupViewPager() {
        // set up filter bar
        View filter_bar = getLayoutInflater().inflate(R.layout.filter_bar, null);
        mGroupContainer = (View) filter_bar.findViewById(R.id.group_container);
        mCurrentFilterIndex = FILTER_ALL;
        mGroup = (RadioGroup) filter_bar.findViewById(R.id.radio_group);
        mGroup.setOnCheckedChangeListener(this);

        mImgXlSmall = filter_bar.findViewById(R.id.image_xl_area);

        setContentView(filter_bar);

        // now check whether filter bar is visible
        loadFilterBarStatus();
        // setFilterBarVisible(mFilterBarVisible);
    }

    private void setupTabs() {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // add downloading and downloaded tabs
            actionBar.setFragmentViewPagerMode(this, getFragmentManager());

            for (int i = 0; i < TAB_TEXTS.length; i++) {
                String tabText = getString(TAB_TEXTS[i]);
                String tag = String.valueOf(TAB_TEXTS[i]);
                Bundle bundle = getFragmentBundleFromTabPos(i);
                Tab tab = actionBar.newTab();
                tab.setText(tabText);
                actionBar.addFragmentTab(tag, tab, DownloadListFragment.class, bundle, false);
            }
            DownloadListFragment dlist = (DownloadListFragment) actionBar.getFragmentAt(0);
            dlist.setSpeedListen(mUiHandler);

            // add title bar in custom view.
            titleBar = getLayoutInflater().inflate(R.layout.title_bar, null);
            //Button option_button = (Button) titleBar.findViewById(R.id.option_button);
            mHeaderTitle = (TextView) titleBar.findViewById(R.id.header_title_container);
            if (Build.IS_TABLET) {
                // set background color of action bar.
                actionBar.setBackgroundDrawable(getResources().getDrawable(R.color.widget_background_color));
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                // custom view aligns right.
				/*
				 * actionBar.setCustomView(titleBar, new
				 * LayoutParams(Gravity.RIGHT));
				 * option_button.setOnClickListener(new View.OnClickListener() {
				 * 
				 * @Override public void onClick(View v) { mFilterBarVisible =
				 * !mFilterBarVisible; setFilterBarVisible(mFilterBarVisible);
				 * // if filter bar is invisible, set filter index with
				 * FILTER_ALL mGroup.check(FILTER_IDS[FILTER_ALL]); } });
				 */
            } else {// set custom view in action bar.

                View headPanel = titleBar.findViewById(R.id.header_panel);
                actionBar.setTitle(R.string.all_downloads);
				if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) 
				//if (true)
				{
       				actionBar.setDisplayHomeAsUpEnabled(false);
					actionBar.setDisplayShowHomeEnabled(false);
					actionBar.setHomeButtonEnabled(false);
				}
				else
				{
					actionBar.setDisplayHomeAsUpEnabled(false);
					actionBar.setDisplayShowHomeEnabled(true);
					actionBar.setHomeButtonEnabled(true);
					setNoSpeedUpIcon(actionBar);
				}
				actionBar.setDisplayShowCustomEnabled(true);
                actionBar.setCustomView(titleBar, new LayoutParams(Gravity.RIGHT));

                // Show tab labels
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
				 
				/*ActionBar.LayoutParams params = new ActionBar.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);
				actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
				actionBar.setDisplayShowCustomEnabled(true);
				actionBar.setCustomView(titleBar, params);*/
                // keep screen portrait
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                setFilterBarVisible(false);
                /*
                option_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(!shouldDisabled)
                        {
                            Intent intent = new Intent(DownloadList.this, DownloadSettingActivity.class);
                            startActivity(intent);
                        }
                    }
                });
                */
            }
        }
        mGroup.check(FILTER_IDS[0]);
        mViewPager = (ViewPager) findViewById(miui.R.id.view_pager);
        // Can drag horizontally
        mViewPager.setDraggable(true);
    }

    // set height of activity
    private void setupWindowSize() {
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.height = getResources().getDimensionPixelSize(R.dimen.window_side_length);
        p.width = getResources().getDimensionPixelSize(R.dimen.window_side_length);
        getWindow().setAttributes(p);
    }

    private boolean getXunleiUsagePermission() {
        return PreferenceLogic.getInstance(this).getIsHaveUseXunleiDownload();
    }

    private void setXunleiUsagePermission(boolean value) {
        PreferenceLogic.getInstance(this).setIsHaveUseXunleiDownload(value);

        if (value) {
        	DownloadUtils.trackBehaviorEvent(getApplicationContext(), "xunlei_open", 0, 0);
        } else {
        	DownloadUtils.trackBehaviorEvent(getApplicationContext(), "xunlei_close", 0, 0);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        RadioButton rb;
        for (int i = 0; i < FILTER_SUM; i++) {
            if (FILTER_IDS[i] == checkedId) {
                mCurrentFilterIndex = i;
                continue;
            }
            rb = (RadioButton) group.getChildAt(i);
            rb.setTextColor(getResources().getColor(R.color.filter_bar_unchecked_button_text_color));
        }
        rb = (RadioButton) group.getChildAt(mCurrentFilterIndex);
        rb.setTextColor(getResources().getColor(R.color.filter_bar_checked_button_text_color));
        ActionBar actionBar = this.getActionBar();
        if (actionBar != null) {
            ((DownloadListFragment) actionBar.getFragmentAt(0)).setFilterIndex(mCurrentFilterIndex);
            ((DownloadListFragment) actionBar.getFragmentAt(1)).setFilterIndex(mCurrentFilterIndex);
        }
    }

    public void setFilterBarVisible(boolean visible) {
        if (visible) {
            mGroupContainer.setVisibility(View.VISIBLE);
        } else {
            mGroupContainer.setVisibility(View.GONE);
        }
    }

    public void saveFilterBarStatus() {
        SharedPreferences preferences = this.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_KEY_FILTER_BAR_VISIBILITY, mFilterBarVisible);
        editor.commit();
    }

    public void loadFilterBarStatus() {
		/*
		 * SharedPreferences preferences = this.getSharedPreferences(PREF_NAME,
		 * Context.MODE_PRIVATE); if
		 * (preferences.contains(PREF_KEY_FILTER_BAR_VISIBILITY)) {
		 * mFilterBarVisible =
		 * preferences.getBoolean(PREF_KEY_FILTER_BAR_VISIBILITY, true); } else
		 * { mFilterBarVisible = false; }
		 */
        mFilterBarVisible = true;
    }

    class LoginSucessReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ExtraAccountManager.LOGIN_ACCOUNTS_POST_CHANGED_ACTION)) {
                // if get xl_token fail pop AUTH workflow
                // login in
                Account account = ExtraAccountManager.getXiaomiAccount(DownloadList.this);
                if (account != null) {
                    XLUtil.logDebug(TAG, "broadcast OnMiuiLoginDeal  mLoginMark--->" + mLoginMark);
                    if (mLoginMark) {
                        mLoginMark = false;
                        XLUtil.logDebug(TAG, "jinghuang --->send getting token broadcast, LoginSucessReceiver test2");
                        Intent bintent = new Intent(ACTION_INTENT_DOWNLOADLIST_BROADCAST);
                        DownloadList.this.sendBroadcast(bintent);
                        Message mess = mHandler.obtainMessage();
                        mUiHandler.sendEmptyMessageDelayed(23, 1000);
                    }
                    XLUtil.logDebug(TAG, "miui login   :  sendmsg 23");
                } else {
                    // login out
                    XLUtil.saveStringPackagePreferenc(getApplicationContext(), "");
                    XLUtil.saveStringPackagePreferenc(getApplicationContext(), "xiaomi_id", "");
                    XLUtil.logDebug(TAG, "miui logout   :  clear token");
                }
            }
        }
    }

    private boolean isToast = false;

    private void showPopWindowsByText(String msg) {
	    if(mThisActivityIsShowing ==false){
		    return;
	    }
        if (!isToast) {
            isToast = true;
            long time = System.currentTimeMillis();
            AppConfig.LOGD(TAG, "time = " + time);
            Toast.makeText(DownloadList.this, msg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (m_ProgressDialog != null && m_ProgressDialog.isShowing()) {
            m_ProgressDialog.dismiss();
        }
        mRunningMark = false;
    }

    private void askRequestToken() {
        XLUtil.logDebug(TAG, "jinghuang --->send getting token broadcast, askRequestToken test1");
        Intent intent = new Intent(ACTION_INTENT_DOWNLOADLIST_BROADCAST);
        this.sendBroadcast(intent);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == android.R.id.home && !shouldDisabled) {
			if (!Build.IS_TABLET) {
            	OnClickXlIcon();
            	return true;	
        	}
        }
        return super.onOptionsItemSelected(item);
    }

    private void setSpeedUpIcon(ActionBar actionBar) {
        if (null != actionBar) {
            actionBar.setIcon(R.drawable.login_thunder);
        }
    }

    private void setNoSpeedUpIcon(ActionBar actionBar) {
        if (null != actionBar) {
            actionBar.setIcon(R.drawable.no_login);
        }
    }
}
