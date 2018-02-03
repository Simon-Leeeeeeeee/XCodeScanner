package com.demo.camera2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 */
public final class ScannerFrameView extends View {

    //画笔
    private Paint mPaint = new Paint();

    //手机的屏幕密度
    private float density;
    //扫描框宽
    private int mFrameWidth;
    //扫描框高
    private int mFrameHeight;
    //扫描框宽占比
    private float mFrameWidthRatio;
    //扫描框高宽比
    private float mFrameHWRatio;
    //扫描框上边距
    private int mFramePaddingTop;
    //扫描框上下边距比例
    private float mFrameVerticalPaddingRatio;
    //扫描框矩形区
    private Rect mFrameRect = new Rect();
    //扫描框外部颜色
    private int mFrameOutsideColor;

    //扫描框边线是否显示
    private boolean isFrameLineVisible;
    //扫描框边线颜色
    private int mFrameLineColor;
    //扫描框边线宽度
    private int mFrameLineWidth;

    //扫描框边角长度
    private int mFrameCornerLength;
    //扫描框边角长占比
    private float mFrameCornerLengthRatio;
    //扫描框边角宽度
    private int mFrameCornerWidth;
    //扫描框边角颜色
    private int mFrameCornerColor;

    //扫描线是否显示
    private boolean isScanLineVisible;
    //扫描线移动方向
    private int mScanLineDirection;
    //扫描线轨迹Padding
    private int mScanLineTrajectoryPadding;
    //扫描线两端Padding
    private int mScanLinePaddingEnd;
    //扫描线长度比例（相对扫描框的宽/高，依方向而定）
    private float mScanLineLengthRatio;
    //扫描线宽度
    private int mScanLineWidth;
    //扫描线颜色
    private int mScanLineColor;
    //扫描线当前偏移量
    private int mCurScanLineOffset;
    //扫描线移动轨迹长度
    private int mScanLineTrajectory;
    //扫描线移动频率（单位毫秒）
    private int mScanLineFrequency;
    //扫描线刷新时间间隔（单位毫秒）
    private int mScanLineInvalidateDelay;

    //扫描线单次移动距离（单位像素）
    private static final int STEP_DISTANCE = 5;

    public static class Direction {
        public static final int TOP = 1;
        public static final int BOTTOM = 2;
        public static final int LEFT = 3;
        public static final int RIGHT = 4;
    }

