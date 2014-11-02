package com.android.providers.downloads;

import java.util.ArrayList;

import android.accounts.Account;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import miui.accounts.IXiaomiAccountService;
import miui.accounts.ExtraAccountManager;

import com.xunlei.speedup.util.Constant;
import com.xunlei.downloadplatforms.util.XLUtil;

public class TokenHelper {

    public static final String PREF_NAME = "download_pref";
    public static final String ACTION_INTENT_XLSPEEDUPACTIVITY_BROADCAST = "com.android.providers.downloads.ui.pay.xlspeedupactivity_broadcast";
    public static final String TOKENHELPER_DEFAULT_ACCOUNT ="tokenhelper_default_account";
    public static final String TOKENHELPER_LAST_TIMESTAMP ="tokenhelper_last_timestamp";
    private IBinder mBinderService;
    private Context mContext;
    SharedPreferences mPreferences;

    private long    MAX_TOKEN_DELAY = 24*60*60;
    private String  mDefaultAccountName ;
    private long    mLastChangeTokenTime;
    private static  int mFalseRequestTimes;
    private static  int mTrueRequestTimes;
    public final Object mutex = new Object();

    Thread mThread;
    Handler handler;
    ArrayList<Runnable> posted = new ArrayList<Runnable>();
    private TokenHelperListener mlistener;

    public TokenHelper() {
        checkThreadAndStart();
    }

    private static TokenHelper instance;

    public synchronized static TokenHelper getInstance() {
        if (instance == null) {
            instance = new TokenHelper();
        }
        return instance;
    }

    public int initWithContext(Context context) {
        int res = 0;

        mContext = context;
        checkThreadAndStart();
        if(mContext == null){
            return -1;
        }
        if ( mPreferences == null) {
            mPreferences = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
        }
        if(mBinderService ==null){
            mFalseRequestTimes =1;
            bindMiService();
        }
        return res;
    }

    public void uninit(){
        if(mContext == null){
            return ;
        }
        unbindMiService();
        mThread =null;
        mlistener =null;
        mContext =null;
    }

    public void setContext(Context context){
        mContext =context;
        if (mContext == null ){
            return;
        }
        if ( mPreferences == null) {
            mPreferences = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
        }
        if(mBinderService ==null){
            mFalseRequestTimes =1;
            bindMiService();
        }
    }


    public void setTokenHelperListener(TokenHelperListener listener) {
        mlistener = listener;
    }

    public abstract interface TokenHelperListener {
        public int OnTokenGet(int ret, String token);

    }

    class subThread extends Thread {
        public subThread() {

        }

        @Override
        public void run() {
            XLConfig.LOGD("(subThread.run) ---> Entering background thread");
            Looper.prepare();
            handler = new Handler() {
                    public void handleMessage(Message message) {
                        XLConfig.LOGD("(subThread.run) ---> message: " + message);
                    }
                };
            XLConfig.LOGD("(subThread.run) ---> Background thread handler is created");
            synchronized (posted) {
                for (Runnable task : posted) {
                    XLConfig.LOGD("(subThread.run) ---> Copying posted bg task to handler : " + task);
                    handler.post(task);
                }
                posted.clear();
            }
            Looper.loop();
            handler = null;
            instance = null;
            XLConfig.LOGD("(subThread.run) ---> Exiting background thread");
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                // mMiUiAccountBinder = null;
                XLConfig.LOGD("xunlei(ServiceConnection) ---> disconnected xiaomi account service.");
                setBinder(null);
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                XLConfig.LOGD("xunlei(ServiceConnection) ---> bind xiaomi account service success.");
                setBinder(service);
                if(mTrueRequestTimes >0){
                    tureRefreshFailAndRetry();

                    return;
                }
                if(mFalseRequestTimes >0 ) {
                    requestTokenInner(false);
                    mFalseRequestTimes =0;
                }

            }
        };

    private void checkThreadAndStart(){
        if (mThread != null && mThread.isAlive()) {
        } else {
            mThread = new subThread();
            mThread.start();
        }
    }
    private void setBinder(IBinder binder){
        mBinderService = binder;
    }
    private void bindMiService(){
        boolean mXunleiEngineEnable = getXunleiUsagePermission();
        if (mXunleiEngineEnable) {
            Intent intent = new Intent("android.intent.action.BIND_XIAOMI_ACCOUNT_SERVICE");
            mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        }
    }

