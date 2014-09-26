package com.xunlei.speedup.manage;

import android.os.Handler;

public interface SpeedupInterface {
    public void onPushTaskCallBack(int ret, Handler callBack, String errorMessage);
}
