package com.android.providers.downloads.ui.activity;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
//import android.app.ProgressDialog;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout.LayoutParams;
import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.activity.BaseActivity;
import com.android.providers.downloads.ui.DownloadList;
import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.utils.MyVolley;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.R;
import com.android.providers.downloads.ui.pay.ConfigJSInstance;
import com.android.providers.downloads.ui.pay.AccountInfoInstance;
import com.android.providers.downloads.ui.pay.MiBiPay;
import com.android.providers.downloads.ui.notification.NotificationLogic;
import com.android.providers.downloads.ui.notification.PreferenceLogic;
import com.android.providers.downloads.ui.notification.NotificationLogic.GiveFlowUsedState;
import com.android.providers.downloads.ui.notification.NotificationLogic.VipExpireStatus;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AccountListener;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.AddFlowInfo;
import com.android.providers.downloads.ui.pay.AccountInfoInstance.FlowInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.ConfigJSInfo;
import com.android.providers.downloads.ui.pay.MiBiPay.PayListener;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.xiaomi.account.openauth.AuthorizeActivity;
import com.android.providers.downloads.ui.auth.AuthManager;
import com.android.providers.downloads.ui.auth.UnbindManager;
import com.android.providers.downloads.ui.auth.Constants;
import com.android.providers.downloads.ui.auth.OnAuthResultListener;
import com.android.providers.downloads.ui.auth.OnUnbindResultListener;
import miui.accounts.ExtraAccountManager;
import miui.app.Activity;
import miui.widget.SlidingButton;
import miui.app.ProgressDialog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;

//import miui.widget.SlidingButton.OnCheckedChangedListener;
public class XLSpeedUpActivity extends BaseActivity {
	private static final String TAG = XLSpeedUpActivity.class.getSimpleName();

	public static final String ACTION_INTENT_DOWNLOADLIST_BROADCAST = "com.process.media.broadcast.downloadlist";
	public static final String ACTION_INTENT_XLSPEEDUPACTIVITY_BROADCAST = "com.android.providers.downloads.ui.pay.xlspeedupactivity_broadcast";

	private ScheduledExecutorService mPool = Executors.newSingleThreadScheduledExecutor();

	TextView mvipclick;
	TextView mVipname;
	TextView mVipInfo;
	TextView mQuickMsg;
	TextView mQuickDetail;
	TextView mPayMsg;
	Button mbtnpay;
	MiBiPay mpay;
	ImageView mVipImg;
	ImageView mVipThumbImg;
	TextView mAutoSpeedTip;
	TextView mVipOrExpTip;
	ProgressBar mProcess;
	ViewGroup mHeadLayout;
	String mToken;
	SlidingButton mSlideChoose;
	AccountInfoInstance mAccountInstance;
	ConfigJSInstance mConfigJSInstance;
	NotificationLogic mNotificationLogic;
	final long DelayFromCreateToResume = 100;
	final long DelayGetAccountInfo = 3 * 1000;
	final long DelayGetNextAccountInfo = 5 * 1000;
	final long DelayGetOrderInfo = 4 * 1000;
	static int mAuthFlag;
	static int mReInitViewFlag;
	static int mSetVipClickGoneFlag;
	static int mReRequestOrderFlag;
	int mRtnResult = 0;
	AccountInfo mDefaultAccountInfo;
	FlowInfo mDefaultFlowInfo;
	private boolean mGetAccountWhenAuthFinished = false;

	OnUnbindResultListener mUnbindListener;

	Handler mHandler = new Handler();

	private static final class StatInfo {
		int data1;
		int data2;
		String key;

		StatInfo() {
			data1 = data2 = -1;
		}
	}

	private StatInfo mStatInfo;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.speedup_activity);
		setOnLineEventId(DownloadUtils.SPEEDUP_ONLINE_EVENT);
		// initBroadcast();
