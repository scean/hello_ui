package com.android.providers.downloads.ui.activity;

import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.view.MenuItem;

import com.android.providers.downloads.ui.auth.Constants;
import com.xiaomi.account.openauth.AuthorizeActivity;


public class XLAuthorizeActivity extends BaseActivity {
	// public static final String HOME_URL =
	// "file:///android_asset/LocalJsDemo.html";
	public static final String HOME_URL = "https://open-api-auth.xunlei.com/platform?m=BindOauth&op=recieveCode";
	private WebView mXLWebView;

	private WebViewClient mWebViewClient = new WebViewClient() {

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			handler.proceed();
		}

		@Override
		public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
			if (url.indexOf("result=") > 0) {
				int result = AuthorizeActivity.RESULT_FAIL;
				String token = null;

				Map<String, String> paramValues = getParamMapValue(url);
				if (paramValues != null && paramValues.size() > 0) {
					result = Integer.valueOf(paramValues.get("result"));
					token = paramValues.get("token");
					if (result == 0) {
						result = AuthorizeActivity.RESULT_SUCCESS;
					}
				}

				setResultAndFinish(result, token);
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			return super.shouldOverrideUrlLoading(view, url);
		}

	};

	// 返回结果并关闭Activity
	private void setResultAndFinish(int result, String token) {
		Bundle bundle = new Bundle();
		bundle.putInt("result", result);
		bundle.putString("token", token);

		Intent intent = new Intent();
		intent.putExtras(bundle);
		setResult(Constants.REQUESTCODE_XL_TOKEN, intent);
		finish();
	}

	/**
	 * 获取请求参数部分
	 *
	 * @param url地址
	 * @return url请求参数部分
	 */
	private String getParams(String url) {
		String strAllParam = null;
		String[] arrSplit = null;
		url = url.trim();
		arrSplit = url.split("[?]");
		if (url.length() > 1) {
			if (arrSplit.length > 1) {
				if (arrSplit[1] != null) {
					strAllParam = arrSplit[1];
				}
			}
		}
		return strAllParam;
	}

	/**
	 * 解析出url参数中的键值对
	 *
	 * @param url地址
	 * @return url请求参数部分
	 */
	private Map<String, String> getParamMapValue(String url) {
		Map<String, String> mapRequest = new HashMap<String, String>();
		String[] arrSplit = null;
		String strUrlParam = getParams(url);
		if (strUrlParam == null) {
			return mapRequest;
		}
		// 每个键值为一组
		arrSplit = strUrlParam.split("[&]");
		for (String strSplit : arrSplit) {
			String[] arrSplitEqual = null;
			arrSplitEqual = strSplit.split("[=]");
			if (arrSplitEqual.length > 1) { // 解析出键值
				mapRequest.put(arrSplitEqual[0], arrSplitEqual[1]); // 正确解析
			} else {
				if (arrSplitEqual[0] != "") { // 只有参数没有值，不加入
					mapRequest.put(arrSplitEqual[0], "");
				}
			}
		}
		return mapRequest;
	}

	public static void startXunLeiLogin(Activity activity, String code, int wap, int secondStep) {
		if (activity == null) {
			return;
		}
		Intent intent = new Intent();
		intent.setClass(activity, XLAuthorizeActivity.class);
		intent.putExtra("code", code);
		intent.putExtra("wap", wap);
		intent.putExtra("secondStep", secondStep);
		// XLUtil.logDebug("secondStep", secondStep+"");
		activity.startActivityForResult(intent, Constants.REQUESTCODE_XL_TOKEN);
	}

	int wap;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(createView());
		this.setTitle("迅雷帐号授权");
		Intent intent = this.getIntent();
		String code = intent.getStringExtra("code");
		wap = intent.getIntExtra("wap", 0);
		int secondStep = intent.getIntExtra("secondStep", 0);
		String secondStepstr = "";
		if (secondStep == 0) {

		} else if (secondStep == 1) {
			secondStepstr = "1";
		}
		mXLWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		mXLWebView.getSettings().setJavaScriptEnabled(true);
		mXLWebView.getSettings().setLoadWithOverviewMode(true);
		mXLWebView.setWebViewClient(mWebViewClient);
//		if(Constants.CURRENT_MIUI_V ==6){
//			ActionBar actionBar = getActionBar();
//			actionBar.setDisplayHomeAsUpEnabled(true);
//			actionBar.setHomeButtonEnabled(true);
//			actionBar.setIcon(null);
//			actionBar.setDisplayUseLogoEnabled(false);
//		}

//		把本地方法注入给JS调用
//		mXLWebView.addJavascriptInterface(this, "android");

		mXLWebView.loadUrl("https://open-api-auth.xunlei.com/platform?m=BindOauth&op=recieveCode&wap=" + wap + "&code=" + code + "&secondStep=" + secondStepstr
				+ "&redirect_uri=&invisible=0&fake=0&force_login=0");
		// mXLWebView.loadUrl(HOME_URL);

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// showExitConfirmDialog();
			String url = mXLWebView.getUrl();
			if (url != null && url.startsWith(HOME_URL) && wap == 4) {
				executeJsFunction("back");
			} else {
				finish();
			}
			return true;
		} else {
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	protected void onDestroy() {
		super.onDestroy();
		if (mXLWebView != null) {
			mXLWebView.removeAllViews();
			mXLWebView.destroy();
		}
	}

	// 创建webview界面布局
	private View createView() {
		LinearLayout linear = new LinearLayout(this);
		linear.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		linear.setOrientation(1);
		mXLWebView = new WebView(this);
		linear.addView(mXLWebView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		return linear;
	}

	/**
	 * 执行带参数的JS函数
	 *
	 * @param functionName
	 * @param params
	 */
	public void executeJsFunction(String functionName, String... params) {
		executeJs(getFormatJscipt(functionName, params));
	}

	/**
	 * 工具方法 根据JS方法名和和参数返回可以在WebView中执行的JS串
	 *
	 * @param callback
	 * @param params
	 * @return
	 */
	private static String getFormatJscipt(String functionName, String... params) {
		if (TextUtils.isEmpty(functionName)) {
			return null;
		}

		StringBuffer res = new StringBuffer();
		res.append(functionName);
		res.append("(");

		// 有参数则把参数添加进来
		if (params != null && params.length > 0) {
			for (int i = 0; i < params.length; i++) {
				res.append("'" + params[i] + "'");
				if (i != params.length - 1) {
					res.append(",");
				}
			}
		}

		res.append(")");
		return res.toString();
	}

	/**
	 * 执行一段JS代码
	 *
	 * @param js
	 */
	public void executeJs(final String jsScript) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (!TextUtils.isEmpty(jsScript)) {
					mXLWebView.loadUrl("javascript:" + jsScript);
				}
			}
		});
	}

	private Handler handler = new Handler();

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// 实现JS本地接口

	// 提示框
	//public void alert(final String text) {
	// alert(text, null);
	//}
}
