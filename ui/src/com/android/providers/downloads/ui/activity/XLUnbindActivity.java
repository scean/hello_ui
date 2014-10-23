package com.android.providers.downloads.ui.activity;
 
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;

import android.app.Activity;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.*;
import android.widget.LinearLayout;

import com.android.providers.downloads.ui.auth.Constants;
import com.android.providers.downloads.ui.auth.UnbindManager;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.xiaomi.account.openauth.AuthorizeActivity;


import android.view.MenuItem;

public class XLUnbindActivity extends BaseActivity {
	// public static final String HOME_URL =
	// "file:///android_asset/LocalJsDemo.html";
	public static final String HOME_URL = "https://open-api-auth.xunlei.com/platform?m=Unbind&from=";
	//public static final String HOME_URL = "https://125.39.36.126/platform?m=Unbind&from=miui_v5&token=";
	private WebView mXLWebView;

	private WebViewClient mWebViewClient = new WebViewClient() {

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			XLUtil.logDebug("XLUnbindActivity", "onReceivedSslError=" + error.getUrl());
			
			handler.proceed();
		}

		@Override
		public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
			super.onPageStarted(view, url, favicon);

			XLUtil.logDebug("XLUnbindActivity", "onPageStarted url=" + url);
		}
		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			if (url.startsWith("https://open.account.xiaomi.com/checkPassword?")) {
				view.loadUrl("javascript:void((function(){var idxu=document.getElementById('miniLoginFrame').src.lastIndexOf('&userId=');var idxn=document.getElementById('miniLoginFrame').src.lastIndexOf('&nickName=');var idxd=document.getElementById('miniLoginFrame').src.lastIndexOf('&_dc=');if(idxu!=-1&&idxn!=-1&&idxd!=-1){var uid=document.getElementById('miniLoginFrame').src.substring(idxu+8,idxn);var nickFirst=document.getElementById('miniLoginFrame').src.substring(0,idxn+10);var dcLast=document.getElementById('miniLoginFrame').src.substring(idxd);if(idxn+10==idxd){document.getElementById('miniLoginFrame').src=nickFirst+uid+dcLast;}}})())");

			}

			XLUtil.logDebug("XLUnbindActivity", "onPageFinished url=" + url);
		}
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			/*String start = "https://open-api-auth.xunlei.com/platform?";
			
			if (url.startsWith(start)) {
				XLUtil.logDebug("XLUnbindActivity", "shouldOverrideUrlLoading url=" + url);
				view.loadUrl(url.replace(start, "https://125.39.36.126/platform?"));
				return true;
			}*/
		
			return super.shouldOverrideUrlLoading(view, url);
		}

	};

	@SuppressLint({ "SetJavaScriptEnabled", "JavascriptInterface" })
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(createView());
		this.setTitle("解绑迅雷帐号");

		CookieSyncManager.createInstance(this);
		CookieManager.getInstance().removeSessionCookie();
		CookieSyncManager.getInstance().sync();

		mXLWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		mXLWebView.getSettings().setJavaScriptEnabled(true);
		mXLWebView.getSettings().setLoadWithOverviewMode(true);
		mXLWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true); 
		
		mXLWebView.setWebViewClient(mWebViewClient);

		// 把本地方法注入给JS调用
		mXLWebView.addJavascriptInterface(this, "Unbind");

		Intent intent = this.getIntent();
		String token = intent.getStringExtra("token");
		String from = intent.getStringExtra("from");
		mXLWebView.loadUrl(HOME_URL+ from+"&token="+token);
		XLUtil.logDebug("XLUnbindActivity", "loadUrl=" + HOME_URL+ from+"&token="+token);
		
//		if(Constants.CURRENT_MIUI_V ==6){
//			ActionBar actionBar = getActionBar();
//			actionBar.setDisplayHomeAsUpEnabled(true);
//			actionBar.setHomeButtonEnabled(true);
//			actionBar.setIcon(null);
//			actionBar.setDisplayUseLogoEnabled(false);
//		}
//		mXLWebView.loadUrl(HOME_URL);

	}

	@JavascriptInterface
	public void onSuccess(int flag, int result)
	{
		XLUtil.logDebug("XLUnbindActivity", "result=" + result);
		
		finish();
		UnbindManager manager = UnbindManager.getInstance();
		manager.fireUnbindResultListeners(flag, result);		
	}
	@JavascriptInterface
	public void onDealMiLoginFrame(String src) {
		if (src != null && mXLWebView != null) {
			XLUtil.logDebug("XLUnbindActivity", "onDealMiLoginFrame=" + src);
			mXLWebView.loadUrl("javascript:void(document.getElementById('miniLoginFrame').src='https://account.xiaomi.com/pass/static/repeatpass_wap.html?inframe=true&onetimeEncode=true&_locale=zh_CN&callback=https%3A%2F%2Fopen.account.xiaomi.com%2Fsts%2FcheckPassword%3Fthirdqs%3DclientId%253D2882303761517159179%2526xmUserId%253D545127359%2526callback%253Dhttps%25253A%25252F%25252Fopen-api-auth.xunlei.com%25252Fplatform%25253Fm%25253DUnbind%252526op%25253DvaliateResult%26sid%3Doauthopenapi%26sign%3DIb3horUmfhQFoQV2LgZ1BLPKbRg%253D%26checkPwd%3Dtrue&sid=oauthopenapi&qs=&sign=vKjJmmKte4y5I1jpZh4TmO2C8ec%3D&hidden=&userId=545127359&nickName=545127359&_dc=1411300099152')");

			/*int idxu = src.lastIndexOf("&userId=");
			int idxn = src.lastIndexOf("&nickName=");
			int idxd = src.lastIndexOf("&_dc=");

			if (idxu != -1 && idxn != -1 && idxd != -1) {
				String uid = src.substring(idxu + 8, idxn);
				String nickFirst = src.substring(0, idxn + 10);
				String dcLast = src.substring(idxd);

				if (idxn + 10 == idxd) { // nickname is null
					src = nickFirst + uid + dcLast;
					XLUtil.logDebug("XLUnbindActivity", "onDealMiLoginFrame=" + "javascript:void(document.getElementById('miniLoginFrame').src='" + src + "')");
					mXLWebView.loadUrl("javascript:void(document.getElementById('miniLoginFrame').src='" + src + "')");
				}
			}*/
		}
	}
	protected void onDestroy() {
		super.onDestroy();
		if (mXLWebView != null) {
			mXLWebView.clearCache(true);
			mXLWebView.clearHistory();
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
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		// TODO Auto-generated method stub
		if(item.getItemId() == android.R.id.home){
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
}