//        askRequestToken();
//	    XLUtil.logDebug(TAG,"density ="+this.getResources().getDisplayMetrics().density);
		XLUtil.askRequestTokenFake(getApplicationContext());
		initToken();
		initConfigJS();
		initNotifyLogic();
		initAccountInfo();
		initView();
		initPay();
		registerXiaomiLoginReceiver();
		initUnbindListener();
		AuthManager.getInstance().addAuthListener(mOnAuthResultListener);
		// 领取流量，授权流程可能有问题，在页面启动后就去领取。
		mPool.schedule(new Runnable() {
			@Override
			public void run() {
				if (mAccountInstance != null) {
					XLUtil.logDebug(TAG, "request add flow1:" + mToken);
					// DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow", 0, 0);
					mAccountInstance.RequestAddFlowInfo(XLUtil.getStringPackagePreference(getApplicationContext()), false);
				}
			}
		}, DelayFromCreateToResume, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent i = getIntent();
		int cmd = i.getIntExtra("cmd", -1);
		if (cmd == -1)
			return;
		DownloadUtils.trackBehaviorEvent(getApplicationContext(), "speedup_show", 1, 0);
	}

	private void initUnbindListener() {
		if (mUnbindListener != null) {
			return;
		}
		mUnbindListener = new OnUnbindResultListener() {
			@Override
			public void onSuccess(int flag, int arg0) {
				XLUtil.logDebug(TAG, "unbind flag =" + flag + " arg=" + arg0);
				if (arg0 == 0) {
					setToken("");
					saveXLId(false);
//					askRequestToken();
					String msg = XLSpeedUpActivity.this.getResources().getString(R.string.unbind_xunlei_success);
					if (flag == 1) {
						msg = XLSpeedUpActivity.this.getResources().getString(R.string.unbind_xunlei_2_success);
					}

					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
					finish();
					XLUtil.logDebug("XLUnbind", "Success");
				} else {

				}
			}


		};
		XLUtil.logDebug("XLUnbind", "addListener");
		UnbindManager.getInstance().addUnbindListener(mUnbindListener);
	}

	private LoginChangedReceiver mLoginReceiver;

	class LoginChangedReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(ExtraAccountManager.LOGIN_ACCOUNTS_POST_CHANGED_ACTION)) {
				XLSpeedUpActivity.this.finish();
			}
		}
	}

	private void registerXiaomiLoginReceiver() {
		mLoginReceiver = new LoginChangedReceiver();
		IntentFilter filter = new IntentFilter(ExtraAccountManager.LOGIN_ACCOUNTS_POST_CHANGED_ACTION);
		registerReceiver(mLoginReceiver, filter);
	}

	private void initToken() {
		String xunlei_token = XLUtil.getStringPackagePreference(getApplicationContext());
		XLUtil.logDebug(TAG, "initToken=" + xunlei_token + "\nmTken=" + mToken);
		if (mAccountInstance != null) {
			if (!xunlei_token.equals(mToken)) {
				mAccountInstance.setToken(xunlei_token);
//                mAccountInstance.requestAccount();
//                mAccountInstance.requestFlow();
			}
		}
		mToken = xunlei_token;
		XLUtil.logDebug(TAG, "initToken() token=" + mToken);
	}

	private void askRequestToken() {
		Intent intent = new Intent(ACTION_INTENT_DOWNLOADLIST_BROADCAST);
		this.sendBroadcast(intent);
	}

	private void setToken(String token) {
		mToken = token;
		if (mAccountInstance != null) {
			mAccountInstance.setToken(token);
		}
		XLUtil.saveStringPackagePreferenc(getApplicationContext(), mToken);
	}

	private void saveXLId(boolean islogin) {
		AccountInfoInstance.AccountInfo account = AccountInfoInstance.getInstance(getApplicationContext(), XLUtil.getStringPackagePreference(getApplicationContext())).getAccountInfo();
		String xunleiID = "";
		if (islogin && account.result == 200) {
			xunleiID = account.uid;
		}

		XLUtil.saveStringPackagePreferenc(getApplicationContext(), AppConfig.PREF_KEY_XUNLEI_USER_ID, xunleiID);
	}

	private void initConfigJS() {
		// TODO Auto-generated method stub
		mConfigJSInstance = ConfigJSInstance.getInstance(getApplicationContext());
	}

	private void initNotifyLogic() {
		mNotificationLogic = new NotificationLogic(getApplicationContext());
	}

	private void initPay() {
		mpay = new MiBiPay();
		mpay.initWithContext(getApplication(), this);
		mpay.setPayListener(mpaylisten);
		if (!XLUtil.isNullOrEmpty(mToken)) {
			mpay.RequestPrize(0, 3, mToken);
		}
	}

	private void initAccountInfo() {
		String methodName = TAG + "_" + "initAccountInfo";
		if (mAccountInstance == null) {
			mAccountInstance = AccountInfoInstance.getInstance(getApplicationContext(), mToken);
			mAccountInstance.setAccountListener(mAccountListen);
			mAccountInstance.requestAccount();
			XLUtil.logDebug(TAG, "XLSpeedUpActivity initAccountInfo request flow");
			mAccountInstance.requestFlow(methodName);
		}
	}

	private void initBroadcast() {

		SpeedUpBroadcaset smsReceiver = new SpeedUpBroadcaset();

		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_INTENT_XLSPEEDUPACTIVITY_BROADCAST);
		this.registerReceiver(smsReceiver, filter);
	}

	private AccountListener mAccountListen = new AccountListener() {
		@Override
		public int OnFlowRtn(int ret, final FlowInfo flow, String msg) {
			// TODO Auto-generated method stub
			final int ret_tmp = ret;
			final int flow_tmp_ret = flow.ret;
			final String msg_tmp = msg;

			XLUtil.logDebug(TAG, "flow rtn" + ret_tmp + " " + flow_tmp_ret + "  " + msg_tmp);
			if (ret == 0) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub

						isFlowInfoChangedAndUpdateView(flow);
					}
				});
			}
			return 0;
		}

		@Override
		public int OnAddFlowRtn(int ret, AddFlowInfo addflow, String msg) {
			AppConfig.LOGD(TAG, "speed rtn =" + addflow.ret);
			// TODO Auto-generated method stub
			final int ret_tmp = ret;
			final int flow_tmp_ret = addflow.ret;
			final String msg_tmp = msg;
			XLUtil.logDebug(TAG, "add flow rtn " + ret_tmp + "  " + flow_tmp_ret + "  " + msg_tmp);
			if (ret == 0 && addflow.ret == 0) {
				//  DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow_succ", 0, 0);

				if (addflow.need_auth == 1) {
				}
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						String head = XLSpeedUpActivity.this.getResources().getString(R.string.download_list_get_hightspeed_flow_sucess_head);
						String tail = XLSpeedUpActivity.this.getResources().getString(R.string.download_list_get_hightspeed_flow_sucess_tail);
						String size = ConfigJSInstance.getInstance(getApplication()).getSpeedJSInfo().free_flow + "MB";
						//   showPopWindowsByText(head + size + tail);
					}
				});
			} else if (addflow.ret == 22) {

			} else if (addflow.ret != 0) {
				if (addflow.ret == 5 || addflow.ret == 10 || addflow.ret == 500) {
					final String toastMsg = addflow.msg;
					mHandler.post(new Runnable() {
						@Override
						public void run() {
							//   showPopWindowsByText(toastMsg);
						}
					});
				}
			}
			return 0;
		}

		@Override
		public int OnAccountRtn(int ret, final AccountInfo account, String msg) {
			// TODO Auto-generated method stub
			final String methodName = TAG + "_" + "OnAccountRtn";
			final int ret_tmp = ret;
			final int flow_tmp_ret = account.result;
			final String msg_tmp = msg;
			XLUtil.logDebug(TAG, "Account rtn " + ret_tmp + " " + flow_tmp_ret + "  " + msg_tmp);

			mHandler.post(new Runnable() {

				@Override
				public void run() {
					// TODO Auto-generated method stub
					if (account.result == 200) {
						stopXLProgressDialog();
					}
					isAccountInfoChangedAndChangeView(account);
				}
			});

			if (mReInitViewFlag > 0) {
				XLUtil.logDebug(TAG, "OnAccountRtn mReinitViewFlag=" + mReInitViewFlag);
//                askRequestToken();
				XLUtil.askRequestTokenFake(getApplicationContext());
				initToken();
				mPool.schedule(new Runnable() {
					@Override
					public void run() {
						mAccountInstance.requestAccount();
						XLUtil.logDebug(TAG, "XLSpeedUpActivity onAccountRtn request flow mReInitViewFlag=" + mReInitViewFlag);
						mAccountInstance.requestFlow(methodName);
					}
				}, DelayGetNextAccountInfo, TimeUnit.MILLISECONDS);
				mReInitViewFlag--;
			}

			// bind_account_succ stat
			if (mGetAccountWhenAuthFinished) {
				mGetAccountWhenAuthFinished = false;

				if (account.result == 200) {
					int flag = -1;
					if (account.isAuto()) {
						if (!account.isFake()) {
							flag = 0;
						}
					} else {
						flag = account.isVip() ? 2 : 1;
					}

					DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_succ", flag, 0);
					Log.d(TAG, "get user info finished! isvip = " + (account.isvip == 1));
				}
			}

			// query_userinfo_finished stat, only stat when last get info
			if (mReInitViewFlag == 0) {
				int flag = -1;
				if (account.result == 200) {

					if (account.isAuto()) {
						flag = account.isFake() ? 1 : 2;
					} else {
						flag = account.isVip() ? 4 : 3;
					}
				} else {
					flag = 0;
				}
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "query_userinfo_finished", flag, 0);
			}

			return 0;
		}
	};

	private boolean isToast = false;


	public class SpeedUpBroadcaset extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			ParcelFileDescriptor filed = (ParcelFileDescriptor) intent.getParcelableExtra("Avatarfd");
			MyVolley.getInstance(getApplicationContext()).setParcelFile(filed);
		}
	}

	long mlastClick;

	private void initView() {

		this.setTitle(this.getResources().getString(R.string.xunlei_speed_service));
		AccountInfo account = mAccountInstance.getAccountInfo();
		FlowInfo flow = mAccountInstance.getFlowInfo();
		mDefaultAccountInfo = account;
		mDefaultFlowInfo = flow;
		mAutoSpeedTip = (TextView) findViewById(R.id.text_vip_auto_speedup);
		mVipOrExpTip = (TextView) findViewById(R.id.text_gaosutongdao);
		mHeadLayout = (ViewGroup) findViewById(R.id.relativeLayout_main);
		mVipThumbImg = (ImageView) findViewById(R.id.img_vipthumb);
		if (account != null) {
			changeVipThumbImg(account);
		}
		mVipname = (TextView) findViewById(R.id.text_vipname);
		changeVipname(account);
		mVipInfo = (TextView) findViewById(R.id.text_vipinfo);
		changeViewExpire(account);
		mVipImg = (ImageView) findViewById(R.id.image_vipinfo);
		changeVipImage(account);
		mQuickMsg = (TextView) findViewById(R.id.text_quick_msg);
		changeQuickMsg(account);
		mQuickDetail = (TextView) findViewById(R.id.text_quick_detail);
		mProcess = (ProgressBar) findViewById(R.id.progressBar1);
		mProcess.setVisibility(View.VISIBLE);

		changeViewflow(flow);
		TextView textNetShow = (TextView) findViewById(R.id.text_quick1);
		textNetShow.setText(XLSpeedUpActivity.this.getResources().getString(R.string.open_xunlei_speed_service));

		mPayMsg = (TextView) findViewById(R.id.text_paymsg);
		mvipclick = (TextView) findViewById(R.id.text_wantvip);
		mvipclick.setText(XLSpeedUpActivity.this.getResources().getString(R.string.bind_xunlei_account));
		changeVipClick(account);
		OnClickListener unBind = new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (XLUtil.isNullOrEmpty(mToken)) {
					return;
				}
				if (mvipclick.getVisibility() != View.VISIBLE) {
					boolean netStatus = DownloadUtils.isNetworkAvailable(getApplicationContext());
					if (netStatus == false) {
						Toast.makeText(getApplicationContext(), R.string.retry_after_network_available, Toast.LENGTH_SHORT).show();
						return;
					}
					UnbindManager.getInstance().Unbind(XLSpeedUpActivity.this, mToken, "miui_v6");
				}
			}
		};

		mVipname.setOnClickListener(unBind);
		mVipThumbImg.setOnClickListener(unBind);
		mvipclick.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if (System.currentTimeMillis() - mlastClick <= 1000) {
					return;
				}
				mlastClick = XLUtil.getCurrentUnixMillTime();
				mAuthFlag = 0;

				AuthManager.getInstance().startProcessAuth(XLSpeedUpActivity.this, Constants.AUTH_TYPE_REAL, 1);

			}
		});

		mbtnpay = (Button) findViewById(R.id.btn_pay);
		mbtnpay.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				XLUtil.logDebug(TAG, "btnpay token=" + mToken);
				if (System.currentTimeMillis() - mlastClick <= 1000) {
					return;
				}
				mlastClick = XLUtil.getCurrentUnixMillTime();
