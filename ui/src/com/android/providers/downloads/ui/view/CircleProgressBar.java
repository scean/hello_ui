package com.android.providers.downloads.ui.view;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

public class CircleProgressBar extends ProgressBar {

    public interface OnProgressChangedListener {
        void onProgressChanged();
    }

    static final private int ALPHA_NEED_DRAW_MIN_VALUE = 10;
    static final private int DEFAULT_ROTATE_VELOCITY = 300; // angle/s
    static final private int DEFAULT_FADE_OUT_DURATION = 300;

    private RectF mArcRect;
    private Animator mChangeProgressAnimator;
    private int[] mProgressLevels;
    private Drawable[] mLevelsBackDrawable;
    private Drawable[] mLevelsMiddleDrawable;
    private Drawable[] mLevelsForeDrawable;
    private OnProgressChangedListener mProgressChangedListener;

    private int mCurrentLevel;
    private Bitmap mBitmapForSoftLayer;
    private Canvas mCanvasForSoftLayer;
    private Paint mPaint;

    // for fade animation
    private int mPrevLevel;
    private int mPrevAlpha;
    private int mRotateVelocity = DEFAULT_ROTATE_VELOCITY;
    private Drawable mThumb;

    {
        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);

        if (System.currentTimeMillis() == 0) {
            // 永不执行的代码。防止编译器不编译这些为Animator准备的方法
            setPrevAlpha(getPrevAlpha());
        }
    }

    public CircleProgressBar(Context context) {
        this(context, null);
    }

    public CircleProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleProgressBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setIndeterminate(false);
    }

    /**
     * 设置进度等级。最后一级默认为max值。因此：等级数量=progressLevels.length+1
     */
    public void setProgressLevels(int[] progressLevels) {
        mProgressLevels = progressLevels;
    }

    /**
     * 等级的数量。
     */
    public int getProgressLevelCount() {
        return mProgressLevels == null ? 1 : mProgressLevels.length + 1;
    }

    /**
     * 设置进度等级对应的3层Drawable。每个Drawable[]的length应该等于等级的数量：getProgressLevelCount()。
     * middle将会被用来切割成扇形显示，以表达进度。
     */
    public void setDrawablesForLevels(Drawable[] backs, Drawable[] middles, Drawable[] fores) {
        mLevelsBackDrawable = backs;
        mLevelsMiddleDrawable = middles;
        mLevelsForeDrawable = fores;

        /* mutate所有drawable */
        if (backs != null) {
            for (Drawable drawable : backs) {
                drawable.mutate();
            }
        }
        if (middles != null) {
            for (Drawable drawable : middles) {
                drawable.mutate();
            }
        }
        if (fores != null) {
            for (Drawable drawable : fores) {
                drawable.mutate();
            }
        }

        /* 中间转圈的层，设置其PorterDuff模式，为draw做的准备 */
        for (Drawable drawable : middles) {
            if (drawable instanceof BitmapDrawable) {
                ((BitmapDrawable) drawable).getPaint().setXfermode(
                        new PorterDuffXfermode(Mode.SRC_IN));
            } else if (drawable instanceof NinePatchDrawable) {
                ((NinePatchDrawable) drawable).getPaint().setXfermode(
                        new PorterDuffXfermode(Mode.SRC_IN));
            } else {
                throw new IllegalArgumentException(
                        "'middles' must a bitmap or nine patch drawable.");
            }
        }

        /* 计算切割扇形的区域大小 */
        mArcRect = new RectF(middles[0].getBounds().left - 5, middles[0].getBounds().top - 5,
                middles[0].getBounds().right + 5, middles[0].getBounds().bottom + 5);
    }

    /**
     * 设置进度等级对应的3层Drawable。每个int[]的length应该等于等级的数量：getProgressLevelCount()。
     * middle将会被用来切割成扇形显示，以表达进度。
     */
    public void setDrawablesForLevels(int[] resourceIdBacks, int[] resourceIdMiddles,
            int[] resourceIdFores) {
        setDrawablesForLevels(getDrawables(resourceIdBacks), getDrawables(resourceIdMiddles),
                getDrawables(resourceIdFores));
    }

    private Drawable[] getDrawables(int[] resourceIds) {
        if (resourceIds == null)
            return null;

        Resources resources = getContext().getResources();
        Drawable[] drawables = new Drawable[resourceIds.length];
        for (int i = 0; i < resourceIds.length; i++) {
            drawables[i] = resources.getDrawable(resourceIds[i]);
            drawables[i].setBounds(0, 0, drawables[i].getIntrinsicWidth(),
                    drawables[i].getIntrinsicHeight());
        }
        return drawables;
    }

    private Drawable getBackDrawable(int level) {
        return mLevelsBackDrawable == null ? null : mLevelsBackDrawable[level];
    }

    private Drawable getMiddleDrawable(int level) {
        return mLevelsMiddleDrawable == null ? null : mLevelsMiddleDrawable[level];
    }

    private Drawable getForeDrawable(int level) {
        return mLevelsForeDrawable == null ? null : mLevelsForeDrawable[level];
    }

    /**
     * 设置Animator的旋转速度。单位：angle/s。默认300。
     */
    public void setRotateVelocity(int velocity) {
        mRotateVelocity = velocity;
    }

    public void setProgressByAnimator(int progress) {
        setProgressByAnimator(progress, null);
    }

    public void setOnProgressChangedListener(OnProgressChangedListener progressChangedListener) {
        this.mProgressChangedListener = progressChangedListener;
    }

    public void setProgressByAnimator(int progress, AnimatorListener listener) {
        stopProgressAnimator();
        int offsetAngle = Math.abs((int) ((progress - getProgress()) / ((float) getMax()) * 360));
        mChangeProgressAnimator = ObjectAnimator.ofInt(this, "progress", progress);
        mChangeProgressAnimator.setDuration(calcDuration(offsetAngle));
        mChangeProgressAnimator.setInterpolator(getInterpolator());
        if (listener != null)
            mChangeProgressAnimator.addListener(listener);
        mChangeProgressAnimator.start();
    }

    public void stopProgressAnimator() {
        if (mChangeProgressAnimator != null && mChangeProgressAnimator.isRunning()) {
            mChangeProgressAnimator.cancel();
        }
    }

    private int calcDuration(int angle) {
        return angle * 1000 / mRotateVelocity;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        int size = getProgressLevelCount();
        for (int i = 0; i < size; i++) {
            if (mLevelsBackDrawable != null) mLevelsBackDrawable[i].setState(getDrawableState());
            if (mLevelsMiddleDrawable != null) mLevelsMiddleDrawable[i].setState(getDrawableState());
            if (mLevelsForeDrawable != null) mLevelsForeDrawable[i].setState(getDrawableState());
        }
        invalidate();
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);

        int newLevel = -1;
        if (mProgressLevels == null) {
            newLevel = 0;
        } else {
            int size = mProgressLevels.length;
            for (int i = 0; i < size; i++) {
                if (progress < mProgressLevels[i]) {
                    newLevel = i;
                    break;
                }
            }
            if (newLevel == -1)
                newLevel = size;
        }

        if (newLevel != mCurrentLevel) {
            /* 内存级别改变了，则需要渐变动画 */
            mPrevLevel = mCurrentLevel;
            mCurrentLevel = newLevel;
            setPrevAlpha(255);
            Animator fadeOutAnimator = ObjectAnimator.ofInt(this, "prevAlpha", 0);
            fadeOutAnimator.setDuration(DEFAULT_FADE_OUT_DURATION);
            fadeOutAnimator.setInterpolator(new LinearInterpolator());
            fadeOutAnimator.start();
        }

        if (mProgressChangedListener != null) {
            mProgressChangedListener.onProgressChanged();
        }
    }

    private float getRate() {
        return ((float) getProgress()) / getMax();
    }

    private int getIntrinsicWidth() {
        int minWidth = getMiddleDrawable(0).getIntrinsicWidth();
        if (mLevelsForeDrawable != null)
            minWidth = Math.max(minWidth, mLevelsForeDrawable[0].getIntrinsicWidth());
        if (mLevelsBackDrawable != null)
            minWidth = Math.max(minWidth, mLevelsBackDrawable[0].getIntrinsicWidth());
        return minWidth;
    }

    private int getIntrinsicHeight() {
        int minHeight = getMiddleDrawable(0).getIntrinsicHeight();
        if (mLevelsForeDrawable != null)
            minHeight = Math.max(minHeight, mLevelsForeDrawable[0].getIntrinsicHeight());
        if (mLevelsBackDrawable != null)
            minHeight = Math.max(minHeight, mLevelsBackDrawable[0].getIntrinsicHeight());
        return minHeight;
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getIntrinsicWidth(), getIntrinsicHeight());
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        // 画当前内存级别的层
        drawLayer(canvas, getBackDrawable(mCurrentLevel), getForeDrawable(mCurrentLevel),
                getMiddleDrawable(mCurrentLevel), getRate(), 255 - mPrevAlpha);

        // 若在渐变期间，则需要画上次内存级别对应相应alpha值的层
        if (mPrevAlpha >= ALPHA_NEED_DRAW_MIN_VALUE) {
            drawLayer(canvas, getBackDrawable(mPrevLevel), getForeDrawable(mPrevLevel),
                    getMiddleDrawable(mPrevLevel), getRate(), mPrevAlpha);
        }
    }

    private void drawLayer(Canvas canvas, Drawable back, Drawable fore,
            Drawable middle, float rate, int alpha) {
        // 画背景层
        if (back != null) {
            back.setAlpha(alpha);
            back.draw(canvas);
        }

        // 画中间层：转动的圈
        if (canvas.isHardwareAccelerated()) {
            canvas.saveLayer(middle.getBounds().left, middle.getBounds().top,
                    middle.getBounds().right, middle.getBounds().bottom, null,
                    Canvas.CLIP_TO_LAYER_SAVE_FLAG);
            canvas.drawArc(mArcRect, -90, 360 * rate, true, mPaint);
            middle.setAlpha(alpha);
            middle.draw(canvas);
            canvas.restore();
        } else {
            if (mBitmapForSoftLayer == null) {
                mBitmapForSoftLayer = Bitmap.createBitmap(middle.getBounds().width(),
                        middle.getBounds().height(), Config.ARGB_8888);
                mCanvasForSoftLayer = new Canvas(mBitmapForSoftLayer);
            }

            mBitmapForSoftLayer.eraseColor(0);
            mCanvasForSoftLayer.save();
            mCanvasForSoftLayer.translate(-middle.getBounds().left, -middle.getBounds().top);
            mCanvasForSoftLayer.drawArc(mArcRect, -90, 360 * rate, true, mPaint);
            middle.setAlpha(alpha);
            middle.draw(mCanvasForSoftLayer);
            mCanvasForSoftLayer.restore();

            canvas.drawBitmap(mBitmapForSoftLayer, middle.getBounds().left, middle.getBounds().top,
                    null);
        }

        final Drawable thumb = mThumb;
        if (thumb != null) {
            canvas.save();
            final int x = (getWidth() - getPaddingLeft() - getPaddingRight()) / 2;
            final int y = (getHeight() - getPaddingTop() - getPaddingBottom()) / 2;
            final int w = thumb.getIntrinsicWidth();
            final int h = thumb.getIntrinsicHeight();
            canvas.rotate(360F * getProgress() / getMax(), x, y);
            thumb.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
            thumb.draw(canvas);
            canvas.restore();
        }

        // 画前景层
        if (fore != null) {
            fore.setAlpha(alpha);
            fore.draw(canvas);
        }
    }

    public void setPrevAlpha(int alpha) {
        this.mPrevAlpha = alpha;
        invalidate();
    }

    public int getPrevAlpha() {
        return this.mPrevAlpha;
    }

    public void setThumb(int resId) {
        setThumb(getResources().getDrawable(resId));
    }

    public void setThumb(Drawable thumb) {
        mThumb = thumb;
    }
}