    private void unbindMiService(){
        if(mBinderService != null){
            mContext.unbindService(serviceConnection);
        }
    }
    /**
     * get xunlei engine enable flag from xml(modifiled by ui)
     * @return
     */
    private boolean getXunleiUsagePermission() {
        boolean xunlei_usage;
        if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) {
            xunlei_usage = false;
        } else {

            SharedPreferences pf = mContext.getSharedPreferences(XLConfig.PREF_NAME, Context.MODE_MULTI_PROCESS);
            if (!pf.contains(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION)) {
                SharedPreferences.Editor et = pf.edit();
                et.putBoolean(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
                et.commit();
            }
            xunlei_usage  = pf.getBoolean(XLConfig.PREF_KEY_XUNLEI_USAGE_PERMISSION, true);
        }
        XLConfig.LOGD("(getXunleiUsagePermission) ---> get xunlei permission from xml:" + xunlei_usage);
        return xunlei_usage;
    }
    private void saveDefaultAccountName(Account account){
        if(account != null) {
            mDefaultAccountName = account.name;
            saveStringPreferences(mContext, TOKENHELPER_DEFAULT_ACCOUNT, mDefaultAccountName);
        }else{
            mDefaultAccountName ="";
            saveStringPreferences(mContext, TOKENHELPER_DEFAULT_ACCOUNT, mDefaultAccountName);
        }
    }
    private void saveLastChangeTime(){
        mLastChangeTokenTime = XLUtil.getCurrentUnixTime();
        XLUtil.saveLongPreferences(mContext,TOKENHELPER_LAST_TIMESTAMP,mLastChangeTokenTime);
    }
    private void checkDefaultInfoAndReadXml(){
        if(XLUtil.isNullOrEmpty( mDefaultAccountName)){
            mDefaultAccountName =getStringPreferences(mContext,TOKENHELPER_DEFAULT_ACCOUNT);
        }
        if( mLastChangeTokenTime == 0){
            mLastChangeTokenTime =XLUtil.getLongPreferences(mContext,TOKENHELPER_LAST_TIMESTAMP);
        }
    }

    private boolean isTokenAvailable(Account account){
        checkDefaultInfoAndReadXml();
        boolean res = true ;
        long timeTmp= XLUtil.getCurrentUnixTime() ;
        XLConfig.LOGD("isTokenAvailable() current"+timeTmp+" last+maxDelay="+ (mLastChangeTokenTime+MAX_TOKEN_DELAY) );

        if(timeTmp < (mLastChangeTokenTime + MAX_TOKEN_DELAY)){
            res = true;
        } else{
            res = false;

            return res;
        }

        if(account ==null){
            return res;
        }

        XLConfig.LOGD("isTokenAvailable() account.name="+account.name+" defaultname="+mDefaultAccountName );
        if(account.name.equals(mDefaultAccountName)){
            res = true;
        } else {
            res = false;
            return res;
        }

        return  res;
    }

    private boolean isAccountNullAndProcess(Account account){
        boolean result =false;
        if(account != null){
            return result;
        }
        saveDefaultAccountName(account);
        result =true;
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString("xunlei_token", "");
        editor.commit();
        return result;
    }

    private void tureRefreshFailAndRetry(){
        if(mTrueRequestTimes >0){
            mTrueRequestTimes --;
            this.requestTokenInner(true);
        }
    }
    /*
     * . miui liye sdk error code int ERROR_OK = 0;
     *
     * int ERROR_INVALID_PARAMS = -1000; //invalid parameter
     *
     * int ERROR_SERVICE_FAILED = -1001; //get service token failed
     *
     * int ERROR_ACCESS_DENIED = -1002; //access denied by server, server return 403
     *
     * int ERROR_AUTH_FAILED = -1003; //authenticate failure, server return 401
     *
     * int ERROR_IO = -1004; //network error
     *
     * int ERROR_UNKNOWN = -1005; //unknown error
     */
    private  long mlastTrueRequestTime  ;

    public  int RequestToken(final boolean tokenStrategy) {
        int res =-1;
        if(tokenStrategy ==true){
            if (System.currentTimeMillis() - mlastTrueRequestTime <= 3000) {
                return res;
            }
            mlastTrueRequestTime = System.currentTimeMillis() ;
            mTrueRequestTimes =1;
        }
        res = requestTokenInner(tokenStrategy);
        return res;
    }

