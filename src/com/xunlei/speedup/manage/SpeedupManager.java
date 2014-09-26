package com.xunlei.speedup.manage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Message;

import com.xunlei.speedup.model.Xlsp;
import com.xunlei.speedup.util.Constant;

public class SpeedupManager implements SpeedupInterface {

    String TAG = SpeedupManager.class.getSimpleName();
    private SpeedupHttpEngine mSuHttpEngine;
    private ExecutorService mExecutor;
    private static SpeedupManager instance;

    private SpeedupManager() {
        mSuHttpEngine = new SpeedupHttpEngine(this);
        mExecutor = Executors.newCachedThreadPool();
    }

    public static SpeedupManager getInstance() {
        if (instance == null) {
            instance = new SpeedupManager();
        }
        return instance;
    }

    public void reqPushTask(final Xlsp xl, final Handler callHack) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mSuHttpEngine.reqPushTask(xl, callHack);
            }
        });
    }

    @Override
    public void onPushTaskCallBack(int ret, Handler callBack, String errorMessage) {
        Message msg = callBack.obtainMessage(Constant.MSG_PUSH_TASK, ret, ret, errorMessage);
        if (ret == 0) {
            callBack.sendMessage(msg);
        } else {
            callBack.sendMessageDelayed(msg, 1000);
        }
    }
    
     

}
