package com.android.providers.downloads.ui.pay.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.os.Environment;
import android.util.Log;

/**
 * 通用Log工具�?可以通过setLogLevel控制log 输出级别
 */
class XLLog {

    // 当前日志的级�?
    private static LogLevel mCurrentLogLevel = LogLevel.LOG_LEVEL_DEBUG;

    private static  String XUNLEI_LOG_TAG = " com.xunlei.log ";
    private static Boolean MYLOG_WRITE_TO_FILE=false;

    private static String MYLOG_PATH_SDCARD_DIR=Environment.getExternalStorageDirectory().getPath();;// 日志文件在sdcard中的路径
    private static int SDCARD_LOG_FILE_SAVE_DAYS = 0;// sd卡中日志文件的最多保存天�?
    private static String MYLOGFILEName = "Log.txt";// 本类输出的日志文件名�?
    private static SimpleDateFormat logfile = new SimpleDateFormat("yyyy-MM-dd");// 日志文件格式
    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static void setWriteFile(boolean  flag) {
        MYLOG_WRITE_TO_FILE =flag;
    }
    public static void setLogTag(final String tag) {
        XUNLEI_LOG_TAG = tag;
    }

    public static void setLogLevel(LogLevel logLevel) {
        mCurrentLogLevel = logLevel;
    }

    public static LogLevel getLogLevel() {
        return mCurrentLogLevel;
    }

    public static void w(String tag, String msg) {
        if (MYLOG_WRITE_TO_FILE ==true) {
            writeLogtoFile("w", tag, msg);
            return;
        }
        if (mCurrentLogLevel.getValue() >= LogLevel.LOG_LEVEL_WARN.getValue()) {
            Log.w(tag,  formatMessage(tag , msg));
        }
    }

    public static void d(String tag, String msg) {
        if (MYLOG_WRITE_TO_FILE ==true) {
            writeLogtoFile("d", tag, msg);
            return;
        }
        if (mCurrentLogLevel.getValue() >= LogLevel.LOG_LEVEL_DEBUG.getValue()) {
            Log.d(tag, formatMessage(tag , msg));
        }
    }

    public static void e(String tag, String msg) {
        if (MYLOG_WRITE_TO_FILE ==true) {
            writeLogtoFile("e", tag, msg);
            return;
        }
        if (mCurrentLogLevel.getValue() >= LogLevel.LOG_LEVEL_ERROR.getValue()) {
            Log.e(tag, formatMessage(tag , msg));
        }
    }

    public static void i(String tag, String msg) {
        if (MYLOG_WRITE_TO_FILE ==true) {
            writeLogtoFile("i", tag, msg);
            return;
        }
        if (mCurrentLogLevel.getValue() >= LogLevel.LOG_LEVEL_INFO.getValue()) {
            Log.i(tag, formatMessage(tag , msg));
        }
    }

    private static String formatMessage(final String tag , String msg) {
        String extraMsg = " [ " + getMethodLocationInfo(tag) ;
        extraMsg += XUNLEI_LOG_TAG;
        extraMsg += getMillisTime();
        extraMsg += " thread_id = ";
        extraMsg += Thread.currentThread().getId();
        extraMsg += " ver:";
        extraMsg += Config.JAR_VERSION;
        extraMsg += " ] ";
        msg  = msg + "   " +extraMsg;
        return msg;
    }

    private static String getMethodLocationInfo(final String tag){
        String extraString = tag ;
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement item:stack) {
            if (-1 != item.getClassName().indexOf(tag)) {
                extraString = "[ ClassName : " + item.getClassName() + " , MethodName : " + item.getMethodName()
                            + " , LineNumber : "+ item.getLineNumber() + " , FileName : " + item.getFileName() + "  ]  ";
                break;
            }
        }
        return extraString;
    }

    public static String getMillisTime() {
        return format.format(new Date());
    }

    public enum LogLevel {
        LOG_LEVEL_OFF(0),
        LOG_LEVEL_ERROR(1),
        LOG_LEVEL_WARN(2),
        LOG_LEVEL_INFO(3),
        LOG_LEVEL_DEBUG(4);

        private int logLevel;

        LogLevel(int value) {
            this.logLevel = value;
        }

        public int getValue() {
            return logLevel;
        }
    }
    /**
     * 打开日志文件并写入日�?
     *
     * @return
     */
    private static void writeLogtoFile(String mylogtype, String tag, String text) {// 新建或打�?��志文�?
        Date nowtime = new Date();
        String needWriteFiel = logfile.format(nowtime);
        String needWriteMessage = format.format(nowtime) + "    " + mylogtype
                + "    " + tag + "    " + text;
        File file = new File(MYLOG_PATH_SDCARD_DIR, needWriteFiel
                + MYLOGFILEName);
        try {
            FileWriter filerWriter = new FileWriter(file, true);//后面这个参数代表是不是要接上文件中原来的数据，不进行覆盖
            BufferedWriter bufWriter = new BufferedWriter(filerWriter);
            bufWriter.write(needWriteMessage);
            bufWriter.newLine();
            bufWriter.close();
            filerWriter.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 删除制定的日志文�?
     */
    public static void delFile() {// 删除日志文件
        String needDelFiel = logfile.format(getDateBefore());
        File file = new File(MYLOG_PATH_SDCARD_DIR, needDelFiel + MYLOGFILEName);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 得到现在时间前的几天日期，用来得到需要删除的日志文件�?
     */
    private static Date getDateBefore() {
        Date nowtime = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(nowtime);
        now.set(Calendar.DATE, now.get(Calendar.DATE)
                - SDCARD_LOG_FILE_SAVE_DAYS);
        return now.getTime();
    }
}
