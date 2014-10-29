package com.android.providers.downloads.ui.auth;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;

import com.android.providers.downloads.ui.activity.XLUnbindActivity;


public class UnbindManager {

	private List<OnUnbindResultListener> mUnbindResultListeners = new ArrayList<OnUnbindResultListener>();


	private static UnbindManager mUnbindManager = null;

	int mSuccessCode = 0;


	public static UnbindManager getInstance() {
		if (mUnbindManager == null) {
			mUnbindManager = new UnbindManager();
		}
		return mUnbindManager;
	}


	public void Unbind(Activity activity, String token, String from) {
		if (activity == null) {
			return;
		}
		Intent intent = new Intent();
		intent.putExtra("token", token);
		intent.putExtra("from", from);

		intent.setClass(activity, XLUnbindActivity.class);
		activity.startActivity(intent);

	}

	public void fireUnbindResultListeners(int flag, int result) {
		List<OnUnbindResultListener> list = new ArrayList<OnUnbindResultListener>(mUnbindResultListeners);

		for (OnUnbindResultListener listener : list) {
			listener.onSuccess(flag, result);
		}

	}


	public void addUnbindListener(OnUnbindResultListener listener) {

		if (listener != null && !mUnbindResultListeners.contains(listener)) {
			mUnbindResultListeners.add(listener);
		}

	}


	public void removeUnbindListener(OnUnbindResultListener listener) {

		if (listener != null) {
			mUnbindResultListeners.remove(listener);
		}
	}
}
