package com.android.providers.downloads.ui.utils;

/*
 * Copyright (C) 2006 The Android Open Source Project
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
import android.R.integer;
import android.content.Context;
import android.net.NetworkUtils;
import com.android.providers.downloads.ui.utils.DownloadUtils;
/*
 * Utility class to aid in formatting common values that are not covered
 * by the {@link java.util.Formatter} class in {@link java.util}
 */
public final class SpeedFormatter {

    /**
     * Formats a content size to be in the form of bytes, kilobytes, megabytes, etc
     *
     * @param context Context to use to load the localized units
     * @param number size value to be formatted
     * @return formatted string with the number
     */

    public static String formatFileSize(Context context, long number){
        if (context == null) {
            return "";
        }

        float result = number;
        int suffix = com.android.internal.R.string.byteShort;
        if (result >= 1024) {
            suffix = com.android.internal.R.string.kilobyteShort;
            result = result / 1024;
        }
        if (result >= 1024) {
            suffix = com.android.internal.R.string.megabyteShort;
            result = result / 1024;
        }
        if (result >= 1024) {
            suffix = com.android.internal.R.string.gigabyteShort;
            result = result / 1024;
        }
        if (result >= 1024) {
            suffix = com.android.internal.R.string.terabyteShort;
            result = result / 1024;
        }
        if (result >= 1024) {
            suffix = com.android.internal.R.string.petabyteShort;
            result = result / 1024;
        }
        String value;
        switch(suffix)
        {
        	case com.android.internal.R.string.byteShort:
        	case com.android.internal.R.string.kilobyteShort:
        	{
        		value=String.valueOf((int)result);
        		break;
        	}
        	default:
        	{
        		try{
        		value=String.format("%.1f", result);
        		double myValue=Double.valueOf(value);
        		if(DownloadUtils.isNumGood(myValue))
        		{
        			value=String.valueOf((int)result);
        		}
        		}catch (Exception e){
        			return "";
        		}
        	}
        }
        
        return context.getResources().
            getString(com.android.internal.R.string.fileSizeSuffix,
                      value, DownloadUtils.formatUnit(context.getString(suffix)));
    }
}

