package com.xunlei.speedup.util;

public class Constant {

    public static final String CC_TAG = "lixianfree";
    
    //Handler msg.what消息 
    public static final int MSG_PUSH_TASK = 102;
    
    

    //网络类型相关
    public static final String NETWORK_NONE ="none";
    public static final String NETWORK_WIFI ="wifi";
    public static final String NETWORK_2G_WAP ="2g_wap";
    public static final String NETWORK_2G_NET ="2g_net";
    public static final String NETWORK_3G ="3g";
    public static final String NETWORK_4G ="4g";
    public static final String NETWORK_OTHER ="other";
    public static final int NETWORK_CLASS_UNKNOWN = 0;
    public static final int NETWORK_CLASS_2G = 1;
    public static final int NETWORK_CLASS_3G = 2;
    public static final int NETWORK_CLASS_4G = 3;
    
    
    //判断是否有更新
    public static  boolean isUpdate = false;
    
    public static final String PREFERENCES = "xl_speedup_sp";
}