//                askRequestToken();
				XLUtil.askRequestTokenFake(getApplicationContext());
				initToken();
				AccountInfo info = mAccountInstance.getAccountInfo();
				PaybtnClickReport(info);
				XLUtil.logDebug(TAG, "btnpay accinfo token:" + mToken + "result" + info.result + "fake:" + info.fake);
				if (XLUtil.isNullOrEmpty(mToken)) {
					// AuthManager.getInstance().addAuthListener(mOnAuthResultListener);

					mAuthFlag = 2;
					XLUtil.logDebug(TAG, "btnpay nulltoken start");
					//   DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_number", 0, 0);
					AuthManager.getInstance().startProcessAuth(XLSpeedUpActivity.this, Constants.AUTH_TYPE_REAL);
				} else if (info != null && (info.result == 200) && (info.fake == 1)) {

					mAuthFlag = 2;
					//DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_number", 1, 0);
					AuthManager.getInstance().startProcessAuth(XLSpeedUpActivity.this, Constants.AUTH_TYPE_REAL);
				} else if (info != null && info.result == 200 && info.fake == 0) {
					openChoosePrizeDialog();
				} else if (info != null && info.result == 400) {
					XLUtil.logDebug(TAG, "btnpay no get fake ");

					askRequestToken();


				} else {
					mAuthFlag = 2;
					// DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_number", 1, 0);
					AuthManager.getInstance().startProcessAuth(XLSpeedUpActivity.this, Constants.AUTH_TYPE_REAL);
				}

			}

		});

		mSlideChoose = (SlidingButton) findViewById(R.id.slide_choose);
		if (mConfigJSInstance.getUseOpt() == ConfigJSInstance.Opt_NetALL) {
			mSlideChoose.setChecked(true);
		} else {
			mSlideChoose.setChecked(false);
		}


		mSlideChoose.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					DownloadUtils.trackBehaviorEvent(getApplicationContext(), "speedup_open_click", 0, 0);
					mConfigJSInstance.setUseOpt(ConfigJSInstance.Opt_NetALL);
				} else if (!isChecked) {
					DownloadUtils.trackBehaviorEvent(getApplicationContext(), "speedup_close_click", 0, 0);
					mConfigJSInstance.setUseOpt(ConfigJSInstance.Opt_Forbidden);
				}

			}
		});
		//*/


		changePayMsgAndBtnMsg(account, flow);

	}

	private OnAuthResultListener mOnAuthResultListener = new OnAuthResultListener() {

		@Override
		public void onAuthResult(final int result, final String token) {
			final String methodName = TAG + "_" + "onAuthResult";
			XLUtil.logDebug(TAG, "auth res:" + result + "token:" + token);
			if (result == AuthorizeActivity.RESULT_SUCCESS) {
				mRtnResult = 110;
				setToken(token);
				mpay.RequestPrize(0, 3, mToken);
				XLUtil.logDebug(TAG, "auth  mAuthFlag" + mAuthFlag);
				mAccountInstance.RequestAddFlowInfo(mToken, true);
//                DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_succ", 1, 0);
				mPool.schedule(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub

						mReInitViewFlag = 1;
						mAccountInstance.requestAccount();
						XLUtil.logDebug(TAG, "XLSpeedUpActivity onAuthResult request flow ");
						mAccountInstance.requestFlow(methodName);

						mGetAccountWhenAuthFinished = true;
					}
				}, DelayGetAccountInfo, TimeUnit.MILLISECONDS);

				XLUtil.logDebug(TAG, "auth success token:" + mToken);

				if (!XLUtil.isNullOrEmpty(token)) {
					if(mAuthFlag ==2) {
						mSetVipClickGoneFlag = 2;
					}
				}
				mHandler.post(new Runnable() {

					@Override
					public void run() {
						if (mAuthFlag == 2) {
							if (!XLUtil.isNullOrEmpty(token)) {
								if (mvipclick != null) {
									mvipclick.setVisibility(View.GONE);
									mSetVipClickGoneFlag = 0;
								}
							}
							openChoosePrizeDialog();
							mAuthFlag = 0;
						}
					}
				});
//				// 授权结束去领取流量
//				if (mAccountInstance != null) {
//					XLUtil.logDebug(TAG, "request add flow2:" + mToken);
//					//   DownloadUtils.trackBehaviorEvent(getApplicationContext(), "get_flow", 0, 0);
//					mAccountInstance.RequestAddFlowInfo(XLUtil.getStringPackagePreference(getApplicationContext()), true);
//				}

			} else {
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "bind_account_fail", 0, 0);
				XLUtil.logDebug(TAG, "授权失败");
			}

		}
	};
	private MiBiPay.PayListener mpaylisten = new PayListener() {

		@Override
		public int OnSuccessPay(int ret, String result) {
			// TODO Auto-generated method stub
			final String methodName = TAG + "_" + "OnSuccessPay";
			mRtnResult = 120;
			PaySuccessReport();
			mPool.schedule(new Runnable() {

				@Override
				public void run() {
					// TODO Auto-generated method stub
					mReInitViewFlag = 1;
					mAccountInstance.requestAccount();
					XLUtil.logDebug(TAG, "XLSpeedUpActivity OnSuccessPay request flow ");
					mAccountInstance.requestFlow(methodName);
				}
			}, DelayGetAccountInfo, TimeUnit.MILLISECONDS);
			XLUtil.logDebug(TAG, "pay成功");
			mHandler.post(new Runnable() {

				@Override
				public void run() {
					AppConfig.LOGD(TAG, "toast = " + R.string.download_list_get_hightspeed_flow_pay_ok);
					Toast.makeText(getApplicationContext(), R.string.download_list_get_hightspeed_flow_pay_ok, Toast.LENGTH_SHORT).show();
					openVipQuick();
				}
			});
			return 0;
		}

		@Override
		public int OnFailed(final int ret, final String result, final int need_token, final int need_auth) {
			// TODO Auto-generated method stub

			XLUtil.logDebug(TAG, "支付失败 " + ret + " " + result);
//            BackgroundThread.instance().postGUI(new Runnable() {
//
//                @Override
//                public void run() {
//                    // TODO Auto-generated method stu
//
//
//                }
//            });
			return 0;
		}

		@Override
		public int onGetOrderEnd(final int ret, final String result, final int month, String org_token, final int need_token, final int need_auth) {

			if (ret == 0) {
				mReRequestOrderFlag = 0;
			}
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (!isProcessDialogShow()) {
						mReRequestOrderFlag = 0;
					}
				}
			});

			if (mReRequestOrderFlag > 0) {
				mPool.schedule(new Runnable() {
					@Override
					public void run() {
						if (mpay != null) {
							mpay.RequestOrder(month, 3, "mi", "", mToken);
						}
					}
				}, DelayGetOrderInfo, TimeUnit.MILLISECONDS);
				mReRequestOrderFlag--;
				return 0;
			}
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					stopProcessDialog();
					if (ret != 0) {
						if (ret == 116 || ret == 115) {
							AppConfig.LOGD(TAG, "toast = " + result);
							Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
						} else {
							AppConfig.LOGD(TAG, "toast = " + XLSpeedUpActivity.this.getResources().getString(R.string.get_order_fail_msg));
							Toast.makeText(getApplicationContext(), XLSpeedUpActivity.this.getResources().getString(R.string.get_order_fail_msg), Toast.LENGTH_SHORT).show();
						}
					}
					if (need_auth == 1) {
						mAuthFlag = 2;
						AuthManager.getInstance().startProcessAuth(XLSpeedUpActivity.this, Constants.AUTH_TYPE_REAL);
					}
				}
			});
			return 0;
		}

		@Override
		public int OnPrizesSuccess(int ret, double[] prizes) {
			// TODO Auto-generated method stub

			return 0;
		}

		@Override
		public int OnPrizesFail(int ret, String result) {
			// TODO Auto-generated method stub
			final String res_tmp = result;
			final int res_ret = ret;
			XLUtil.logDebug(TAG, "get prize fail " + res_ret + " " + res_tmp);

			return 0;
		}
	};
	static int ans;
	private Builder mbuilder;

	private void openChoosePrizeDialog() {
		XLUtil.logDebug(TAG, "openChoosePrizeDialog");
		if (mbuilder == null) {
			mbuilder = new AlertDialog.Builder(XLSpeedUpActivity.this);
			mbuilder.setTitle(XLSpeedUpActivity.this.getResources().getString(R.string.choose_open_time));
		}
		TreeMap<Integer, String> mapTmp = MiBiPay.XLMONPrize;
		int len = 0;
		if (mapTmp == null || (len = mapTmp.size()) <= 0) {
			XLUtil.logDebug(TAG, "openChoosePrizeDialog XLMONPRIZE ==null");
			mapTmp = new TreeMap<Integer, String>();
			mapTmp.put(Integer.valueOf(1), XLSpeedUpActivity.this.getResources().getString(R.string.one_month));
			mapTmp.put(Integer.valueOf(12), XLSpeedUpActivity.this.getResources().getString(R.string.member_cost));
			len = 2;

		}
		int id = 0;
		final TreeMap<Integer, String> map = mapTmp;
		String[] chooseStr = new String[len];
		Iterator<Integer> it = map.keySet().iterator();
		while (it.hasNext()) {
			Integer sskey = (Integer) it.next();
			chooseStr[id] = map.get(sskey);
			id = id + 1;
		}

		ans = 0;
		mbuilder.setSingleChoiceItems(chooseStr, 0, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				XLUtil.logDebug(TAG, "dialog which :" + which);
				ans = which;
			}

		});
		mbuilder.setPositiveButton(XLSpeedUpActivity.this.getResources().getString(R.string.determine), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				XLUtil.logDebug(TAG, "dialog negative w:" + which + " ans:" + ans);
				// int ans = (which ==0) ? 1 : 12;
				if (!XLUtil.isNullOrEmpty(mToken)) {
					ArrayList<Integer> list = new ArrayList<Integer>(map.keySet());
					int tran_ans = list.get(ans);
					XLUtil.logDebug(TAG, "dialog negative ta:" + tran_ans + " ans:" + ans);
					mNowPayProduct = tran_ans;
					mReRequestOrderFlag = 0;
					mpay.RequestOrder(tran_ans, 3, "mi", "", mToken);
					dialog.dismiss();
					showProcessDialog();
				} else {
					dialog.dismiss();
				}
			}
		});
		mbuilder.setNegativeButton(XLSpeedUpActivity.this.getResources().getString(R.string.cancle), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog.dismiss();
			}
		});

		mbuilder.show();
	}

	private void openVipQuick() {
		mConfigJSInstance.setUseOpt(ConfigJSInstance.Opt_NetALL);
		if (mSlideChoose != null) {
			mSlideChoose.setChecked(true);
		}
	}

	public ProgressDialog XLProgressDialog = null;

	private void showXLProgressDialog() {
		XLProgressDialog = ProgressDialog.show(XLSpeedUpActivity.this, "",
				getResources().getString(R.string.get_account_dialog_msg),
				true, true);
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					if (XLProgressDialog.isShowing()) {
						XLProgressDialog.dismiss();
					}
				} catch (IllegalArgumentException e) {
					// TODO: handle exception
					e.printStackTrace();
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
			}
		}, 80000);
	}

	private void stopXLProgressDialog() {
		if (this.XLProgressDialog != null && this.XLProgressDialog.isShowing()) {
			this.XLProgressDialog.dismiss();
		}
	}

	private TigerDialog mProgressDialog = null;

	private void showProcessDialog() {
		if (mProgressDialog == null) {
			mProgressDialog = new TigerDialog(XLSpeedUpActivity.this);
			mProgressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

		}
		mProgressDialog.show();

	}

	private boolean isProcessDialogShow() {
		boolean res = false;
		if (mProgressDialog == null) {
			return res;
		}
		if (mProgressDialog.isShowing()) {
			res = true;
		}
		return res;
	}

	private void stopProcessDialog() {
		if (mProgressDialog == null)
			return;
		if (mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		XLUtil.logDebug(TAG, "onActivityResult request=" + requestCode + "resultcode=" + resultCode + "mauthflag=" + mAuthFlag);
		if (requestCode > 9999) {
			AuthManager.getInstance().processAuthComplete(data, requestCode, resultCode, Constants.WAP_TYPE_V6);
		}

	}

	// ---------------------------------------view change method
	// ------------------------------------------------------------
	private String InsertEnterAfterFirstDot(String src) {
	    /*String res = src;
	    int index = src.indexOf("，");
        XLUtil.logDebug(TAG, "Insert index dot, =" + index + ",src:"+src);
        if (index > 4) {
            res = (src.substring(0, index) + "\n" + src.substring(index + 1));
        }

        //return res.replace("，", "").replace("！", "").replace("。", "");
        return res.replace("！", "").replace("。", "");*/
		return src.replace("\\n", "\n");
	}

	private boolean isAccountInfoChangedAndChangeView(AccountInfo newAccInfo) {
		boolean res = false;
		XLUtil.logDebug(TAG, "isAccountInfoChangedAndChangeView" + newAccInfo);
		if (mDefaultAccountInfo == null) {
			mDefaultAccountInfo = newAccInfo;
			initView();
			res = true;
			return res;
		}
		if (mDefaultAccountInfo.result != 200 && newAccInfo.result == 200) {
			mDefaultAccountInfo = newAccInfo;
			initView();
			res = true;
			return res;
		}
		if (newAccInfo.result != 200) {
			return res;
		}
		if ((!XLUtil.isNullOrEmpty(newAccInfo.expire)) && (!newAccInfo.expire.equals(mDefaultAccountInfo.expire))) {
			changeViewExpire(newAccInfo);
			changePayMsgAndBtnMsg(newAccInfo, mAccountInstance.getFlowInfo());
			res = true;
		}
		if ((newAccInfo.nickname != null) && (!newAccInfo.nickname.equals(mDefaultAccountInfo.nickname))) {
			changeVipname(newAccInfo);
			res = true;
		}
		if (newAccInfo.isvip != mDefaultAccountInfo.isvip) {
			changePayMsgAndBtnMsg(newAccInfo, mAccountInstance.getFlowInfo());
			changeVipImage(newAccInfo);
			res = true;
		}
		if (newAccInfo.fake != mDefaultAccountInfo.fake) {
			changeVipClick(newAccInfo);
			changeQuickMsg(newAccInfo);
			changeVipname(newAccInfo);
			changeVipThumbImg(newAccInfo);
			changeVipImage(newAccInfo);
			res = true;
		}
		if ((!XLUtil.isNullOrEmpty(newAccInfo.figureurl3)) && (!newAccInfo.figureurl3.equals(mDefaultAccountInfo.figureurl3))) {
			changeVipThumbImg(newAccInfo);
			res = true;
		}

		mDefaultAccountInfo = newAccInfo;
		return res;
	}

	private boolean isFlowInfoChangedAndUpdateView(FlowInfo newFlowInfo) {
		boolean res = false;

		XLUtil.logDebug(TAG, "isFlowInfoChangedAndUpdateView" + newFlowInfo);
		if (mDefaultFlowInfo == null) {
			mDefaultFlowInfo = newFlowInfo;
			changeViewflow(newFlowInfo);
			changePayMsgAndBtnMsg(mDefaultAccountInfo, newFlowInfo);
			res = true;
			return res;
		}
		if (mDefaultFlowInfo.ret != 0 && newFlowInfo.ret == 0) {
			mDefaultFlowInfo = newFlowInfo;
			changeViewflow(newFlowInfo);
			changePayMsgAndBtnMsg(mDefaultAccountInfo, newFlowInfo);
			res = true;
			return res;
		}
		if (newFlowInfo.ret != 0) {
			return res;
		}

		if (mDefaultFlowInfo.org_total_capacity != newFlowInfo.org_total_capacity ||
				mDefaultFlowInfo.org_used_capacity != newFlowInfo.org_used_capacity) {
			changeViewflow(newFlowInfo);
			changePayMsgAndBtnMsg(mDefaultAccountInfo, newFlowInfo);
			mDefaultFlowInfo = newFlowInfo;
			res = true;
		}
		return res;
	}

	private void changeVipname(AccountInfo account) {
		if (mVipname == null) {
			changeVipService(account);
			return;
		}
		if (account != null && (account.result == 200) && (account.fake == 0) && (account.nickname != null)) {
			mVipname.setText(account.nickname);
			mVipname.setVisibility(View.VISIBLE);
		} else {
			//mVipname.setText(XLSpeedUpActivity.this.getResources().getString(R.string.experience_xunlei_account_speed));
			mVipname.setVisibility(View.GONE);
		}
		changeVipService(account);
	}

	private void changeVipService(AccountInfo account) {
		if (mVipOrExpTip == null) {
			return;
		}
		if (account != null && account.result == 200 && account.isvip == 1) {
			mVipOrExpTip.setText(XLSpeedUpActivity.this.getResources().getString(R.string.xunlei_gaosu));
		} else {
			mVipOrExpTip.setText(XLSpeedUpActivity.this.getResources().getString(R.string.free_speedup_exp));
		}
	}

	private void changeQuickMsg(AccountInfo account) {
		if (mQuickMsg == null) {
			return;
		}
		if (account != null && account.isvip == 1 && account.fake == 0) {
			mQuickMsg.setText(XLSpeedUpActivity.this.getResources().getString(R.string.high_speed_channel));
		} else {
			mQuickMsg.setText(XLSpeedUpActivity.this.getResources().getString(R.string.free_high_speed_channel));
		}
	}

	private void changeVipClick(AccountInfo account) {
		if (mvipclick == null) {
			return;
		}
		if (account != null && (account.result == 200) && (account.fake == 0)) {
			mvipclick.setVisibility(View.GONE);
			mAutoSpeedTip.setVisibility(View.GONE);
		} else {
			mvipclick.setVisibility(View.VISIBLE);
			mAutoSpeedTip.setVisibility(View.VISIBLE);
		}
	}

	private int changePayMsgAndBtnMsg(AccountInfo acc, FlowInfo flow) {
		int res = -1;

		if (mPayMsg == null || mbtnpay == null) {
			return res;
		}
		ConfigJSInfo config = ConfigJSInstance.getInstance(getApplicationContext()).getConfigJSInfo();
		if (config == null) {
			return res;
		}

		String payMsg = InsertEnterAfterFirstDot(config.speed_page_openvip);
		XLUtil.logDebug(TAG, "pay msg speed_page_openvip=" + config.speed_page_openvip);
		String btnMsg = XLSpeedUpActivity.this.getResources().getString(R.string.dredge_xunlei_account);
		if (acc.result == 200 && acc.isvip == 0) {
			GiveFlowUsedState flowstate = mNotificationLogic.getShowBeforeGivenFlowOutStateWithoutIsShown();
			XLUtil.logDebug(TAG, "getShowBeforeGivenFlowOutStateWithoutIsShown flowstate=" + flowstate);
			if (flowstate == GiveFlowUsedState.OUT_LIMIT_FLOW) {
				XLUtil.logDebug(TAG, "pay msg speed_page_openvip_flow_before_used=" + config.speed_page_openvip_flow_before_used);
				payMsg = InsertEnterAfterFirstDot(config.speed_page_openvip_flow_before_used);
			} else if (flowstate == GiveFlowUsedState.NO_FLOW) {
				XLUtil.logDebug(TAG, "pay msg speed_page_openvip_flow_used=" + config.speed_page_openvip_flow_used);
				payMsg = InsertEnterAfterFirstDot(config.speed_page_openvip_flow_used);
			}
		}
		if (acc.result == 200 && acc.isvip == 1) {
			VipExpireStatus vipstatus = mNotificationLogic.isOutDateVip();
			XLUtil.logDebug(TAG, "isOutDateVip vipstatus=" + vipstatus);
			switch (vipstatus) {
				default:
					btnMsg = XLSpeedUpActivity.this.getResources().getString(R.string.immediately_a_renewal);
					break;
				case MOREDAY:
					btnMsg = XLSpeedUpActivity.this.getResources().getString(R.string.immediately_a_renewal);
					break;
				case SEVENDAY:
				case FOURDAY:
				case ONEDAY:
					payMsg = InsertEnterAfterFirstDot(config.speed_page_openvip_expire);
					XLUtil.logDebug(TAG, "pay msg speed_page_openvip_expire=" + config.speed_page_openvip_expire);
					btnMsg = XLSpeedUpActivity.this.getResources().getString(R.string.immediately_a_renewal);
					break;
				case TODAY:
					payMsg = InsertEnterAfterFirstDot(config.speed_page_openvip_expire_today);
					XLUtil.logDebug(TAG, "pay msg speed_page_openvip_expire_today=" + config.speed_page_openvip_expire_today);
					btnMsg = XLSpeedUpActivity.this.getResources().getString(R.string.immediately_a_renewal);
					break;
			}
		}
		if (payMsg.contains("，")) {
			String s[] = payMsg.split("，");
			mPayMsg.setText(s[0] + "，" + s[1]);
			mbtnpay.setText(btnMsg);
			return res;
		}
		mPayMsg.setText(payMsg);
		mbtnpay.setText(btnMsg);
		return res;
	}

	private void changeViewExpire(AccountInfo account) {

		if (mVipInfo == null)
			return;

		// if (account != null && (!XLUtil.isNullOrEmpty(account.expire))) {
		if (account != null && account.result == 200 && account.isvip == 1) {
			if (account.expire.equals("-1")) {

				mVipInfo.setText(XLSpeedUpActivity.this.getResources().getString(R.string.display_expire_netiv1));
			} else {
				mVipInfo.setText(XLSpeedUpActivity.this.getResources().getString(R.string.period_of_validity) + XLUtil.convertDateToShowDate2(account.expire));
			}


			// mVipInfo.setText("有效期："+account.expire);
		} else {
			//mVipInfo.setText(XLSpeedUpActivity.this.getResources().getString(R.string.period_of_validity) + "-/-/-");
			mVipInfo.setText(XLSpeedUpActivity.this.getResources().getString(R.string.valid_in_this_month));
		}
	}

	private void changeViewflow(FlowInfo flow) {
		if (mQuickDetail == null || mProcess == null) {
			return;
		}

		if (flow != null && flow.ret == 0) {
			XLUtil.logDebug(TAG,
					"ratediv =" + XLUtil.ratedDivide(flow.org_used_capacity,
							flow.org_total_capacity));
			mQuickDetail.setText("(" + formatFlow(flow.used_capacity) + "/" + formatFlow(flow.total_capacity) + ")  ");
			mProcess.setProgress(XLUtil.ratedDivide(flow.org_used_capacity, flow.org_total_capacity));
		} else {
			mQuickDetail.setText("(-/-)");
			mProcess.setProgress(0);
		}
	}

	private String formatFlow(String flow) {
		String value = flow;
		Pattern p = Pattern.compile("(\\d+(\\.)?\\d*)(\\w{1,2}).*");
		Matcher m = p.matcher(value);
		double myValue = 0;
		String numgroup = "";
		String unit = "";
		if (m.matches()) {
			numgroup = m.group(1);
			unit = m.group(3);
			//System.out.println("mzh"+m.group(3) + "-aaaa--");
			myValue = Double.valueOf(numgroup);
		}
		if (DownloadUtils.isNumGood(myValue)) {
			return String.valueOf((int) myValue) + DownloadUtils.formatUnit(unit);
		} else {
			return String.format("%.1f", myValue) + DownloadUtils.formatUnit(unit);
		}
	}

	private void changeVipImage(AccountInfo info) {
		if (mVipImg == null) {
			return;
		}
		mVipImg.setVisibility(View.GONE);
		if (info == null) {
			return;
		}
		if (info.fake == 1) {
			return;
		}
		if (info.result != 200) {
			return;
		}
		int image_id = 0;
		if (info.isvip == 1) {
			switch (info.level) {
				case 1:
					image_id = R.drawable.isvip_01;
					break;
				case 2:
					image_id = R.drawable.isvip_02;
					break;
				case 3:
					image_id = R.drawable.isvip_03;
					break;
				case 4:
					image_id = R.drawable.isvip_04;
					break;
				case 5:
					image_id = R.drawable.isvip_05;
					break;
				case 6:
					image_id = R.drawable.isvip_06;
					break;
				case 7:
					image_id = R.drawable.isvip_07;
					break;
			}
		} else if (info.isvip == 0) {
			switch (info.level) {
				case 1:
					image_id = R.drawable.notvip_01;
					break;
				case 2:
					image_id = R.drawable.notvip_02;
					break;
				case 3:
					image_id = R.drawable.notvip_03;
					break;
				case 4:
					image_id = R.drawable.notvip_04;
					break;
				case 5:
					image_id = R.drawable.notvip_05;
					break;
				case 6:
					image_id = R.drawable.notvip_06;
					break;
				case 7:
					image_id = R.drawable.notvip_07;
					break;
			}
		}
		if (image_id != 0) {
			mVipImg.setImageResource(image_id);
			mVipImg.setVisibility(View.VISIBLE);
		}
	}

	private void changeVipThumbImg(AccountInfo account) {
		if (account == null || mVipThumbImg == null)
			return;
		if (account.fake == 1) {
			mVipThumbImg.setImageResource(R.drawable.head_default);
		}
		if (account.fake == 0) {
			getVipThumbImg(account.figureurl3);
		}
		return;
	}

	private RequestQueue mRequestQueue;

	private void getVipThumbImg(String url) {

		if (XLUtil.isNullOrEmpty(url)) {
			return;
		}
		if (mRequestQueue == null) {
			mRequestQueue = MyVolley.getInstance(getApplicationContext()).getRequestQueue();
		}
		ImageLoader imageLoader = MyVolley.getInstance(getApplicationContext()).getImageLoader();
		imageLoader.get(url, new ImageListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onResponse(ImageContainer response, boolean isImmediate) {
				// TODO Auto-generated method stub
				if (response.getBitmap() != null) {
					mVipThumbImg.setImageBitmap(response.getBitmap());
				}
			}
		});
	}

	// --------------------------------report
	// method-----------------------------------------------------------

	static GiveFlowUsedState mflowstate;
	static VipExpireStatus mvipstatus;
	static int mNowPayProduct;
	static AccountInfo mReportAccountInfo;

	private void PaybtnClickReport(AccountInfo info) {
		mflowstate = mNotificationLogic.getShowBeforeGivenFlowOutStateWithoutIsShown();
		mvipstatus = mNotificationLogic.isOutDateVip();
	    /*
	    if (XLUtil.isNullOrEmpty(mToken) || info == null || info.result != 200) {
		    if (mflowstate == GiveFlowUsedState.NO_FLOW) {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 0, 1);
		    } else {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 0, 0);
		    }

		    return;
	    }
	    if (info.fake == 1) {
		    if (mflowstate == GiveFlowUsedState.NO_FLOW) {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 1, 1);
		    } else {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 1, 0);
		    }
		    return;
	    }


	    if (info.isvip == 0) {
		    if (mflowstate == GiveFlowUsedState.NO_FLOW) {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 2, 1);
		    } else {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_click", 2, 0);
		    }
	    } else if (info.isvip == 1) {
		    if (mvipstatus == VipExpireStatus.OUTDATE) {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "renew_click", 1, 0);
		    } else {
			    DownloadUtils.trackBehaviorEvent(getApplicationContext(), "renew_click", 0, 0);
		    }
	    }
	    mReportAccountInfo = info;*/
		String key = null;
		int data1 = -1;
		int data2 = -1;

		if (info.isVip()) {
			key = "renew_click";
			data1 = data2 = 0;
		} else {
			key = "openvip_click";

			if (info.isFake()) {
				data1 = 0;
			} else {
				if (info.isAuto()) {
					data1 = 1;
				} else {
					if (info.level != 0) {    // 过期会员
						data1 = 3;
					} else {
						data1 = 2;
					}
				}
			}

			if (info.isVip()) {
				data2 = 0;
			} else {
				if (mflowstate == GiveFlowUsedState.NO_FLOW) {
					data2 = 1;
				} else {
					data2 = 0;
				}
			}
		}

		mStatInfo = new StatInfo();
		mStatInfo.data1 = data1;
		mStatInfo.data2 = data2;
		mStatInfo.key = key;
		DownloadUtils.trackBehaviorEvent(getApplicationContext(), key, data1, data2);
	}

	private void PaySuccessReport() {
		if (mStatInfo == null) {
			return;
		}

		String key = null;
		int data1 = mStatInfo.data1;
		if (mStatInfo.key.equals("renew_click")) {
			key = "renew_succ";
		} else if (mStatInfo.key.equals("openvip_click")) {
			key = "openvip_succ";
		}

		if (key != null) {
			DownloadUtils.trackBehaviorEvent(getApplicationContext(), key, data1, mNowPayProduct);
		}
		mStatInfo = null;
	}

	/*
		private void PaySuccessReport() {
			AccountInfo info = mReportAccountInfo;
			if (info == null || info.result != 200) {
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 0, mNowPayProduct);
				return;
			}

			if (info.fake == 1) {
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 0, mNowPayProduct);
				return;
			}

			if (info.isvip == 0) {
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 1, mNowPayProduct);
	//            if (mflowstate == GiveFlowUsedState.NO_FLOW) {
	//                DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 3, mNowPayProduct);
	//            } else {
	//                DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 2, mNowPayProduct);
	//            }
			} else if (info.isvip == 1) {
				DownloadUtils.trackBehaviorEvent(getApplicationContext(), "openvip_succ", 1, mNowPayProduct);
				if (mvipstatus == VipExpireStatus.OUTDATE) {
					DownloadUtils.trackBehaviorEvent(getApplicationContext(), "renew_succ", 1, mNowPayProduct);
				} else {
					DownloadUtils.trackBehaviorEvent(getApplicationContext(), "renew_succ", 0, mNowPayProduct);
				}
			}
		}
	*/
	// ----------------------------------activity override method
	// ----------------------------------------------------
	@Override
	protected void onPause() {
		mReInitViewFlag = 0;
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		final String methodName = TAG + "_" + "onResume";
		// TODO Auto-generated method stub
		XLUtil.logDebug(TAG, "speedactivity onResume");
		// initView();
		if (mAccountInstance != null) {
			mAccountInstance.requestAccount();
		}
		if (mSetVipClickGoneFlag == 2 && mvipclick != null) {
			mvipclick.setVisibility(View.GONE);

		}
		mSetVipClickGoneFlag = 0;

		if (mPayMsg != null) {
			if (XLUtil.isLanguageCNorTW()) {
				mPayMsg.setVisibility(View.VISIBLE);
			} else {
				mPayMsg.setVisibility(View.GONE);
			}
		}

		mPool.schedule(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				mReInitViewFlag = 2;
//                askRequestToken();
				XLUtil.askRequestTokenFake(getApplicationContext());
				initToken();
				mAccountInstance.requestAccount();
				XLUtil.logDebug(TAG, "XLSpeedUpActivity onResume request flow ");
				mAccountInstance.requestFlow(methodName);
			}
		}, DelayGetAccountInfo, TimeUnit.MILLISECONDS);
		AccountInfo account = mAccountInstance.getAccountInfo();
		FlowInfo flow = mAccountInstance.getFlowInfo();
		if (account.result != 200 && flow.ret != 0) {
			showXLProgressDialog();
		}

		//showXLProgressDialog();
		//TigerDialog t=new TigerDialog(this);
		//t.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
		//t.show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		XLUtil.logDebug(TAG, "speedactivity ondestroy");
		setResult(mRtnResult);
		if (mpay != null) {
			mpay.uninit();
		}
		if (mHeadLayout != null) {
			mHeadLayout.removeView(mVipThumbImg);
		}
		UnbindManager.getInstance().removeUnbindListener(mUnbindListener);
		AuthManager.getInstance().removeAuthListener(mOnAuthResultListener);
		mAccountInstance.removeAccountListener(mAccountListen);
		unregisterReceiver(mLoginReceiver);
		mPool.shutdown();
	}

	static class TigerDialog extends Dialog {


		public TigerDialog(final Context ctx) {
			super(ctx);
			requestWindowFeature(Window.FEATURE_NO_TITLE);
//            this.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
			setContentView(R.layout.speedup_activity1);
			Window window = getWindow();
			WindowManager.LayoutParams attributesParams = window.getAttributes();
			attributesParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			attributesParams.dimAmount = 0.5f;

			window.setLayout(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		}

		@Override
		public void onWindowFocusChanged(boolean hasFocus) {
			super.onWindowFocusChanged(hasFocus);
		}

		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			this.dismiss();
			return super.onKeyDown(keyCode, event);
		}
	}
}
