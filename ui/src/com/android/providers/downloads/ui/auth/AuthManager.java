package com.android.providers.downloads.ui.auth;

import java.util.ArrayList;
import java.util.List;

import com.android.providers.downloads.ui.activity.XLAuthorizeActivity;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.xiaomi.account.openauth.AuthorizeActivity;
import com.xiaomi.account.openauth.AuthorizeApi;
import com.xiaomi.account.openauth.XMAuthericationException;
import com.xiaomi.account.openauth.XiaomiOAuthorize;
import com.xiaomi.auth.AuthConstants;


public class AuthManager {

	private Long clientId = 2882303761517159179L;
	private String redirectUri = "https://open-api-auth.xunlei.com/platform?m=BindOauth&op=bindCallback";
	// private String clientSecret = "KIV/4Ittm17a4pIvzNM2wA==";

	private MiTokenInfo miTokenInfo = new MiTokenInfo();
	private static AuthManager mRequestMISdkManager = null;
	private static Activity mActivity;
	private static int mAuthType;
	private static int mWapType;

	public static int secondStep = 0;

	private List<OnAuthResultListener> mAuthResultListeners = new ArrayList<OnAuthResultListener>();

	private AuthManager() {
		setGlobalConstant();
	}
	

	public static AuthManager getInstance() {
		if (mRequestMISdkManager == null) {
			mRequestMISdkManager = new AuthManager();
		}
		return mRequestMISdkManager;
	}
	private void setGlobalConstant(){
		if(Constants.CURRENT_MIUI_V == 5){
			mWapType =Constants.WAP_TYPE_CUSTOM;
		}else if(Constants.CURRENT_MIUI_V == 6){
			mWapType =Constants.WAP_TYPE_V6;
		}else{
			mWapType =Constants.WAP_TYPE_CUSTOM;
		}
	}
	// 添加授权结束监听器
	public void addAuthListener(OnAuthResultListener onAuthResultListener) {

		Log.e("JF", "addAL:" + onAuthResultListener.getClass().getName());

		if (onAuthResultListener != null && !mAuthResultListeners.contains(onAuthResultListener)) {
			mAuthResultListeners.add(onAuthResultListener);
		}

	}

	// 移除授权结束监听器
	public void removeAuthListener(OnAuthResultListener onAuthResultListener) {
		Log.e("JF", "remove:" + onAuthResultListener.getClass().getName());
		if (onAuthResultListener != null) {
			mAuthResultListeners.remove(onAuthResultListener);
		}
		for (int i = 0; i < mAuthResultListeners.size(); i++) {
			OnAuthResultListener o = mAuthResultListeners.get(i);
			Log.e("JF", o.getClass().getName());
		}
	}

	// 处理授权结束结果
	public void processOnAuthResultBack(int result, String token) {
		// Log.e("jiangfeiXl", "processOnAuthResultBack|" + "处理授权结束结果");
		if (!mAuthResultListeners.isEmpty()) {

			Log.e("JF", "处理授权结束结果:token=" + token + ";result=" + result);

			for (OnAuthResultListener onAuthResultListener : mAuthResultListeners) {
				if (onAuthResultListener == null) {
					continue;
				}
				Log.e("JF", onAuthResultListener.getClass().getName());
				onAuthResultListener.onAuthResult(result, token);
			}
		}

	}

	// 开始处理Auth流程 secondStep 用户 后台页面自动跳转 
	public void startProcessAuth(Activity activity, int authType, int secondStep) {
		this.secondStep = secondStep;
		time = System.currentTimeMillis();
		Log.e("jiangfeiXl", "startProcessAuthElse 开始处理Auth流程|" + authType + time + ":secondStep" + secondStep);
		if (activity == null || (authType != Constants.AUTH_TYPE_FAKE && authType != Constants.AUTH_TYPE_REAL)) {
			Log.e("jiangfeiXl", "startProcessAuth|" + authType);
			processOnAuthResultBack(AuthorizeActivity.RESULT_FAIL, null);
		} else {
			getXiaoMiCode(activity, authType);
		}
	}

	// 开始处理Auth流程
	public void startProcessAuth(Activity activity, int authType) {
		time = System.currentTimeMillis();
		Log.e("jiangfeiXl", "startProcessAuthElse 开始处理Auth流程|" + authType + time);
		if (activity == null || (authType != Constants.AUTH_TYPE_FAKE && authType != Constants.AUTH_TYPE_REAL)) {
			Log.e("jiangfeiXl", "startProcessAuth|" + authType);
			processOnAuthResultBack(AuthorizeActivity.RESULT_FAIL, null);
		} else {
			getXiaoMiCode(activity, authType);
		}
	}

	// 获取小米Token
	public void getXiaoMiCode(Activity activity, int authType) {
		clearCache();
		Bundle options = new Bundle();
		options.putString(AuthConstants.EXTRA_SKIP_CONFIRM, "true");

		mActivity = activity;
		mAuthType = authType;
		// Log.e("jiangfeiXl", "startGetOAuthCode|clientId=" + clientId +
		// "|redirectUri=" + redirectUri + "time=" + time);

		XiaomiOAuthorize.startGetOAuthCode(activity, clientId, redirectUri, options, Constants.REQUESTCODE_CODE);

		// Log.e("jiangfeiXl", "(只是调用完不代表已经返回)调用完后GetOAuthCode|clientId=" +
		// clientId + "|redirectUri=" + redirectUri + "time=" + time);

	}

