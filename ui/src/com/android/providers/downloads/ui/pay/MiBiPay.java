package com.android.providers.downloads.ui.pay;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.TreeMap;
import java.util.Iterator;

import com.android.providers.downloads.ui.utils.DateUtil;
import com.android.providers.downloads.ui.utils.MyVolley;
import com.android.providers.downloads.ui.utils.XLUtil;
import com.android.providers.downloads.ui.network.ExtHttpClientStack;
import com.android.providers.downloads.ui.network.SSLSocketFactoryEx;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import miui.payment.PaymentManager.PaymentListener;
import miui.payment.PaymentManager;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

public class MiBiPay {
    private static String TAG = MiBiPay.class.getSimpleName();

    //static String mReportPrefix="http://dypay.vip.xunlei.com/mipay/order/?";
    static String mReportPrefix="https://payment.xunlei.com/mipay/order/?";
    //static String mPrizePrefix="http://dypay.vip.xunlei.com/mipay/getprice/?";
    static String mPrizePrefix="https://payment.xunlei.com/mipay/getprice/?";    

    public static String XLTOKEN="tk10.8F76F2B66E72644EF3A85D0DC8FE052D1358385F3E69463604674617ED741B67A991179E93730066E9217D59B37D936B";
    public static double[]    XLprizes;
    public static double XLYearPrize;
    public static TreeMap<Integer, String> XLMONPrize;
    private final int HTTPS_SOCKET_TIMEOUT_MS =15000;
    private final int HTTP_SOCKET_TIMEOUT_MS =10000;
    private RequestQueue     mHTTPSRequestQueue; 
    private RequestQueue     mRequestQueue;
    private PaymentManager     mPayManager;
    private PayListener       mListener;
    private Context mContext;
    private Activity mActivity;
    public int  initWithContext(Context context,Activity act) {
        int res =0;
        mContext =context;
        mActivity =act;
        mHTTPSRequestQueue = MyVolley.getInstance(context).getHttpsRequestQueue();
        mRequestQueue =MyVolley.getInstance(context).getRequestQueue();
        mPayManager =PaymentManager.get(context);
        return res;
    }
    public void uninit() {
        mContext =null;
        mActivity =null;
        mListener =null;
        mPayManager =null;
    }

    public abstract interface PayListener {
        public int OnSuccessPay(int ret,String result);
        public int OnFailed( int ret,String result,int need_token,int need_auth);
        public int onGetOrderEnd(int ret,String result,int month,String org_token, int need_token,  int need_auth);
        public int OnPrizesSuccess(int ret,double[] prizes);
        public int OnPrizesFail(int ret,String result);
    }
    public void setPayListener(PayListener listener) {
        mListener =listener;
    }

