package com.android.providers.downloads.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class DownloadProgressBar extends LinearLayout {
    public final static int STATUS_DOWNLOADING = 0;
    public final static int STATUS_PAUSE = 1;
    public final static int STATUS_PAUSE_BY_APP = 2;
    public final static int STATUS_FAILED = 3;
    private CircleProgressBar mProgressBar;
    private int mStatus;

    static private int MAX = 100;
    static private int BEGIN = 0;

    private int progress;

    public DownloadProgressBar(Context context) {
        this(context, null);
    }

    public DownloadProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DownloadProgressBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setClickable(true);
        setFocusable(true);
        mProgressBar = new CircleProgressBar(context, attrs, defStyle);
        //mProgressBar.setClickable(true);
        mProgressBar.setMax(MAX);
        //mProgressBar.setProgressLevels(new int[]{MAX});
        mProgressBar.setDrawablesForLevels(new int[]{R.drawable.download_progress_background},
                new int[] {R.drawable.download_progress_running},
                new int[] {R.drawable.download_status_running});
        mProgressBar.setProgress(BEGIN);
        addView(mProgressBar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mStatus = STATUS_DOWNLOADING;
    }

    public void setProgress(int progress) {
        if(progress < 0){
            throw new IllegalArgumentException("progress not less than 0");
        }
        if(progress > MAX){
            progress = MAX;
        }
        if(progress <= MAX || mStatus != STATUS_PAUSE_BY_APP){
            this.progress = progress;
            mProgressBar.setProgress(this.progress);
        }

    }
    public void setDownloadStatus(int status) {
        if ((status != STATUS_DOWNLOADING && status != STATUS_PAUSE
                && status != STATUS_PAUSE_BY_APP && status != STATUS_FAILED)
                || status == mStatus) {
            return;
        }
        mStatus = status;
        switch(mStatus) {
            case STATUS_DOWNLOADING:
                mProgressBar.setDrawablesForLevels(new int[]{R.drawable.download_progress_background},
                        new int[] {R.drawable.download_progress_running},
                        new int[] {R.drawable.download_status_running});
                break;
            case STATUS_PAUSE:
            case STATUS_PAUSE_BY_APP:
                mProgressBar.setDrawablesForLevels(new int[]{R.drawable.download_progress_background},
                        new int[] {R.drawable.download_progress_running},
                        new int[] {R.drawable.download_status_pause});
                break;
            case STATUS_FAILED:
                mProgressBar.setDrawablesForLevels(new int[]{R.drawable.download_progress_background},
                        new int[] {R.drawable.download_progress_failed_and_retry},
                        new int[] {R.drawable.download_status_retry});
                break;
        }
    }
}
