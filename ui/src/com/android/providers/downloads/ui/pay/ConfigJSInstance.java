package com.android.providers.downloads.ui.pay;

import java.util.ArrayList;
import java.util.Currency;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.view.WindowManager;

import com.android.providers.downloads.ui.app.AppConfig;
import com.android.providers.downloads.ui.utils.MyVolley;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.Request.Method;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class ConfigJSInstance {
    private static String TAG = ConfigJSInstance.class.getSimpleName();

    final static String mConfigJsUrl="http://pay.vip.xunlei.com/mi/js/app/3.0.js";
    final static String mSpeedupJSUrl="http://pay.vip.xunlei.com/mi/js/speedup/1.0.js";
    final static String CONFIGSRC_PREFERENCE="xl_configjsinfo_src";
    final static String SPEEDUPSRC_PREFERENCE="xl_speddjsinfo_src";
    final static String OPTDOWNLOAD_PREFERENCE="xl_optdownload_flag";
    private final int HTTP_SOCKET_TIMEOUT_MS =20000;
    private RequestQueue     mRequestQueue;
    private ConfigJSInfo     mConfigJSInfo;
    private SpeedupJSInfo     mSpeedupJSInfo;
    private Context mContext;
    private Long    mLastTime;
    private static String miVersion = "mi3";
    
    public ConfigJSInstance() {
        // TODO Auto-generated constructor stub
    }
    private static ConfigJSInstance instance;
    public  synchronized static ConfigJSInstance getInstance(Context context){

            if (instance ==null) {
                instance = new ConfigJSInstance();
                instance.initWithContext(context);
            } else {
            }
        return instance;
    }
    public void  HaveInitWith(Context context) {
        if(XLUtil.getCurrentUnixTime() - mLastTime >60 *60 *24){
            RequestConfigJS();
            RequestSpeedupJS();
            mLastTime =XLUtil.getCurrentUnixTime();
        }
    }
    public int  initWithContext(Context context) {
        int res =0;
        mContext =context;
        getDefaultSpeedupInfo();
        getDefaultInfo();
        mRequestQueue = MyVolley.getInstance(context).getRequestQueue();
        RequestSpeedupJS();
        RequestConfigJS();

        return res;
    }

    public void uninit() {
        mContext =null;
    }
    public ConfigJSInfo getConfigJSInfo() {
        if(mContext ==null || mConfigJSInfo ==null) {
	        getDefaultInfo();
	        return mConfigJSInfo;
        }
        return mConfigJSInfo;
    }
    public SpeedupJSInfo getSpeedJSInfo() {
        if(mContext == null || mSpeedupJSInfo == null) {
	        getDefaultSpeedupInfo();
	        return  mSpeedupJSInfo;
        }
        return mSpeedupJSInfo;
    }
    //-------------------choose use download-------------------------
    public static final int Opt_Forbidden =1;
    public static final int Opt_WifiOnly  =2;
    public static final int Opt_NetALL  =3;

    public String getStrFromOptFlag(int flag){
        String str ="";
        switch(flag){
            case Opt_Forbidden: str ="禁用"; break;
            case Opt_WifiOnly: str ="仅wifi下生效";    break;
            case Opt_NetALL: str ="所有网络";    break;
        }
        return str;
    }
    public int  setUseOpt(int flag) {
        if(mContext ==null){
            return -1;
        }
        XLUtil.saveLongPreferences(mContext, OPTDOWNLOAD_PREFERENCE, flag);
        return 0;
    }
    public  int getUseOpt(){
        if(mContext ==null){
            return -1;
        }
        int res =0;
        res =(int)XLUtil.getLongPreferences(mContext, OPTDOWNLOAD_PREFERENCE);
        res = (res ==0) ? 3 :res;
        return res;
    }

    //-----------private method        
    private void getDefaultInfo(){
        String src =XLUtil.getStringPreferences(mContext, CONFIGSRC_PREFERENCE);
        if(XLUtil.isNullOrEmpty(src)){
            src =DefaultConfigJSSrc;
        }
        mConfigJSInfo =configjsonparse(src);
    }
    private void setDefaultInfo(ConfigJSInfo jsinfo,String src){
        mConfigJSInfo =jsinfo;
        XLUtil.saveStringPreferences(mContext, CONFIGSRC_PREFERENCE, src);
    }
    private void getDefaultSpeedupInfo(){
        String src =XLUtil.getStringPreferences(mContext, SPEEDUPSRC_PREFERENCE);
        if(XLUtil.isNullOrEmpty(src)){
            src =DefaultSpeedupJSSrc;
        }
        mSpeedupJSInfo =speedupjsonparse(src);
    }
    private void setDefaultSpeedupInfo(SpeedupJSInfo jsinfo,String src){
        mSpeedupJSInfo =jsinfo;
        XLUtil.saveStringPreferences(mContext, SPEEDUPSRC_PREFERENCE, src);
    }
    //----------------getConfigJS----------------------------    
    final String DefaultConfigJSSrc="{\"speed_page\":{\"openvip\":\"500\\u4e07\\u8fc5\\u96f7\\u4f1a\\u5458\\u7684\\u9009\\u62e9\\\\n\\u4e13\\u5c5e\\u52a0\\u901f\\u7279\\u6743\\uff0c\\u7545\\u4eab\\u5feb\\u4eba\\u4e00\\u6b65\\u7684\\u653e\\u8086\",\"openvip_flow_before_used\":\"\\u672c\\u6708\\u52a0\\u901f\\u7279\\u6743\\u5373\\u5c06\\u6682\\u505c\\\\n\\u5f00\\u901a\\u8fc5\\u96f7\\u4f1a\\u5458\\u7acb\\u4eab\\u65e0\\u9650\\u5236\\u52a0\\u901f\\u7279\\u6743\",\"openvip_flow_used\":\"\\u672c\\u6708\\u52a0\\u901f\\u7279\\u6743\\u5df2\\u7ecf\\u6682\\u505c\\\\n\\u5f00\\u901a\\u8fc5\\u96f7\\u4f1a\\u5458\\u7acb\\u4eab\\u65e0\\u9650\\u5236\\u52a0\\u901f\\u7279\\u6743\",\"openvip_expire\":\"\\u60a8\\u7684\\u8fc5\\u96f7\\u4f1a\\u5458\\u5373\\u5c06\\u5230\\u671f\\\\n\\u7acb\\u5373\\u7eed\\u8d39\\u5c0a\\u4eab95\\u6298\\u4f18\\u60e0\",\"openvip_expire_today\":\"\\u60a8\\u7684\\u8fc5\\u96f7\\u4f1a\\u5458\\u4eca\\u5929\\u5c06\\u5230\\u671f\\\\n\\u7acb\\u5373\\u7eed\\u8d39\\u5c0a\\u4eab95\\u6298\\u4f18\\u60e0\",\"flow_send_tips\":\"\\u5df2\\u81ea\\u52a8\\u4e3a\\u60a8\\u9886\\u53d6\\u672c\\u6708\\u514d\\u8d39\\u8fc5\\u96f7\\u4f1a\\u5458\\u52a0\\u901f\\u670d\\u52a1\"},\"flow_guide\":{\"bar_before_used\":{\"percent_config\":[80],\"msg\":\"\\u672c\\u6708\\u52a0\\u901f\\u7279\\u6743\\u5373\\u5c06\\u6682\\u505c\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5>>\"},\"bar_used\":{\"msg\":\"\\u672c\\u6708\\u52a0\\u901f\\u7279\\u6743\\u5df2\\u6682\\u505c\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5>>\"}},\"speed_wakeup\":{\"bar_flow\":{\"msg\":\"\\u60a8\\u7684\\u5269\\u4f59\\u52a0\\u901f\\u6570\\u636e\\u91cf\\u4e3a%dMB\\uff0c\\u5feb\\u6765\\u52a0\\u901f\\u8bd5\\u8bd5\\u3002\",\"btn\":\"\\u52a0\\u901f\"}},\"vip_guide\":{\"bar_before_expire\":{\"expire_config\":[7,4,1],\"msg\":\"\\u60a8\\u7684\\u8fc5\\u96f7\\u4f1a\\u5458\\u5373\\u5c06\\u5230\\u671f\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5\\u3002\"},\"bar_expire\":{\"msg\":\"\\u60a8\\u7684\\u8fc5\\u96f7\\u4f1a\\u5458\\u4eca\\u5929\\u5c06\\u5230\\u671f\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5\\u3002\"}},\"flow_notice\":{\"unlogin\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u767b\\u5f55\\u5c0f\\u7c73\\u5e10\\u53f7\\u514d\\u8d39\\u9886\\u53d6\\u52a0\\u901f\\u670d\\u52a1\",\"b\":\"\\u9886\\u53d6\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u767b\\u5f55\\u5c0f\\u7c73\\u8d26\\u53f7\\u514d\\u8d39\\u9886\\u53d6\\u4f1a\\u5458\\u52a0\\u901f\\u670d\\u52a1\",\"b\":\"\\u9886\\u53d6\"}},\"login_bind_novip\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u5c0f\\u7c73\\u7528\\u6237\\u514d\\u8d39\\u9886\\u53d6\\u52a0\\u901f\\u670d\\u52a1\",\"b\":\"\\u9886\\u53d6\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u5c0f\\u7c73\\u7528\\u6237\\u514d\\u8d39\\u9886\\u53d6\\u4f1a\\u5458\\u52a0\\u901f\\u670d\\u52a1\",\"b\":\"\\u9886\\u53d6\"}},\"login_bind_vip\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u5df2\\u4e3a\\u60a8\\u5f00\\u901a\\u4f1a\\u5458\\u52a0\\u901f\\u670d\\u52a1\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\",\"s\":\"\\u5df2\\u4e3a\\u60a8\\u5f00\\u901a\\u4f1a\\u5458\\u52a0\\u901f\\u670d\\u52a1\\uff0c\\u4e0b\\u8f7d\\u5feb\\u4eba\\u4e00\\u6b65\"}},\"nextmonth\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u9001\\u798f\\u5229\",\"s\":\"\\u60a8\\u5df2\\u83b7\\u5f97\\u8fc5\\u96f7300M\\u9ad8\\u901f\\u6d41\\u91cf\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u9001\\u798f\\u5229\",\"s\":\"\\u60a8\\u5df2\\u514d\\u8d39\\u83b7\\u5f97\\u8fc5\\u96f7300M\\u9ad8\\u901f\\u6d41\\u91cf\"}}},\"flow_use_up\":{\"before_use_up\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\\u5373\\u5c06\\u6682\\u505c\\u5566\",\"s\":\"\\u5f00\\u901a\\u52a0\\u901f\\u670d\\u52a1\\u7545\\u4eab\\u52a0\\u901f\\u7279\\u6743>>\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\\u5373\\u5c06\\u6682\\u505c\\u5566\",\"s\":\"\\u5f00\\u901a\\u52a0\\u901f\\u670d\\u52a1\\u7545\\u4eab\\u52a0\\u901f\\u7279\\u6743\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5\"}},\"use_up\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\\u5df2\\u6682\\u505c\\u5566\",\"s\":\"\\u5f00\\u901a\\u52a0\\u901f\\u670d\\u52a1\\u7545\\u4eab\\u52a0\\u901f\\u7279\\u6743>>\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u52a0\\u901f\\u7279\\u6743\\u5df2\\u6682\\u505c\\u5566\",\"s\":\"\\u5f00\\u901a\\u52a0\\u901f\\u670d\\u52a1\\u7545\\u4eab\\u52a0\\u901f\\u7279\\u6743\\uff0c\\u70b9\\u51fb\\u67e5\\u770b\\u8be6\\u60c5\"}}},\"vip_expire\":{\"before_expire\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u4f1a\\u5458\\u5373\\u5c06\\u5230\\u671f\\u5566\",\"s\":\"\\u7eed\\u8d39\\u8fc5\\u96f7\\u4f1a\\u5458,\\u8ba9\\u4e0b\\u8f7d\\u98de\\u8d77\\u6765>>\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u4f1a\\u5458\\u5373\\u5c06\\u5230\\u671f\\u5566\",\"s\":\"\\u7eed\\u8d39\\u8fc5\\u96f7\\u4f1a\\u5458\\uff0c\\u8ba9\\u4e0b\\u8f7d\\u98de\\u8d77\\u6765>>\"}},\"expire\":{\"mi2\":{\"t\":\"\\u8fc5\\u96f7\\u4f1a\\u5458\\u4eca\\u5929\\u5230\\u671f\\u5566\",\"s\":\"\\u8fc5\\u96f7\\u4f1a\\u5458\\u9ad8\\u901f\\u4e0b\\u8f7d\\u505c\\u4e0d\\u4e0b\\u6765>>\"},\"mi3\":{\"t\":\"\\u8fc5\\u96f7\\u4f1a\\u5458\\u4eca\\u5929\\u5230\\u671f\",\"s\":\"\\u7eed\\u8d39\\u8fc5\\u96f7\\u4f1a\\u5458\\uff0c\\u9ad8\\u901f\\u4e0b\\u8f7d\\u6839\\u672c\\u505c\\u4e0d\\u4e0b\\u6765>>\"}}}}";
    public class ConfigJSInfo{
    	
    	public String speed_page_openvip;
        public String speed_page_openvip_flow_before_used;
        public String speed_page_openvip_flow_used;
        public String speed_page_openvip_expire;
        public String speed_page_openvip_expire_today;
    	
//       场景1未登录
        public String speed_guide_bar_unlogin_scene1_title;
        public String speed_guide_bar_unlogin_scene1_content;
        public String speed_guide_bar_unlogin_scene1_btn;
//        场景1登录非会员
        public String speed_guide_bar_login_unbind_scene1_title;
        public String speed_guide_bar_login_unbind_scene1_content;
        public String speed_guide_bar_login_unbind_scene1_btn;
//        场景1登录会员
        public String speed_guide_bar_login_bind_scene1_title;
        public String speed_guide_bar_login_bind_scene1_content;
        
//      次月领取通知
        public String flow_gift_next_month_title;
        public String flow_gift_next_month_content;
        
//      会员今天到期
        public String vip_guide_bar_expire_title;
        public String vip_guide_bar_expire_content;
        
//      会员即将到期
        public String vip_guide_bar_before_expire_title;
        public String vip_guide_bar_before_expire_content;
		public ArrayList<Integer>  flow_guide_bar_before_used_percent_config;     //提醒流量时间点  例如 50/100  75/100
        
//      赠送流量即将用完
        public String gift_flow_before_use_up_title;
        public String gift_flow_before_use_up_content;
       public ArrayList<Integer> vip_guide_bar_before_expire_expire_config;       //提醒到期天时间点  例如 7 4 1 天
//        赠送流量已用完
        public String gift_flow_use_up_tile;
        public String gift_flow_use_up_content;
    }
    ConfigJSInfo configjsonparse(String str){

	    ConfigJSInfo result=null;
        if(XLUtil.isNullOrEmpty(str) && mContext == null)
                return null;


            try{
                JSONObject jo =new JSONObject(str);
                
                if (((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getWidth() >=1080) {
                	miVersion = "mi3";
				}
                else {
                	miVersion = "mi2";
				}

                result =new ConfigJSInfo();
                result.flow_guide_bar_before_used_percent_config =new ArrayList<Integer>();
	            result.vip_guide_bar_before_expire_expire_config = new ArrayList<Integer>();
	            
	            JSONObject jospeedguide=jo.getJSONObject("speed_page");
	            result.speed_page_openvip = jospeedguide.getString("openvip");
	            result.speed_page_openvip_flow_before_used=jospeedguide.getString("openvip_flow_before_used");
                result.speed_page_openvip_flow_used=jospeedguide.getString("openvip_flow_used");
                result.speed_page_openvip_expire=jospeedguide.getString("openvip_expire");
                result.speed_page_openvip_expire_today =jospeedguide.getString("openvip_expire_today");
                
//                场景1提示
                jospeedguide=jo.getJSONObject("flow_notice");
//              提示-->登录小米帐号免费领取加速服务
                JSONObject tmp=jospeedguide.getJSONObject("unlogin");
                JSONObject flow=tmp.getJSONObject(miVersion);
                result.speed_guide_bar_unlogin_scene1_title =flow.getString("t");
                result.speed_guide_bar_unlogin_scene1_content =flow.getString("s");
                result.speed_guide_bar_unlogin_scene1_btn =flow.getString("b");
                
//              提示-->小米用户免费领取会员加速服务
                tmp=jospeedguide.getJSONObject("login_bind_novip");
                flow=tmp.getJSONObject(miVersion);
                result.speed_guide_bar_login_unbind_scene1_title =flow.getString("t");
                result.speed_guide_bar_login_unbind_scene1_content =flow.getString("s");
                result.speed_guide_bar_login_unbind_scene1_btn =flow.getString("b");
                
//              提示-->已为您开通会员加速服务，下载快人一步
                tmp=jospeedguide.getJSONObject("login_bind_vip");
                flow=tmp.getJSONObject(miVersion);
                result.speed_guide_bar_login_bind_scene1_title =flow.getString("t");
                result.speed_guide_bar_login_bind_scene1_content =flow.getString("s");
                
//              提示-->已为您开通会员加速服务，下载快人一步
                tmp=jospeedguide.getJSONObject("nextmonth");
                flow=tmp.getJSONObject(miVersion);
                result.flow_gift_next_month_title = flow.getString("t");
                result.flow_gift_next_month_content = flow.getString("s");
                

                jospeedguide=jo.getJSONObject("flow_guide");
                tmp=jospeedguide.getJSONObject("bar_before_used");
                JSONArray tmparr =tmp.getJSONArray("percent_config");
                for(int i=0;i<tmparr.length();i++){
                    result.flow_guide_bar_before_used_percent_config.add(tmparr.getInt(i));
                }
                
                jospeedguide=jo.getJSONObject("vip_guide");
                tmp=jospeedguide.getJSONObject("bar_before_expire");
                tmparr =tmp.getJSONArray("expire_config");
                for(int i=0;i<tmparr.length();i++){
                    result.vip_guide_bar_before_expire_expire_config.add(tmparr.getInt(i));
                }
                
//              赠送流量即将用完
                jospeedguide=jo.getJSONObject("flow_use_up");
                tmp=jospeedguide.getJSONObject("before_use_up");
                flow=tmp.getJSONObject(miVersion);
                result.gift_flow_before_use_up_title = flow.getString("t");
                result.gift_flow_before_use_up_content = flow.getString("s");
                
//                赠送流量已用完
                tmp=jospeedguide.getJSONObject("use_up");
                flow=tmp.getJSONObject(miVersion);
                result.gift_flow_use_up_tile = flow.getString("t");
                result.gift_flow_use_up_content = flow.getString("s");
                
//               会员即将到期
                jospeedguide=jo.getJSONObject("vip_expire");
                tmp=jospeedguide.getJSONObject("before_expire");
                flow=tmp.getJSONObject(miVersion);
                result.vip_guide_bar_before_expire_title = flow.getString("t");
                result.vip_guide_bar_before_expire_content = flow.getString("s");
                
//              会员今天到期
                tmp=jospeedguide.getJSONObject("expire");
                flow=tmp.getJSONObject(miVersion);
                result.vip_guide_bar_expire_title = flow.getString("t");
                result.vip_guide_bar_expire_content = flow.getString("s");
                
            }catch(JSONException e){
                e.printStackTrace();
            }

            return result;
    }
    public int RequestConfigJS(){
            int res =0;

            String requrl=mConfigJsUrl;
            StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

                @Override
                public void onResponse(String response) {
                    // TODO Auto-generated method stub
                    XLUtil.logDebug(TAG,"Config json response"+response);
                    ConfigJSInfo res =configjsonparse(response);
                    if(res !=null){
                        setDefaultInfo(res, response);
                    }
                }
            }, new ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    AppConfig.LOGD(TAG, error.getMessage());
                }
            });
            req.setRetryPolicy(new DefaultRetryPolicy(
                    HTTP_SOCKET_TIMEOUT_MS, 
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES, 
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(req);
            return res;
    }

    //----------------getSpeedupJS----------------------------    
    final String DefaultSpeedupJSSrc="{\"free\":{\"flow\":300},\"scene1\":{\"min_download_times\":3,\"min_file_size\":60,\"remind_cycle\":1},\"scene2\":{\"slow_second\":5,\"min_slow_speed\":5,\"min_file_size\":60,\"remind_cycle\":1}}";
    public class SpeedupJSInfo{
        public int free_flow;                        //MB
        public int scene1_min_download_times;
        public int scene1_min_file_size;            //MB
        public int scene1_remind_cycle;            //day
        public int scene2_slow_second;                //s
        public int scene2_min_slow_speed;            //kb/s
        public int scene2_min_file_size;            //MB
        public int scene2_remind_cycle;            //day
    }
    SpeedupJSInfo speedupjsonparse(String str){

        if(XLUtil.isNullOrEmpty(str))
                return null;
            SpeedupJSInfo result=null;

            try{
                JSONObject jo =new JSONObject(str);
                result =new SpeedupJSInfo();
                JSONObject jofree=jo.getJSONObject("free");
                result.free_flow=jofree.getInt("flow");

                JSONObject    joscene1 =jo.getJSONObject("scene1");
                result.scene1_min_download_times =joscene1.getInt("min_download_times");
                result.scene1_min_file_size =joscene1.getInt("min_file_size");
                result.scene1_remind_cycle =joscene1.getInt("remind_cycle");

                JSONObject    joscene2 =jo.getJSONObject("scene2");
                result.scene2_slow_second =joscene2.getInt("slow_second");
                result.scene2_min_slow_speed =joscene2.getInt("min_slow_speed");
                result.scene2_min_file_size =joscene2.getInt("min_file_size");
                result.scene2_remind_cycle =joscene2.getInt("remind_cycle");

            }catch(JSONException e){
                e.printStackTrace();
            }

            return result;
    }
    public int RequestSpeedupJS(){
            int res =0;

            String requrl=mSpeedupJSUrl;

            StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

                @Override
                public void onResponse(String response) {
                    // TODO Auto-generated method stub
                    SpeedupJSInfo res =speedupjsonparse(response);
                    if(res !=null){
                        setDefaultSpeedupInfo(res, response);
                    }
                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    AppConfig.LOGD(TAG, error.getMessage());
                }
            });
            req.setRetryPolicy(new DefaultRetryPolicy(
                    HTTP_SOCKET_TIMEOUT_MS, 
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES, 
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(req);
            return res;
    }

}
