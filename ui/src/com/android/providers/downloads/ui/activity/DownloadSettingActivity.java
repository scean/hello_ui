/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.downloads.ui.activity;

import com.android.providers.downloads.ui.notification.PreferenceLogic;
import com.android.providers.downloads.ui.utils.DownloadUtils;
import com.android.providers.downloads.ui.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import miui.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import miui.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.provider.Settings;
import android.util.Log;

/**
 * With this activity, users can set download size limit for data usage
 */
public class DownloadSettingActivity extends PreferenceActivity implements NumberPicker.OnValueChangeListener {
	// display size limit list
	private NumberPicker mSizeLimitPicker;
	// displayed items' text
	private String[] mEntries;
	// corresponding values displayed items
	private String[] mValues;
	// Preference for display summary of limited download size
	private Preference mLimitedSizePref;
	private static final String PREF_KEY_SIZE_LIMIT = "pref_size_limit_info";
	// checkbox
	private CheckBoxPreference mXunleiUsagePref;
	private PreferenceCategory mPreferenceCategory;
	public static final String PREF_KEY_XUNLEI_USAGE_PERMISSION = "xunlei_usage_permission";
	public static final String PREF_KEY_CATEGORY = "CategoryCheckbox";
	public static final String PREF_NAME = "download_pref";
	private static final Uri sRecommendedBytesLimitUri = Uri.parse("content://downloads/download_bytes_limit_over_mobile");
	private static final String DOWNLOADPROVIDER_PKG_NAME = "com.android.providers.downloads";
	private static final int ONLINE_EVENT_ID = DownloadUtils.SETTING_ONLINE_EVENT;
	private boolean mPaused = false;	// 弹框的情况不统计下线（onPause）
	

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.settings);
		initialize();
	}
	
	 @Override
		protected void onResume() {
			// TODO Auto-generated method stub
			super.onResume();
			
			if (!mPaused) {
				Context ctx = getApplicationContext();
				DownloadUtils.trackOnlineStatus(ctx, BaseActivity.STATUS_ONLINE,
						!DownloadUtils.getXunleiUsagePermission(ctx), !DownloadUtils.getXunleiVipSwitchStatus(ctx),
						ONLINE_EVENT_ID);
			}
		}
	    
	    @Override
		protected void onPause() {
			// TODO Auto-generated method stub
			super.onPause();
			mPaused = true;
		}

		@Override
		protected void onStop() {
			// TODO Auto-generated method stub
			super.onStop();
			mPaused = false;
			
			Context ctx = getApplicationContext();
			DownloadUtils.trackOnlineStatus(ctx, BaseActivity.STATUS_OFFLINE,
					!DownloadUtils.getXunleiUsagePermission(ctx), !DownloadUtils.getXunleiVipSwitchStatus(ctx),
					ONLINE_EVENT_ID);
		}

	private void initialize() {
		addPreferencesFromResource(R.xml.setting_prefs);
		// setup number pick
		mEntries = getResources().getStringArray(R.array.prefEntries_max_bytes_limit);
		mValues = getResources().getStringArray(R.array.prefValues_max_bytes_limit);
		mSizeLimitPicker = (NumberPicker) findViewById(R.id.sizeLimit);
		mSizeLimitPicker.setDisplayedValues(mEntries);
		mSizeLimitPicker.setMinValue(0);
		mSizeLimitPicker.setMaxValue(mEntries.length - 1);
		mSizeLimitPicker.setWrapSelectorWheel(false);
		mSizeLimitPicker.setOnValueChangedListener(this);
		String maxLimitedSize = String.valueOf(DownloadUtils.getRecommendedMaxBytesOverMobile(this));
		int index;
		for (index = 0; index < mEntries.length; index++) {
			if (mValues[index].equals(maxLimitedSize))
				break;
		}
		mSizeLimitPicker.setValue(index);

		// set up summary preference
		mLimitedSizePref = findPreference(PREF_KEY_SIZE_LIMIT);
		mLimitedSizePref.setSummary(getMaxBytesSummary());

		// setup checkbox
		boolean xunlei_usage = PreferenceLogic.getInstance().getIsHaveUseXunleiDownload();
		mPreferenceCategory = (PreferenceCategory) findPreference(PREF_KEY_CATEGORY);
		mXunleiUsagePref = (CheckBoxPreference) findPreference(PREF_KEY_XUNLEI_USAGE_PERMISSION);
		mXunleiUsagePref.setChecked(xunlei_usage);
		mXunleiUsagePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean checked = (Boolean) newValue;
				if (!checked) {
					Builder dialog = new AlertDialog.Builder(DownloadSettingActivity.this).setIcon(android.R.drawable.ic_dialog_alert).setIconAttribute(android.R.attr.alertDialogIcon)
							.setTitle(R.string.turn_off_xunlei_title).setMessage(R.string.turn_off_xunlei_message).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									mXunleiUsagePref.setChecked(true);
								}
							}).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {

										PreferenceLogic.getInstance().setIsHaveUseXunleiDownload(false);
										DownloadUtils.trackXLSwitchTrigerEvent(DownloadSettingActivity.this, false);
								}
							}).setOnCancelListener(new DialogInterface.OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									mXunleiUsagePref.setChecked(true);
									
								}
							});
					dialog.show();
				} else {
					mXunleiUsagePref.setChecked(checked);
						PreferenceLogic.getInstance().setIsHaveUseXunleiDownload(checked);
						DownloadUtils.trackXLSwitchTrigerEvent(DownloadSettingActivity.this, checked);
				}
				return true;
			}
		});// */
		if (miui.os.Build.IS_CTS_BUILD || miui.os.Build.IS_INTERNATIONAL_BUILD) 
		//if (true) 
		{
			if(mPreferenceCategory != null)
				mPreferenceCategory.removePreference(mXunleiUsagePref);
		}
	}

	@Override
	public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
		mLimitedSizePref.setSummary(getMaxBytesSummary());
		int index = picker.getValue();
		Long value = Long.valueOf(mValues[index]);
		ContentValues values = new ContentValues();
		values.put(Settings.Global.DOWNLOAD_RECOMMENDED_MAX_BYTES_OVER_MOBILE, value);
		getContentResolver().update(sRecommendedBytesLimitUri, values, null, null);
	}

	private String getMaxBytesSummary() {
		int index = mSizeLimitPicker.getValue();
		if (index == (mValues.length - 1)) {
			return getString(R.string.pref_summary_any_bytes_popup);
		} else if (index == 0) {
			return getString(R.string.pref_summary_no_limit_popup);
		}

		String newEntry = String.valueOf(mEntries[index]);
		return getString(R.string.pref_summary_max_bytes_limit_format, newEntry);
	}
}