    public ScannerFrameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public ScannerFrameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attributeSet) {
        density = context.getResources().getDisplayMetrics().density;
        TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ScannerFrameView);
        this.mFrameWidth = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frame_width, 0);
        this.mFrameHeight = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frame_height, 0);
        this.mFramePaddingTop = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frame_paddingTop, -1);
        if (mFrameWidth <= 0) {
            this.mFrameWidthRatio = typedArray.getFloat(R.styleable.ScannerFrameView_frame_widthRatio, 0.66666F);
            if (mFrameWidthRatio <= 0 || mFrameWidthRatio > 1) {
                mFrameWidthRatio = 0.66666F;
            }
        }
        if (mFrameHeight <= 0) {
            this.mFrameHWRatio = typedArray.getFloat(R.styleable.ScannerFrameView_frame_hwRatio, 1F);
            if (mFrameHWRatio <= 0) {
                mFrameHWRatio = 1F;
            }
        }
        if (mFramePaddingTop < 0) {
            this.mFrameVerticalPaddingRatio = typedArray.getFloat(R.styleable.ScannerFrameView_frame_verticalPaddingRatio, 0.5F);
            if (mFrameVerticalPaddingRatio < 0) {
                mFrameVerticalPaddingRatio = 0.5F;
            }
        }
        this.mFrameOutsideColor = typedArray.getColor(R.styleable.ScannerFrameView_frame_outsideColor, 0x60000000);

        this.isFrameLineVisible = typedArray.getBoolean(R.styleable.ScannerFrameView_frameLine_visible, true);
        if (isFrameLineVisible) {
            this.mFrameLineWidth = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frameLine_width, (int) (density));
            this.mFrameLineColor = typedArray.getColor(R.styleable.ScannerFrameView_frameLine_color, Color.WHITE);
        }

        this.mFrameCornerLength = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frameCorner_length, 0);
        if (mFrameCornerLength <= 0) {
            this.mFrameCornerLengthRatio = typedArray.getFloat(R.styleable.ScannerFrameView_frameCorner_lengthRatio, 0.07143F);
            if (mFrameCornerLengthRatio <= 0 || mFrameCornerLengthRatio > 0.5F) {
                mFrameCornerLengthRatio = 0.07143F;
            }
        }
        this.mFrameCornerWidth = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_frameCorner_width, (int) (3 * density));
        this.mFrameCornerColor = typedArray.getColor(R.styleable.ScannerFrameView_frameCorner_color, Color.BLUE);

        this.isScanLineVisible = typedArray.getBoolean(R.styleable.ScannerFrameView_scanLine_visible, true);
        if (isScanLineVisible) {
            this.mScanLineDirection = typedArray.getInt(R.styleable.ScannerFrameView_scanLine_direction, Direction.BOTTOM);
            this.mScanLineTrajectoryPadding = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_scanLine_trajectoryPadding, (int) (3 * density));
            this.mScanLinePaddingEnd = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_scanLine_paddingEnd, -1);
            if (mScanLinePaddingEnd < 0) {
                this.mScanLineLengthRatio = typedArray.getFloat(R.styleable.ScannerFrameView_scanLine_lengthRatio, 0.99F);
                if (mScanLineLengthRatio <= 0 || mScanLineLengthRatio > 1F) {
                    mScanLineLengthRatio = 0.99F;
                }
            }
            mScanLinePaddingEnd += mFrameLineWidth;
            this.mScanLineWidth = typedArray.getDimensionPixelSize(R.styleable.ScannerFrameView_scanLine_width, (int) (2 * density));
            this.mScanLineColor = typedArray.getColor(R.styleable.ScannerFrameView_scanLine_color, Color.BLUE);
            this.mScanLineFrequency = typedArray.getInt(R.styleable.ScannerFrameView_scanLine_frequency, 1500);
        }
        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int mMeasuredHeight = getMeasuredHeight();
        int mMeasuredWidth = getMeasuredWidth();
        if (mMeasuredWidth > 0 && mMeasuredHeight > 0) {
            if (mFrameWidthRatio > 0) {//按比例计算扫描框的宽度
                mFrameWidth = (int) (mFrameWidthRatio * mMeasuredWidth);
            }
            if (mFrameHWRatio > 0) {//按比例计算扫描框的高度
                mFrameHeight = (int) (mFrameHWRatio * mFrameWidth);
            }
            int leftOffset = (mMeasuredWidth - mFrameWidth) / 2;//计算扫描框左边距
            if (mFrameVerticalPaddingRatio > 0) {//按比例计算扫描框上边距
                float topOffsetRatio = mFrameVerticalPaddingRatio / (mFrameVerticalPaddingRatio + 1);
                mFramePaddingTop = (int) ((mMeasuredHeight - mFrameHeight) * topOffsetRatio);
            }
            mFrameRect.set(leftOffset, mFramePaddingTop, leftOffset + mFrameWidth, mFramePaddingTop + mFrameHeight);

            if (mFrameCornerLengthRatio > 0) {//按比例计算扫描框边角长度
                mFrameCornerLength = (int) (mFrameWidth * mFrameCornerLengthRatio);
            }
            if (isScanLineVisible) {
                if (mScanLineLengthRatio > 0 && mScanLineLengthRatio <= 1) {//按比例计算扫描线两端padding
                    int maxLength = (mScanLineDirection == Direction.TOP || mScanLineDirection == Direction.BOTTOM) ? mFrameWidth : mFrameHeight;
                    mScanLinePaddingEnd = (int) (maxLength * (1 - mScanLineLengthRatio) / 2);
                    mScanLinePaddingEnd += mFrameLineWidth;
                }
                int distance = (mScanLineDirection == Direction.TOP || mScanLineDirection == Direction.BOTTOM) ? mFrameHeight : mFrameWidth;
                mScanLineTrajectory = distance - mScanLineTrajectoryPadding * 2 - mFrameLineWidth * 2 - mScanLineWidth;
                int stepCount = mScanLineTrajectory / STEP_DISTANCE;//总长除以单步距离，此处会有余数。在onDraw中对余数进行处理
                mScanLineInvalidateDelay = mScanLineFrequency / stepCount;
            }
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mFrameRect.isEmpty()) {
            return;
        }

        //获取屏幕的宽和高
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        //画出扫描框外面的阴影部分，共四个部分
        mPaint.setColor(mFrameOutsideColor);
        canvas.drawRect(0, 0, width, mFrameRect.top, mPaint);//上部分
        canvas.drawRect(0, mFrameRect.top, mFrameRect.left, mFrameRect.bottom, mPaint);//左部分
        canvas.drawRect(mFrameRect.right, mFrameRect.top, width, mFrameRect.bottom, mPaint);//右部分
        canvas.drawRect(0, mFrameRect.bottom, width, height, mPaint);//下部分

        //画扫描框的四条边线
        if (isFrameLineVisible) {
            mPaint.setColor(mFrameLineColor);
            canvas.drawRect(mFrameRect.left, mFrameRect.top, mFrameRect.right, mFrameRect.top + mFrameLineWidth, mPaint);//上边线
            canvas.drawRect(mFrameRect.left, mFrameRect.top, mFrameRect.left + mFrameLineWidth, mFrameRect.bottom, mPaint);//左边线
            canvas.drawRect(mFrameRect.right - mFrameLineWidth, mFrameRect.top, mFrameRect.right, mFrameRect.bottom, mPaint);//右边线
            canvas.drawRect(mFrameRect.left, mFrameRect.bottom - mFrameLineWidth, mFrameRect.right, mFrameRect.bottom, mPaint);//下边线
        }

        if (isScanLineVisible) {
            int remainderPadding = (mScanLineTrajectory % STEP_DISTANCE) / 2;//取出起止点的余数，并对余数进行处理
            if (mCurScanLineOffset < mFrameLineWidth + mScanLineTrajectoryPadding + remainderPadding
                    || mCurScanLineOffset > mScanLineTrajectory + mFrameLineWidth + mScanLineTrajectoryPadding) {
                mCurScanLineOffset = mFrameLineWidth + mScanLineTrajectoryPadding + remainderPadding;//超出距离范围，回到起点
            }
            mPaint.setColor(mScanLineColor);
            //TODO 根据扫描方向，当前偏移量，计算当次扫描线位置
            switch (mScanLineDirection) {
                case Direction.TOP: {
                    canvas.drawRect(mFrameRect.left + mScanLinePaddingEnd,
                            mFrameRect.bottom - mCurScanLineOffset - mScanLineWidth,
                            mFrameRect.right - mScanLinePaddingEnd,
                            mFrameRect.bottom - mCurScanLineOffset, mPaint);
                    break;
                }
                case Direction.BOTTOM: {
                    canvas.drawRect(mFrameRect.left + mScanLinePaddingEnd,
                            mFrameRect.top + mCurScanLineOffset,
                            mFrameRect.right - mScanLinePaddingEnd,
                            mFrameRect.top + mCurScanLineOffset + mScanLineWidth, mPaint);
                    break;
                }
                case Direction.LEFT: {
                    canvas.drawRect(mFrameRect.left + mCurScanLineOffset,
                            mFrameRect.top + mScanLinePaddingEnd,
                            mFrameRect.left + mCurScanLineOffset + mScanLineWidth,
                            mFrameRect.bottom - mScanLinePaddingEnd, mPaint);
                    break;
                }
                case Direction.RIGHT: {
                    canvas.drawRect(mFrameRect.right - mCurScanLineOffset - mScanLineWidth,
                            mFrameRect.top + mScanLinePaddingEnd,
                            mFrameRect.right - mCurScanLineOffset,
                            mFrameRect.bottom - mScanLinePaddingEnd, mPaint);
                    break;
                }
            }
            mCurScanLineOffset += STEP_DISTANCE;
            postInvalidateDelayed(mScanLineInvalidateDelay, mFrameRect.left, mFrameRect.top, mFrameRect.right, mFrameRect.bottom);
        }

        //画扫描框四个边角，共八个部分
        mPaint.setColor(mFrameCornerColor);
        canvas.drawRect(mFrameRect.left, mFrameRect.top, mFrameRect.left + mFrameCornerLength, mFrameRect.top + mFrameCornerWidth, mPaint);//左上角横
        canvas.drawRect(mFrameRect.left, mFrameRect.top, mFrameRect.left + mFrameCornerWidth, mFrameRect.top + mFrameCornerLength, mPaint);//左上角竖
        canvas.drawRect(mFrameRect.left, mFrameRect.bottom - mFrameCornerWidth, mFrameRect.left + mFrameCornerLength, mFrameRect.bottom, mPaint);//左下角横
        canvas.drawRect(mFrameRect.left, mFrameRect.bottom - mFrameCornerLength, mFrameRect.left + mFrameCornerWidth, mFrameRect.bottom, mPaint);//左下角竖
        canvas.drawRect(mFrameRect.right - mFrameCornerLength, mFrameRect.top, mFrameRect.right, mFrameRect.top + mFrameCornerWidth, mPaint);//右上角横
        canvas.drawRect(mFrameRect.right - mFrameCornerLength, mFrameRect.bottom - mFrameCornerWidth, mFrameRect.right, mFrameRect.bottom, mPaint);//右下角横
        canvas.drawRect(mFrameRect.right - mFrameCornerWidth, mFrameRect.top, mFrameRect.right, mFrameRect.top + mFrameCornerLength, mPaint);//右上角竖
        canvas.drawRect(mFrameRect.right - mFrameCornerWidth, mFrameRect.bottom - mFrameCornerLength, mFrameRect.right, mFrameRect.bottom, mPaint);//右下角竖
    }

    public void setFrameOutsideColor(int frameOutsideColor) {
        this.mFrameOutsideColor = frameOutsideColor;
    }

}
