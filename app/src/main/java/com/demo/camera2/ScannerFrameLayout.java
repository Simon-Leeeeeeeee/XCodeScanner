package com.demo.camera2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;

@SuppressWarnings("unused")
public class ScannerFrameLayout extends RelativeLayout {

    /**
     * 扫描框宽占比（相对View本身的宽）
     */
    private float mFrameWidthRatio;

    /**
     * 扫描框高宽比（相对扫描框的宽）
     */
    private float mFrameHWRatio;

    /**
     * 扫描框上下margin比例
     */
    private float mFrameVerticalMarginRatio;

    /**
     * 扫描框外部颜色
     */
    private int mFrameOutsideColor;

    /**
     * 扫描框矩形区
     */
    private Rect mFrameRect = new Rect();

    /**
     * 画笔（用来绘制扫描框外部区域）
     */
    private Paint mPaint = new Paint();

    /**
     * 宽占比默认值 {@link #mFrameWidthRatio}
     */
    private static final float DEFAULT_RATIO_WIDTH = 0.7F;

    /**
     * 高宽比默认值 {@link #mFrameHWRatio}
     */
    private static final float DEFAULT_RATIO_HW = 1F;

    /**
     * 仅当预览模式时有效 {@link #isInEditMode()}
     */
    private int count;

    public ScannerFrameLayout(Context context) {
        super(context);
    }

    public ScannerFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public ScannerFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attributeSet) {
        TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ScannerFrameLayout);
        this.mFrameWidthRatio = typedArray.getFloat(R.styleable.ScannerFrameLayout_frame_widthRatio, 0F);
        if (mFrameWidthRatio > 1) {
            mFrameWidthRatio = 1;
        }
        this.mFrameHWRatio = typedArray.getFloat(R.styleable.ScannerFrameLayout_frame_hwRatio, 0F);
        this.mFrameOutsideColor = typedArray.getColor(R.styleable.ScannerFrameLayout_frame_outsideColor, 0x60000000);

        this.mFrameVerticalMarginRatio = typedArray.getFloat(R.styleable.ScannerFrameLayout_frame_verticalMarginRatio, -1);
        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isInEditMode()) {//解决当父布局为RelativeLayout，本身高度未填满时，预览布局会显示错误的问题。
            ViewParent viewParent = getParent();
            if (viewParent != null && viewParent instanceof RelativeLayout) {
                if ((++count) % 2 != 0) return;
            }
        }
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            if (child != null & child instanceof ScannerFrameView) {
                updateFrameLayoutParams((ScannerFrameView) child);
                break;
            }
        }
    }

    private void updateFrameLayoutParams(ScannerFrameView scannerFrameView) {
        if (scannerFrameView == null) return;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            width = getMeasuredWidth();
            height = getMeasuredHeight();
        }
        if (width > 0 && height > 0) {
            ViewGroup.LayoutParams lp = scannerFrameView.getLayoutParams();
            LayoutParams newLayoutParams;
            if (lp instanceof LayoutParams) {
                newLayoutParams = (LayoutParams) lp;
            } else {
                newLayoutParams = new LayoutParams(lp);
            }
            if (mFrameWidthRatio > 0) {
                newLayoutParams.width = (int) (mFrameWidthRatio * width);
            } else if (newLayoutParams.width <= 0) {
                newLayoutParams.width = (int) (DEFAULT_RATIO_WIDTH * width);
            }
            if (newLayoutParams.width > width) {
                newLayoutParams.width = width;
            }
            if (mFrameHWRatio > 0) {
                newLayoutParams.height = (int) (mFrameHWRatio * newLayoutParams.width);
            } else if (newLayoutParams.height <= 0) {
                newLayoutParams.height = (int) (DEFAULT_RATIO_HW * newLayoutParams.width);
            }
            if (newLayoutParams.height > height) {
                newLayoutParams.height = height;
            }
            if (mFrameVerticalMarginRatio >= 0) {
                float topOffsetRatio = mFrameVerticalMarginRatio / (mFrameVerticalMarginRatio + 1);
                newLayoutParams.topMargin = (int) ((height - newLayoutParams.height) * topOffsetRatio);
            }
            scannerFrameView.setLayoutParams(newLayoutParams);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (child instanceof ScannerFrameView) {
            child.getHitRect(mFrameRect);
            drawFrameOutside(canvas);
        }
        return more;
    }

    private void drawFrameOutside(Canvas canvas) {
        int width = Math.min(getMeasuredWidth(), canvas.getWidth());
        int height = Math.min(getMeasuredHeight(), canvas.getHeight());

        // 画出扫描框外面的阴影部分，共四个部分
        mPaint.setColor(mFrameOutsideColor);
        canvas.drawRect(0, 0, width, mFrameRect.top, mPaint);//上部分
        canvas.drawRect(0, mFrameRect.top, mFrameRect.left, mFrameRect.bottom, mPaint);//左部分
        canvas.drawRect(mFrameRect.right, mFrameRect.top, width, mFrameRect.bottom, mPaint);//右部分
        canvas.drawRect(0, mFrameRect.bottom, width, height, mPaint);//下部分
    }

    /**
     * 设置扫描宽占比（相对View本身的宽）
     *
     * @param frameWidthRatio 宽占比
     */
    public void setFrameWidthRatio(float frameWidthRatio) {
        if (frameWidthRatio > 1) {
            frameWidthRatio = 1;
        }
        this.mFrameWidthRatio = frameWidthRatio;
    }

    /**
     * 设置扫描框高宽比（相对扫描框的宽）
     *
     * @param frameHWRatio 高宽比(height/width)
     */
    public void setFrameHWRatio(float frameHWRatio) {
        this.mFrameHWRatio = frameHWRatio;
    }

    /**
     * 设置扫描框上下margin比例
     *
     * @param frameVerticalMarginRatio 上下边距比值，有效区间(0,+∞)
     */
    public void setFrameVerticalMarginRatio(float frameVerticalMarginRatio) {
        this.mFrameVerticalMarginRatio = frameVerticalMarginRatio;
    }

    /**
     * 设置扫描框外部填充色
     *
     * @param frameOutsideColor 十六进制色值
     */
    public void setFrameOutsideColor(@ColorInt int frameOutsideColor) {
        this.mFrameOutsideColor = frameOutsideColor;
    }

    /**
     * 返回扫码框区域
     */
    public Rect getFrameRect() {
        return mFrameRect;
    }
}
