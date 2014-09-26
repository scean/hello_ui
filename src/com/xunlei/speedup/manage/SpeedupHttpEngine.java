package com.xunlei.speedup.manage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;

import com.xunlei.speedup.model.Xlsp;
import com.xunlei.speedup.util.ErrorCode;
import com.xunlei.speedup.util.XLLOG;

public class SpeedupHttpEngine {

    String TAG = SpeedupHttpEngine.class.getSimpleName();

    private static final String SERVER_URL = "http://interface.open.xunlei.com";

    // private static final String SERVER_URL = "http://192.168.102.38:8080";

    // private static final String COMMAND_PUSHTASK = "/Test/Test";

    private static final String COMMAND_PUSHTASK = "/yunjiasu";

    private SpeedupInterface mSpeedupInterface;

    public SpeedupHttpEngine(SpeedupInterface speedupInterface) {
        this.mSpeedupInterface = speedupInterface;
    }

    public SpeedupHttpEngine() {
    }

    public boolean reqPushTask(Xlsp xlsp, Handler callHack) {
        Random random = new Random();
        String url = SERVER_URL + COMMAND_PUSHTASK + buildBaseSyncUrl() + "&random=" + random.nextInt();
        boolean backmessage = false;
        JSONObject postJson = new JSONObject();
        int ret = 0;
        String errorMessage = "";
        try {
            postJson.put("downUrl", xlsp.getDownUrl());
            postJson.put("appId", xlsp.getAppId());
            postJson.put("appVersion", xlsp.getAppVersion());
            postJson.put("miuiId", xlsp.getMiuiId());
            postJson.put("miuiVersion", xlsp.getMiuiVersion());
            postJson.put("peerid", xlsp.getPeerid());
            postJson.put("buildModel", android.os.Build.MODEL);
        } catch (JSONException e) {
            ret = ErrorCode.ERR_BUILD_JSON_EXCEPTION;
        }
        XLLOG.d(TAG, "postJson=" + ret);
        if (ret == 0) {
            ResultData responseData = executeHttpPostRequest(url, postJson.toString());
            ret = responseData.result_code;
            JSONObject json = responseData.result_json;
            XLLOG.d(TAG, "getserver=" + ret);
            if (ret == 0) {
                XLLOG.d(TAG, "reqPushTask retJson = " + json);
                ret = json.optInt("result", ErrorCode.ERR_DEFAULT);
                if (ret != 0) {
                    errorMessage = json.optString("message");
                    XLLOG.d(TAG, "serverback=" + ret);
                    backmessage = false;
                } else {
                    XLLOG.d(TAG, "serverback=" + ret);
                    backmessage = true;
                }
            }
        }
        // XLLOG.d(TAG, "reqPushTask ccTag = " + ccTag + " ccTagMD5 = " +
        // ccTagMD5 + " ret = " + ret);
        if (callHack != null && mSpeedupInterface != null) {
            mSpeedupInterface.onPushTaskCallBack(ret, callHack, errorMessage);
        }
        return backmessage;
    }

    private ResultData executeHttpPostRequest(String url, String post_content) {
        ResultData res_data = new ResultData();
        try {
            HttpPost requestPost = new HttpPost(url);
            if (post_content != null) {
                requestPost.setEntity(new StringEntity(post_content, "UTF-8"));
            }
            res_data = executeHttpRequest(requestPost);
        } catch (UnsupportedEncodingException e) {
            res_data.result_code = ErrorCode.ERR_UNE_EXCEPTION;
        } catch (IllegalArgumentException e) {
            res_data.result_code = ErrorCode.ERR_ILA_EXCEPTION;
        }
        return res_data;
    }

    private ResultData executeHttpRequest(HttpUriRequest request) {
        int res_code;
        String res_str = null;
        JSONObject res_json = null;
        request.addHeader("Accept", "application/json");
        request.addHeader("Accept-Charset", "GBK,utf-8");
        request.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        // 超时设置
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 20000);// +设置20秒连接超时
        HttpConnectionParams.setSoTimeout(params, 20000);// 设置20秒等待超时
        HttpClient client = new DefaultHttpClient(params);
        HttpResponse response;
        try {
            response = client.execute(request);
            res_code = response.getStatusLine().getStatusCode();
            if (res_code == HttpStatus.SC_OK) {
                try {
                    res_str = EntityUtils.toString(response.getEntity(), "UTF-8");
                    res_json = new JSONObject(res_str);
                    res_code = 0;
                } catch (IOException e) {
                    res_code = ErrorCode.ERR_STRIO_EXCEPTION;
                } catch (JSONException e) {
                    res_code = ErrorCode.ERR_JSON_EXCEPTION;
                }
            }
        } catch (ClientProtocolException e) {
            res_code = ErrorCode.ERR_CLP_EXCEPTION;
        } catch (UnknownHostException e) {
            res_code = ErrorCode.ERR_UNKNOW_HOST_EXCEPTION;
        } catch (SocketException e) {
            res_code = ErrorCode.ERR_SOCKET_EXCEPTION;
        } catch (SocketTimeoutException e) {
            res_code = ErrorCode.ERR_SOCKET_TIMEOUT_EXCEPTION;
        } catch (IOException e) {
            res_code = ErrorCode.ERR_IO_EXCEPTION;
        } catch (ParseException e) {
            res_code = ErrorCode.ERR_PARSE_EXCEPTION;
        }
        ResultData res_data = new ResultData(res_code, res_json);
        return res_data;
    }

    class ResultData {
        public int result_code;
        public JSONObject result_json;

        public ResultData() {
        }

        public ResultData(int result_code, JSONObject result_json) {
            this.result_code = result_code;
            this.result_json = result_json;
        }
    }

    public static String buildBaseSyncUrl() {
        String protocol_ver = "1.0";
        String command_id = "3g_jiasu_req";
        String url = "?protocol_ver=" + protocol_ver + "&" + "command_id=" + command_id;
        return url;
    }

}