	// 获取迅雷Token
	public void requertXunLeiToken() {
		// Log.e("jiangfeiXl", "获取迅雷Token开始");
		new GetXunLeiTokenThread().start();
	}

	long time = 0l;

	// 处理授权JSON信息
	public MiTokenInfo processAccessToken(String jsonStr) {
		try {
			JSONObject json = new JSONObject(jsonStr);
			if (json.has("error")) {
				return null;
			}
			miTokenInfo = new MiTokenInfo();
			miTokenInfo.accessToken = json.optString("access_token");
			miTokenInfo.tokenType = json.optString("token_type");
			miTokenInfo.refreshToken = json.optString("refresh_token");
			miTokenInfo.macKey = json.optString("mac_key");
			miTokenInfo.macAlgorithm = json.optString("mac_algorithm");
			miTokenInfo.expiresIn = json.optString("expires_in");
			miTokenInfo.scope = json.optString("scope");
		} catch (JSONException e) {
			// Log.e("testopenauth", "json字符串不合法");
		}
		return null;
	}

	// 处理授权Bundle信息
	public MiTokenInfo processAuthResult(Bundle bundle) {
		miTokenInfo = new MiTokenInfo();
		miTokenInfo.accessToken = bundle.getString("access_token");
		miTokenInfo.tokenType = bundle.getString("token_type");
		miTokenInfo.expiresIn = bundle.getString("expires_in");
		miTokenInfo.scope = bundle.getString("scope");
		miTokenInfo.code = bundle.getString("code");
		miTokenInfo.state = bundle.getString("state");
		miTokenInfo.macKey = bundle.getString("mac_key");
		miTokenInfo.macAlgorithm = bundle.getString("mac_algorithm");
		return miTokenInfo;
	}

	// 处理授权流程完毕
	public void processAuthComplete(Intent data, int requestCode, int resultCode, int wap) {

		// Log.e("jiangfeiXl", "从授权页面回来处理授权流程完毕，花费时间=" +
		// (System.currentTimeMillis() - time) + "毫秒");

		if (data == null) {
			return;
		}
		Bundle bundle = data.getExtras();
		if (Constants.REQUESTCODE_CODE == requestCode) {
			if (AuthorizeActivity.RESULT_SUCCESS == resultCode) { // 小米Auth验证成功
				MiTokenInfo miInfo = processAuthResult(bundle);

				if (mAuthType == Constants.AUTH_TYPE_FAKE) {
					Log.e("jiangfeiXl", "开始迅雷伪授权");
					requertXunLeiToken();
				} else if (mAuthType == Constants.AUTH_TYPE_REAL) {
					Log.e("jiangfeiXl", "开始迅雷真授权");
					XLAuthorizeActivity.startXunLeiLogin(mActivity, miInfo.code, wap, AuthManager.this.secondStep);
					this.secondStep = 0;
				}

			} else if (AuthorizeActivity.RESULT_FAIL == resultCode) { // 小米Auth验证失败
				processOnAuthResultBack(AuthorizeActivity.RESULT_FAIL, null);
			}
		} else if (Constants.REQUESTCODE_XL_TOKEN == requestCode) {
			int result = bundle.getInt("result");
			String token = bundle.getString("token");
			processOnAuthResultBack(result, token);
		}

	}

	// 获取用户相关信息
	public String getXiaoMiUserInfo(String path) {
		try {
			return AuthorizeApi.doHttpGet(mActivity, path, clientId, miTokenInfo.accessToken, miTokenInfo.macKey, miTokenInfo.macAlgorithm);
		} catch (XMAuthericationException e) {
			e.printStackTrace();
		}
		return "";
	}

	public void clearCache() {
		miTokenInfo = new MiTokenInfo();
	}

	// 获取迅雷Token线程
	private class GetXunLeiTokenThread extends Thread {

		@Override
		public void run() {
			String result = null;
			try {
				// Log.e("jiangfeiXl", "start迅雷伪授权");
				result = new RequestXLAuthJob().getXLAuth(mActivity, miTokenInfo.code, mAuthType, mWapType, null, Constants.INVISABLE_TYPE_JSON, Constants.FORCE_LOGIN_FALSE);
				JSONObject jsonObject = new JSONObject(result);
				int resultNum = jsonObject.optInt("result");
				String token = jsonObject.optString("token");
				if (resultNum == 0) {
					processOnAuthResultBack(AuthorizeActivity.RESULT_SUCCESS, token);
				} else {
					processOnAuthResultBack(AuthorizeActivity.RESULT_FAIL, result);
				}
				// Log.e("jiangfeiXl", "end迅雷伪授权");
			} catch (Exception e) {
				processOnAuthResultBack(AuthorizeActivity.RESULT_FAIL, result);
			}

		}

	}

}
