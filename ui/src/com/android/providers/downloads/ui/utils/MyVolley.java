/**
 * Copyright 2013 Ognyan Bankov
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
package com.android.providers.downloads.ui.utils;

import android.content.Context;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import com.android.providers.downloads.ui.network.ExtHttpClientStack;
import com.android.providers.downloads.ui.network.SSLSocketFactoryEx;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

/**
 * Helper class that is used to provide references to initialized RequestQueue(s) and ImageLoader(s)
 *
 * @author Ognyan Bankov
 */
public class MyVolley {
	private RequestQueue mRequestQueue;
	private RequestQueue mHttpsRequestQueue;
	private ImageLoader mImageLoader;

	public ParcelFileDescriptor getParcelFile() {
		return mParcelFile;
	}

	public void setParcelFile(ParcelFileDescriptor mParcelFile) {
		this.mParcelFile = mParcelFile;
	}

	private ParcelFileDescriptor mParcelFile;

	private MyVolley() {
		// no instances
	}

	private static MyVolley instance;

	public synchronized static MyVolley getInstance(Context context) {

		if (instance == null) {
			instance = new MyVolley();
			instance.initWithContext(context);
		} else {
		}
		return instance;
	}

	void initWithContext(Context context) {
		mRequestQueue = Volley.newRequestQueue(context, new ExtHttpClientStack(SSLSocketFactoryEx.getNewHttpClient()));
		mHttpsRequestQueue = mRequestQueue;

//        int memClass = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
//                .getMemoryClass();
//        // Use 1/8th of the available memory for this memory cache.
//        int cacheSize = 1024 * 1024 * memClass / 8;
		mImageLoader = new ImageLoader(mRequestQueue, new BitmapCache());
	}


	public RequestQueue getRequestQueue() {
		if (mRequestQueue != null) {
			return mRequestQueue;
		} else {
			return null;
		}
	}

	public RequestQueue getHttpsRequestQueue() {
		if (mHttpsRequestQueue != null) {
			return mHttpsRequestQueue;
		} else {
			return null;
		}
	}


	/**
	 * Returns instance of ImageLoader initialized with {@see FakeImageCache} which effectively means
	 * that no memory caching is used. This is useful for images that you know that will be show
	 * only once.
	 *
	 * @return
	 */
	public ImageLoader getImageLoader() {
		if (mImageLoader != null) {
			return mImageLoader;
		} else {
			return null;
		}
	}

	public class BitmapCache implements ImageLoader.ImageCache {

		private LruCache<String, Bitmap> mCache;

		public BitmapCache() {
			int maxSize = 10 * 1024 * 1024;
			mCache = new LruCache<String, Bitmap>(maxSize) {
				@Override
				protected int sizeOf(String key, Bitmap bitmap) {
					return bitmap.getRowBytes() * bitmap.getHeight();
				}
			};
		}

		@Override
		public Bitmap getBitmap(String url) {
			return mCache.get(url);
		}

		@Override
		public void putBitmap(String url, Bitmap bitmap) {
			mCache.put(url, bitmap);
		}

	}

}
