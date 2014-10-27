package com.android.providers.downloads.ui.utils;

import com.android.providers.downloads.ui.app.AppConfig;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.*;

import android.os.Build;
import android.os.Environment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Default crash log dir is external cache dir.
 */
public class CrashHandler implements UncaughtExceptionHandler {

	private Thread.UncaughtExceptionHandler mDefaultHandler;

	private static CrashHandler sInstance;

	private Context mContext;

	private Map<String, String> mDeviceInfo = new HashMap<String, String>();

	private DateFormat mFormatter = new SimpleDateFormat("yyMMdd");

	private CrashHandler() {
	}

	public static CrashHandler getInstance() {
		if (sInstance == null) {
			sInstance = new CrashHandler();
		}
		return sInstance;
	}

	public void init(Context context) {
		mContext = context;
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	@Override
	public void uncaughtException(Thread thread, Throwable tx) {
		handleException(tx);

		if (mDefaultHandler != null) {
			mDefaultHandler.uncaughtException(thread, tx);
		}
	}

	public boolean handleException(Throwable tx) {
		if (tx == null) {
			return false;
		}

		collectDeviceInfo(mContext);
		saveCrashInfoToFile(tx);
		return true;
	}

	public void collectDeviceInfo(Context context) {
		try {
			PackageManager manager = context.getPackageManager();
			PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
			if (info != null) {
				String versionName = info.versionName == null ? "null" : info.versionName;
				String versionCode = info.versionCode + "";
				mDeviceInfo.put("versionName", versionName);
				mDeviceInfo.put("versionCode", versionCode);
			}
		} catch (NameNotFoundException e) {
		}

		Field[] fields = Build.class.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				mDeviceInfo.put(field.getName(), field.get(null).toString());
			} catch (Exception e) {
			}
		}
	}

	public String saveCrashInfoToFile(Throwable tx) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : mDeviceInfo.entrySet()) {
			sb.append(entry.getKey());
			sb.append("=");
			sb.append(entry.getValue());
			sb.append("\n");
		}

		Writer writer = new StringWriter();
		PrintWriter pw = new PrintWriter(writer);
		tx.printStackTrace(pw);
		Throwable cause = tx.getCause();
		while (cause != null) {
			cause.printStackTrace(pw);
			cause = cause.getCause();
		}
		pw.close();
		String result = writer.toString();
		sb.append(result);
		sb.append("\n\n");

		try {
			String time = mFormatter.format(new Date());
			String fileName = "crash_log_ui_" + time + ".log";
			if (AppConfig.LOG_DIR != null) {
				String path = AppConfig.LOG_DIR;
				File dir = new File(path);
				if (!dir.exists()) {
					dir.mkdirs();
				}
				FileOutputStream fos = new FileOutputStream(new File(path, fileName), true);
				fos.write(sb.toString().getBytes());
				fos.close();
			}
			return fileName;
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return null;
	}
}