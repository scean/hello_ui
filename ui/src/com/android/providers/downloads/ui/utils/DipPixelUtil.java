/*
 * 文件名称 : DipPixelUtil.java
 * <p>
 * 作者信息 : liuzongyao
 * <p>
 * 创建时间 : 2013-9-10, 下午7:43:55
 * <p>
 * 版权声明 : Copyright (c) 2009-2012 Hydb Ltd. All rights reserved
 * <p>
 * 评审记录 :
 * <p>
 */

package com.android.providers.downloads.ui.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.util.FloatMath;

/**
 * Dip和Pixel之间转化
 * <p>
 */
public class DipPixelUtil
{
    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    @SuppressLint("DefaultLocale")
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public static boolean isFirstDownload(Context context) {
        SharedPreferences flagFile = context.getSharedPreferences("isfirstdownload", Activity.MODE_PRIVATE);
        if (flagFile.getBoolean("isfirstdownload", true)) {
            SharedPreferences.Editor edit = flagFile.edit();
            edit.putBoolean("isfirstdownload", false);
            edit.commit();
            return true;
        } else {
            return false;
        }
    }

    public static int formatDipToPx(Context context, int dip) {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (int) FloatMath.ceil(dip * dm.density);
    }

    /**
    * @param spValue
    * @param fontScale
    *            (DisplayMetrics类中的scaledDensity属性)
    * @return
    */
   public static int sp2pix(float spValue, float fontScale) {
       return (int) (spValue * fontScale + 0.5f);
   }

}
