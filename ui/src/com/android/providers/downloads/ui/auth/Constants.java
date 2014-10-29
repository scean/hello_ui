package com.android.providers.downloads.ui.auth;

public class Constants {

	public static final int CURRENT_MIUI_V = 6;
	public static final int REQUESTCODE_TOKEN = 10001;
	public static final int REQUESTCODE_CODE = 10002;
	public static final int REQUESTCODE_XL_TOKEN = 10003;

	// 流程类型 真/伪
	public static final int AUTH_TYPE_FAKE = 1;
	public static final int AUTH_TYPE_REAL = 0;

	// 请求后台类型 http/web
	public static final int REQUEST_TYPE_HTTP = 1;
	public static final int REQUEST_TYPE_WEB = 0;

	// 请求页面类型 0 表示PC版页面 ,1 表示移动版页面,3 表示定制版页面
	public static final int WAP_TYPE_PC = 0;
	public static final int WAP_TYPE_MOBILE = 1;
	public static final int WAP_TYPE_CUSTOM = 3;
	public static final int WAP_TYPE_V6 = 5;

	// 0 表示其他或不填则 302 跳转 ,1 表示 视为http调用 json格式返回
	public static final int INVISABLE_TYPE_OTHER = 0;
	public static final int INVISABLE_TYPE_JSON = 1;

	public static final int FORCE_LOGIN_FALSE = 0;
	public static final int FORCE_LOGIN_TRUE = 1;

	public static final String USER_PROFILE_PATH = "/user/profile";
	public static final String USER_RELATION_PATH = "/user/relation";

}
