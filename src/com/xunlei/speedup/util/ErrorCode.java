package com.xunlei.speedup.util;

public class ErrorCode {
    
    //客户端异常错误码(自定义)
    public static final int ERR_NO_NETWORK = -20100;//无网络
    public static final int ERR_DEFAULT = -20000;//ClientProtocolException
    public static final int ERR_CLP_EXCEPTION = -20001;//ClientProtocolException
    public static final int ERR_SOCKET_TIMEOUT_EXCEPTION = -20002;//SocketTimeoutException 超时
    public static final int ERR_PARSE_EXCEPTION = -20003;//ParseException
    public static final int ERR_UNE_EXCEPTION = -20004;//UnsupportedEncodingException
    public static final int ERR_ILA_EXCEPTION = -20005;//IllegalArgumentException
    public static final int ERR_JSON_EXCEPTION = -20006;//JSONException
    public static final int ERR_STRIO_EXCEPTION = -20007;//JSONException
    public static final int ERR_INVALID_JSON_FIELD = -20008;//JSONException
    public static final int ERR_JAVA_EXCEPTION = -20009;//java代码异常抛出的错误码
    public static final int ERR_IO_EXCEPTION = -20010;//SocketTimeoutException
    public static final int ERR_SOCKET_EXCEPTION = -20011;//SocketException
    public static final int ERR_UNKNOW_HOST_EXCEPTION = -20012;//UnknownHostException
    public static final int ERR_BUILD_JSON_EXCEPTION = -20013;//JSONException
    
    
    // 服务器端错误码
    public static final int SERVER_ERR_KOUSHICHANG = 1;//扣除时长失败 
    public static final int SERVER_ERR_LIXIANRENZHENG = 2;//离线认证失败
    public static final int SERVER_ERR_TIMEOUT = 3;//服务器超时 
    public static final int SERVER_ERR_VERICODE = 4;//校验码错误*
    public static final int SERVER_ERR_SQL = 5;//访问数据库失败 
    public static final int SERVER_ERR_NORECORD = 6;//无数据记录
    public static final int SERVER_ERR_ALREADY_SPEED = 7;//此任务已经加速
    public static final int SERVER_ERR_CONNECT_FAIL = 8;//push任务时，没有xl7长连接
}