    private synchronized int requestTokenInner(final boolean tokenStrategy){
        XLConfig.LOGD("(RequestToken) --> requestToken mdefaultaccount ="+mDefaultAccountName+"mlastchangetime="+mLastChangeTokenTime);
        int res = -1;
        if(mContext == null){
            return  res;
        }

        if (mBinderService == null ) {
            if(tokenStrategy == false) {
                mFalseRequestTimes = 1;
            }
            bindMiService();
            res =-2;
            return res;
        }
        checkThreadAndStart();

        if (handler instanceof Handler) {
            handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        Account account = ExtraAccountManager.getXiaomiAccount(mContext);
                        if(isAccountNullAndProcess(account)){
                            XLConfig.LOGD("(RequestToken) ---> account is null set token empty");
                            return;
                        }
                        if(tokenStrategy ==false){
                            if(isTokenAvailable(account)){
                                XLConfig.LOGD("(RequestToken) ---> token is available return");
                                return;
                            }
                        }
                        String result = null;
                        IXiaomiAccountService accountService = IXiaomiAccountService.Stub.asInterface(mBinderService);
                        if(accountService == null){
                            return ;
                        }
                        try {
                            result = accountService.getAccessToken(account, "micloud", "XUNLEI", tokenStrategy);
                            XLConfig.LOGD("(RequestToken) ---> token ExtraAccountManager.getXiaomiAccount(mContext):"
                                          + account + ", tokenStrategy=" + tokenStrategy);

                            String beginTarget = "StringContent{body='";
                            String endTarget = "'}";
                            String xl_token = "";
                            String xm_id = "";
                            xm_id = account.name;
                            XLConfig.LOGD("(RequestToken) ---> get token from api: " + result + ", xiaomiid:" + xm_id);

                            if (result != null && result.startsWith(beginTarget)) {
                                result = result.substring(beginTarget.length());
                            }
                            if (result != null && result.endsWith(endTarget)) {
                                result = result.substring(0, result.length() - endTarget.length());
                            }
                            //XLConfig.LOGD(Constants.TAG,  "(RequestToken) ---> json token: " + result);
                            if (result != null) {
                                JSONObject json_object = new JSONObject(result);
                                int code = json_object.getInt("code");
                                XLConfig.LOGD("(RequestToken) ---> token.code=" + code);
                                if (24003 != code) {
                                    JSONObject xl_data = json_object.getJSONObject("data");
                                    xl_token = xl_data.getString("key");
                                    XLConfig.LOGD("(RequestToken) ---> real token: " + xl_token);
                                    if (mlistener != null) {
                                        mlistener.OnTokenGet(code, xl_token);
                                    }
                                    mTrueRequestTimes =0;

                                    //step1. set token to jni
                                } else {
                                    //                                tureRefreshFailAndRetry();
                                }
                            }
                            // step2. set token to xml
                            saveDefaultAccountName(account);
                            saveLastChangeTime();
                            SharedPreferences.Editor editor = mPreferences.edit();
                            editor.putString(XLConfig.PREF_KEY_XUNLEI_TOKEN, xl_token);
                            editor.putString(XLConfig.PREF_KEY_XIAOMI_ID, xm_id);
                            boolean editortrue = editor.commit();
                            XLConfig.LOGD("(RequestToken) ---> write token to xml:" + xl_token + ", xiaomiid:" + xm_id +",editortrue ="+editortrue);
                        } catch (DeadObjectException e){
                            bindMiService();
                            return;
                        } catch (RemoteException e) {
                            XLConfig.LOGD("error in requestTokenInner: ", e);
                        } catch (JSONException e) {
                            XLConfig.LOGD("error in requestTokenInner: ", e);
                            tureRefreshFailAndRetry();
                            return;
                        }
                    }
                });
        }

        res = 0;
        return res;
    }
    private  void saveStringPreferences(Context context, String key, String str) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, str);
        editor.commit();
    }

    private  String getStringPreferences(Context context, String key) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(key, "");
    }

    private void RequestNickName(IXiaomiAccountService accountService)
        throws RemoteException {
        String res = accountService.getNickName(ExtraAccountManager.getXiaomiAccount(mContext));
        XLConfig.LOGD("(RequestNickName) ---> nickname=" + res);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString("miui_nickname", res);
        editor.commit();
    }

    private void RequestAvatar(IXiaomiAccountService accountService)
        throws RemoteException {
        ParcelFileDescriptor res = accountService.getAvatarFd(ExtraAccountManager.getXiaomiAccount(mContext));
        XLConfig.LOGD("(RequestAvatar) ---> avstartFile=" + res);
        Intent intent = new Intent(ACTION_INTENT_XLSPEEDUPACTIVITY_BROADCAST);
        intent.putExtra("Avatarfd", res);
        mContext.sendBroadcast(intent);
    }

}