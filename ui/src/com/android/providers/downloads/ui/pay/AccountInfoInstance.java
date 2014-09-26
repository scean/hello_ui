package com.android.providers.downloads.ui.pay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.android.providers.downloads.ui.notification.AccountLogic;
import com.android.providers.downloads.ui.pay.util.MyVolley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.android.providers.downloads.ui.DownloadList;
import com.android.providers.downloads.ui.DownloadUtils;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.ConfigJSInfo;
import com.android.providers.downloads.ui.pay.ConfigJSInstance.SpeedupJSInfo;
import com.android.providers.downloads.ui.pay.MiBiPay.PayListener;
import com.android.providers.downloads.ui.pay.util.XLUtil;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.Request.Method;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class AccountInfoInstance {
    static String TAG=XLUtil.getTagString(AccountInfoInstance.class);
    final static String mAccountUrl="http://developer.open-api-auth.xunlei.com/get_user_info?";
    final static String mFlowUrl="http://openapi.service.cdn.vip.xunlei.com/mipay/queryFlow/?";
    final static String mAddFlowUrl="http://openapi.service.cdn.vip.xunlei.com/mipay/addFlow/?";
    final static String ACCOUNTSRC_PREFERENCE="xl_accountinfo_src";
    final static String FLOWSRC_PREFERENCE="xl_flow_src";
    private final int HTTP_SOCKET_TIMEOUT_MS =20000;
    //    private AccountListener mListener;
    private HashSet<AccountListener> mListeners;
    private RequestQueue     mRequestQueue;
    private AccountInfo     mAccountInfo;
    private FlowInfo        mFlowInfo;
    private String            mToken;
    private Context mContext;

    public void AccountInfoInstance(){
    }
    private static AccountInfoInstance instance;
    public synchronized static AccountInfoInstance getInstance(Context context,String token){
        if(instance ==null){
            instance =new AccountInfoInstance();
            instance.initWithContext(context, token);
        }else{
            instance.HaveInitWith(context, token);
        }
        return instance;
    }
    public void  HaveInitWith(Context context,String token){

        mContext =context;

        mToken =token;
        if(!XLUtil.isNullOrEmpty(mToken)){

        }else{
            clearAccountAndFlowDefaultInfo();
        }
    }
    public int  initWithContext(Context context,String token){
	    String methodName =TAG+"_"+"initWithContext";
        int res =0;
        mContext =context;
        mToken =token;
        mListeners = new HashSet<AccountInfoInstance.AccountListener>();
        if(XLUtil.isNullOrEmpty(mToken)){
            clearAccountAndFlowDefaultInfo();
        }
        getDefaultAccountInfo();
        getDefaultFlowInfo();
        mRequestQueue = MyVolley.getInstance(context).getRequestQueue();
        XLUtil.logDebug(TAG, "account init token="+mToken);
        if(!XLUtil.isNullOrEmpty(mToken)){
            RequestAccountInfo(mToken);
	        XLUtil.logDebug(TAG,"AccountInfoInstance initWithContext request flow ");
            RequestFlowInfo(mToken,methodName);
        }
        return res;
    }

    public abstract interface AccountListener{
        public int OnAccountRtn(int ret,AccountInfo account,String msg);
        public int OnFlowRtn(int ret,FlowInfo  flow,String msg);
        public int OnAddFlowRtn(int ret,AddFlowInfo addflow,String msg);
    }
    public synchronized void setAccountListener(AccountListener listener){
        mListeners.add(listener);
    }
    public synchronized void removeAccountListener(AccountListener listener){
        mListeners.remove(listener);
    }
    public void setToken(String token){
        mToken =token;
        if(XLUtil.isNullOrEmpty(mToken)){
            clearAccountAndFlowDefaultInfo();
        }
    }
	public boolean isFakeAuthed(){
		boolean res =true;
		if(mAccountInfo != null && mAccountInfo.result ==200 ){
			Long tmp =Long.valueOf(mAccountInfo.uid);
			if(tmp < 1000000000){
				res =false;
			}
		}
		return  res;
	}
    public void uninit(){
        mContext =null;
    }
    public AccountInfo getAccountInfo(){
        if(mContext ==null)
            return getFailAccountInfo();
        return mAccountInfo;
    }
    public FlowInfo getFlowInfo(){
        if(mContext ==null)
            return getFailFlowInfo();
        return mFlowInfo;
    }
    public int requestAccount(){
        int res =0;
        if(mContext ==null){
            res =-1;
            return res;
        }
        if(XLUtil.isNullOrEmpty(mToken)){
            res =-2;
            return res;
        }
        RequestAccountInfo(mToken);
        return res;
    }
    public int requestFlow(String refer){
        int res =0;
        if(mContext ==null){
            res =-1;
            return res;
        }
        if(XLUtil.isNullOrEmpty(mToken)){
            res =-2;
            return res;
        }
        RequestFlowInfo(mToken,refer);
        return res;
    }
    private void clearAccountAndFlowDefaultInfo(){
        setDefaultAccountInfo(getFailAccountInfo(), "");
        setDefaultFlowInfo(getFlowInfo(), "");
    }
    private void getDefaultAccountInfo(){
        String src =XLUtil.getStringPreferences(mContext, ACCOUNTSRC_PREFERENCE);

        mAccountInfo =accountjsonparse(src);
        if(mAccountInfo ==null){
            mAccountInfo =getFailAccountInfo();
        }
    }
    private void setDefaultAccountInfo(AccountInfo jsinfo,String src){
        mAccountInfo =jsinfo;
        XLUtil.saveStringPreferences(mContext, ACCOUNTSRC_PREFERENCE, src);
    }
    private void getDefaultFlowInfo(){
        String src =XLUtil.getStringPreferences(mContext, FLOWSRC_PREFERENCE);

        mFlowInfo =flowjsonparse(src);
        if(mFlowInfo ==null){
            mFlowInfo =getFailFlowInfo();
        }
    }
    private void setDefaultFlowInfo(FlowInfo jsinfo,String src){
        mFlowInfo =jsinfo;
        XLUtil.saveStringPreferences(mContext, FLOWSRC_PREFERENCE, src);
    }
    //-------------Account request ---------------------------------
    public class AccountInfo{
        public int result;
        public String msg;
        public String uid;
        public String nickname;
        public String gender;     // u:unknown  f:girl m:boy
        public String figureurl1;   //headthumb url 50*50
        public String figureurl2;   //headthumb url 100*100
	    public String figureurl3;   //headthumb url 300*300
        public int    isvip;         //0:no vip  1: vip
        public int    level;          // vip level
        public String  expire ;        //vip expire time
        public int     fake;           //0:ture  1:false
        public int     xl_type;		// 0: normal 1. auto
        
        public boolean isVip() {
        	return isvip == 1;
        }
        
        public boolean isFake() {
        	return fake == 1;
        }
        
        public boolean isAuto() {
        	return xl_type == 1;
        }
    }
    AccountInfo getFailAccountInfo(){
        AccountInfo info =new AccountInfo();
        info.result =-1000;
        return info;
    }
    AccountInfo accountjsonparse(String str) {
	    AccountInfo result = getFailAccountInfo();

        if(XLUtil.isNullOrEmpty(str)) {
	        return result;
        }

            XLUtil.logDebug(TAG, "account json ="+str);
            try{
                JSONObject jo =new JSONObject(str);
                result.result =jo.getInt("result");
                result.msg      =jo.getString("msg");
                result.uid      =jo.getString("uid");
                result.nickname=jo.getString("nickname");
                result.gender =jo.getString("gender");
                result.figureurl1=jo.getString("figureurl1");
                result.figureurl2=jo.getString("figureurl2");
	            result.figureurl3=jo.getString("figureurl3");
                result.isvip   =jo.getInt("isvip");
                result.level   =jo.getInt("level");
                result.expire  =jo.getString("expire");
                result.fake    =jo.getInt("fake");
                result.xl_type =jo.getInt("xl_type");
            }catch(JSONException e) {
                e.printStackTrace();
            }
            return result;
    }
    private int RequestAccountInfo(String token){
            int res =0;

            String requrl=mAccountUrl+
                          "scope="+"get_user_info"+
                          "&access_token="+token+
		                  "&version="+XLUtil.getSelfAppVersion(mContext)+
                          "&ct="+XLUtil.getCurrentUnixMillTime();

            XLUtil.logDebug(TAG, "request aacount="+requrl);

            StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

                @Override
                public void onResponse(String response) {
                    // TODO Auto-generated method stub
                    AccountInfo res =accountjsonparse(response);
                    XLUtil.logDebug(TAG, "account json "+response);
	                if(res.result == 400){
		                XLUtil.askRequestToken(mContext);

	                }
                    if(res.result ==200){
                        setDefaultAccountInfo(res, response);
                    }
                    for(AccountListener listener : mListeners){
                        listener.OnAccountRtn(0, res,null);
                    }
                }
            }, new ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO Auto-generated method stub
                    XLUtil.logError(TAG, error.toString());
                    for(AccountListener listener : mListeners){
                        listener.OnAccountRtn(-1, getFailAccountInfo(),error.toString());
                    }

                }
            });
            req.setRetryPolicy(new DefaultRetryPolicy(
                    HTTP_SOCKET_TIMEOUT_MS,
		            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(req);
            return res;
    }
    //-----------------get flow---------------------------------
    public class FlowInfo{
          public int  ret;
          public int need_refresh;
          public int need_auth;
          public String total_capacity ;                    //B,K,M,G,T
          public String used_capacity  ;                    //B,K,M,G,T
          public long org_total_capacity;                    //B
          public long org_used_capacity;                    //B
          public int    autosend       ;                    //本月自动赠送流量 1 成功；0 失败
	      public int    historysend  = -1  ;                    //历史是否领取过流量 >1 非首月领取 ；  =1 首月领取； =0 未领取

    }
    FlowInfo getFailFlowInfo(){
        FlowInfo info =new FlowInfo();
        info.ret =-1000;
        return info;
    }
    FlowInfo flowjsonparse(String str){
	    FlowInfo result = getFailFlowInfo();
        if(XLUtil.isNullOrEmpty(str))
                return result;

        XLUtil.logDebug(TAG, "flow json ="+str);

            try{
                JSONObject jo =new JSONObject(str);

                result.ret              =jo.getInt("ret");
                result.need_refresh   =jo.getInt("need_refresh");
                result.need_auth      =jo.getInt("need_auth");
                result.total_capacity =jo.getString("total_capacity");
                result.used_capacity  =jo.getString("used_capacity");
                result.org_total_capacity=jo.getLong("org_total_capacity");
                result.org_used_capacity=jo.getLong("org_used_capacity");
                result.autosend         =jo.getInt("autosend");
	            result.historysend      =jo.getInt("historysend");
            }catch(JSONException e){
                e.printStackTrace();

            }

            return result;
    }
    private int RequestFlowInfo(String token,String refer){
            int res =0;

            String requrl=mFlowUrl+
                          "access_token="+token+
		                  "&version="+XLUtil.getSelfAppVersion(mContext)+
		                  "&refer="+refer+
		                  "&callback="+XLUtil.getCurrentUnixMillTime();
            XLUtil.logDebug(TAG, "request flow="+requrl);
            StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

                @Override
                public void onResponse(String response) {
                    // TODO Auto-generated method stub
                    XLUtil.logDebug(TAG, "flow json"+response);
	                String pair1,pair2="";
	                try {
		                pair1 = response.substring(response.indexOf("(") + 1);
		                pair2 = pair1.substring(0, pair1.lastIndexOf(")"));

	                }catch (Exception e){
		                XLUtil.printStackTrace(e);
	                }
                    FlowInfo res =flowjsonparse(pair2);

                    if(res !=null){
	                    if(res.need_refresh ==1){

			                    XLUtil.askRequestToken(mContext);
	                    }
                        setDefaultFlowInfo(res, pair2);
                    }
                    for(AccountListener listener : mListeners){
                        listener.OnFlowRtn(0, res,null);
                    }
                }
            }, new ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO Auto-generated method stub
                    XLUtil.logError(TAG, error.toString());
                    for(AccountListener listener : mListeners){
                        listener.OnFlowRtn(-1,getFailFlowInfo(),error.toString());
                    }
                }
            });
            req.setRetryPolicy(new DefaultRetryPolicy(
                    HTTP_SOCKET_TIMEOUT_MS,
		            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(req);
            return res;
    }
    //-----------------add flow---------------------------------
    public class AddFlowInfo{
          public int ret;
          public int need_refresh;
          public int need_auth;
          public String msg;
    }
    AddFlowInfo getFailAddFlowInfo(){
        AddFlowInfo info =new AddFlowInfo();
        info.ret =-1000;
        info.msg ="null";
        return info;
    }
    AddFlowInfo addflowjsonparse(String str){
	    AddFlowInfo result = getFailAddFlowInfo();
        if(XLUtil.isNullOrEmpty(str))
                return result;


            try{
                JSONObject jo =new JSONObject(str);
                result =new AddFlowInfo();
                result.ret              =jo.getInt("ret");
                result.need_refresh   =jo.getInt("need_refresh");
                result.need_auth      =jo.getInt("need_auth");
                result.msg               =jo.getString("msg");
            }catch(JSONException e){
                e.printStackTrace();
            }

            return result;
    }
    public int RequestAddFlowInfo(String token, final boolean isTrace){
            int res =0;
            final int flag;
            if (AccountLogic.getInstance().getAddFlowReason() == AccountLogic.REQUEST_ADD_FLOW_REASON_NOTIFY) {
            	flag = 0;
            }
            else 
            	flag = 1;
           // XLUtil.logDebug("DownloadUtils.Stat.Behavior", "flag in get_flow = " + flag);
            if (isTrace)
            	DownloadUtils.trackBehaviorEvent(mContext, "get_flow", flag, 0);
            String requrl=mAddFlowUrl+
                          "access_token="+token+
		                  "&version="+XLUtil.getSelfAppVersion(mContext)+
		                  "&callback="+XLUtil.getCurrentUnixMillTime();
            XLUtil.logDebug(TAG, "request add flow"+requrl);
            StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

                @Override
                public void onResponse(String response) {
                    // TODO Auto-generated method stub
                    XLUtil.logDebug(TAG, "add flow json"+response);
	                String pair1,pair2="";
	                try {
		                pair1 = response.substring(response.indexOf("(") + 1);
		                pair2 = pair1.substring(0, pair1.lastIndexOf(")"));

	                }catch (Exception e){
		                XLUtil.printStackTrace(e);
	                }
                    AddFlowInfo res =addflowjsonparse(pair2);
	                if(res.need_refresh ==1){

		                XLUtil.askRequestToken(mContext);
	                }
                    for(AccountListener listener : mListeners){
                        listener.OnAddFlowRtn(0, res,null);
                    }
                    AccountLogic.getInstance().setAddFlowReason(AccountLogic.REQUEST_ADD_FLOW_REASON_UNKNOWN);
                   // XLUtil.logDebug("DownloadUtils.Stat.Behavior", "flag in get_flow_succ = " + flag);
                    if ((res.ret == 0) && isTrace)
                    	DownloadUtils.trackBehaviorEvent(mContext, "get_flow_succ", flag, 0);
                }
            }, new ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO Auto-generated method stub
                    XLUtil.logError(TAG, error.toString());
                    for(AccountListener listener : mListeners){
                        listener.OnAddFlowRtn(-1, getFailAddFlowInfo(),error.toString());
                    }

                }
            });
            req.setRetryPolicy(new DefaultRetryPolicy(
                    HTTP_SOCKET_TIMEOUT_MS,
		            5,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(req);
            return res;
    }
}