    public int MibipayOrder(String paymentId,String order,boolean quickPay) {

        Bundle extra = new Bundle();
        if (quickPay) {
            extra.putBoolean(PaymentManager.PAYMENT_KEY_QUICK, true);
        }else{
            extra.putBoolean(PaymentManager.PAYMENT_KEY_QUICK, false);
        }
        XLUtil.logDebug(TAG,"miui pay order="+order);
        try{
        mPayManager.payForOrder(mActivity, paymentId, order, extra, new PaymentListener() {

            @Override
            public void onSuccess(String paymentId, Bundle result) {
                // TODO Auto-generated method stub
                String res = result.getString(PaymentManager.PAYMENT_KEY_PAYMENT_RESULT);
                if(mListener !=null) {
                    mListener.OnSuccessPay(0, res);
                }
            }

            @Override
            public void onFailed(String paymentId, int code, String message, Bundle result) {
                // TODO Auto-generated method stub
                if (mListener !=null) {
                    mListener.OnFailed(code, message,0,0);
                }
            }
        });
        } catch(Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    //------------getOrder-----------------------------
    public class OrderInfo {
        public int ret  ;//    0  成功 ，其他的为异常
        public String msg    ; // 异常说明
        public int need_refresh;
        public int need_auth;
        public String userid  ; //迅雷id
        public String orderid  ;//订单号
        public String ext   ;  // 启动客户端额外支付信息  (小米的orderjson)

        @Override
        public String toString() {
            return  (ret+msg+need_refresh+need_auth+userid+orderid+ext);
        }
    }
    OrderInfo orderjsonparse(String str) {
	    OrderInfo result =new OrderInfo();
	    result.ret =-1;
        if (XLUtil.isNullOrEmpty(str)) {
            return result;
        }

        try {
            JSONObject json_object =new JSONObject(str);
            result.ret=json_object.getInt("ret");
            result.msg=json_object.getString("msg");
            result.need_refresh   =json_object.getInt("need_refresh");
            result.need_auth      =json_object.getInt("need_auth");
            result.orderid =json_object.getString("orderid");
            result.userid =json_object.getString("userid");
            result.ext=json_object.getString("ext");

        } catch(JSONException e) {
            e.printStackTrace();
        }

        return result;
    }
    /*
     * vastype      支付类型：3 白金会员
     * month        月份：可选1-12
     * source       业务来源 （开发分配，小米：mi）
     * referfrom    支付统计点（运营分配,可为空）
     * client_id    开发者申请的app key
     * access_token 开发者获取到的 access token
     */

    public int RequestOrder(final int month,int vastype,String source,String referfrom, final String access_token) {
        int res =0;

        String requrl=mReportPrefix+
                        "month="+month+
                        "&source="+source+
                        "&referfrom="+referfrom+
                        "&vastype="+vastype+
                        "&access_token="+access_token+
                        "&callback="+XLUtil.getCurrentUnixMillTime();

        StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

            @Override
            public void onResponse(String response) {
                // TODO Auto-generated method stub
                XLUtil.logDebug(TAG,"pay order response"+response);
	            String pair1,pair2="";
	            try {
		            pair1 = response.substring(response.indexOf("(") + 1);
		            pair2 = pair1.substring(0, pair1.lastIndexOf(")"));

	            }catch (Exception e){
	            }
                OrderInfo info =orderjsonparse(pair2);
	            if(info.need_refresh ==1){

		            XLUtil.askRequestToken(mContext);
	            }
                if (info.ret ==0 && info.need_auth ==0) {
                    if(mListener !=null){
                        mListener.onGetOrderEnd(0,info.msg,month,access_token,info.need_refresh,info.need_auth);
                    }
                    MibipayOrder(""+XLUtil.getCurrentUnixTime(), info.ext, true);
                } else {
                    if (mListener !=null) {
                        XLUtil.logDebug(TAG,"pay fail= "+info);
//                        mListener.OnFailed(info.ret, info.msg,info.need_refresh,info.need_auth);
                        mListener.onGetOrderEnd(info.ret,info.msg,month,access_token,info.need_refresh,info.need_auth);
                    }
                }
            }
        }, new ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO Auto-generated method stub
                XLUtil.logDebug(TAG, error.getMessage());

                int state =-1;
                if(error.networkResponse !=null){
                    state =error.networkResponse.statusCode;
                }
                if(mListener !=null){
//                    mListener.OnFailed(state, error.toString(),0,0);
                    mListener.onGetOrderEnd(state,error.toString(),month,access_token,0,0);
                }
            }
        });
        req.setRetryPolicy(new DefaultRetryPolicy(
                HTTPS_SOCKET_TIMEOUT_MS, 
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, 
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mHTTPSRequestQueue.add(req);
        return res;
    }

    //--------------getprizes---------------------    
    public class PrizeInfo {
        public int ret  ;//    0  成功 ，其他的为异常
        public double[] prizes;   //int[0-11] index
        public double or_year_price; //year origin prize
        public TreeMap<Integer, String> mon_price_map;
    }
    PrizeInfo Prizejsonparse(String str) {
	    PrizeInfo result =new PrizeInfo();
	    result.ret =-1;
	    result.prizes =new double[20];
	    result.mon_price_map = new TreeMap<Integer, String>();
        if (XLUtil.isNullOrEmpty(str)) {

            return result;
        }
        try {
            JSONObject json_object =new JSONObject(str);
            result.ret=json_object.getInt("ret");
            JSONObject arr =(JSONObject) json_object.get("price");
            for (int i=0;i<12;i++) {
                result.prizes[i] =arr.getDouble(""+(i+1));
            }
            result.or_year_price =json_object.getDouble("or_year_price");

            JSONObject new_arr =(JSONObject) json_object.get("price_new");
            Iterator<String> it = new_arr.keys();
            while(it.hasNext()){
                String n_key = (String) it.next();
                String n_value = new_arr.getString(n_key);
                result.mon_price_map.put(Integer.valueOf(n_key), n_value);
                XLUtil.logDebug(TAG, "price_new k:"+n_key+ " v:"+n_value);
            }

        } catch(JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    /*
     * access_token 开发者获取到的 access token
     * type         业务类型：0 购买/续费
     * vastype      支付类型：3 白金会员
     */
    public int RequestPrize(int type,int vastype,String access_token){
        int res =0;

        String requrl=mPrizePrefix+
                        "type="+type+
                        "&vastype="+vastype+
                        "&access_token="+access_token+
                        "&callback="+XLUtil.getCurrentUnixMillTime();

        StringRequest req =new StringRequest(Method.GET, requrl,new Listener<String>() {

            @Override
            public void onResponse(String response) {
                // TODO Auto-generated method stub
                XLUtil.logDebug(TAG, "prize response"+response);
	            String pair1,pair2="";
	            try {
		            pair1 = response.substring(response.indexOf("(") + 1);
		            pair2 = pair1.substring(0, pair1.lastIndexOf(")"));

	            } catch (Exception e){
	            }
                PrizeInfo info =Prizejsonparse(pair2);
                if(info.ret ==0){
                    XLprizes =info.prizes;
                    XLYearPrize =info.or_year_price;
                    XLMONPrize = info.mon_price_map;
                    if(mListener !=null){
                        mListener.OnPrizesSuccess(info.ret, info.prizes);
                    }
                }
            }
        }, new ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO Auto-generated method stub
                XLUtil.logDebug(TAG, error.getMessage());
                int state =-1;
                if(error.networkResponse !=null){
                    state =error.networkResponse.statusCode;
                }
                if(mListener !=null){
                    mListener.OnPrizesFail(state,""+ error.toString());
                }
            }
        });
        req.setRetryPolicy(new DefaultRetryPolicy(
                HTTP_SOCKET_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mHTTPSRequestQueue.add(req);
        return res;
    }
}
