package com.android.providers.downloads.ui.auth;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.NameValuePair;

import android.content.Context;

import com.xiaomi.account.openauth.utils.Network;

public class RequestXLAuthJob {

	private String URL = "https://open-api-auth.xunlei.com/platform?m=BindOauth&op=recieveCode";

	/**
	 * 请求迅雷后台
	 * 
	 * @param code
	 * @param authType
	 *            1 表示走伪授权流程 0 表示走真授权流程
	 * @param wap
	 *            0 表示PC版页面 1 表示移动版页面
	 * @param redirectUri
	 *            可以指定回调地址 可选
	 * @param invisible
	 *            1 表示 视为http调用 json格式返回 ，其他或不填则 302 跳转
	 * @param responseHandler
	 *            请求回调监听器
	 */
	public String getXLAuth(Context context, String code, int authType, int wap, String redirectUri, int invisible, int forceLogin) {
		try {
			URL += "&code=" + code + "&fake=" + authType + "&wap=" + wap + "&redirect_uri=" + redirectUri + "&invisible=" + invisible + "&force_login=" + forceLogin;
			return Network.doHttpPost(context, URL, new ArrayList<NameValuePair>());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
