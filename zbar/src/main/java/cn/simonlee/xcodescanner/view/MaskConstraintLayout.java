package cn.simonlee.xcodescanner.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.view.View;

import cn.simonlee.xcodescanner.R;

/**
 * 带阴影的ConstraintLayout
 * 可根据frame_viewid属性指定绘制阴影的View，若无指定，则默认指定为ScannerFrameView
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-04-10
 */
@SuppressWarnings("unused")
public class MaskConstraintLayout extends ConstraintLayout {

    /**
     * 扫描框外部颜色
     */
    private int mFrameOutsideColor;

    /**
     * 画笔（用来绘制扫描框外部区域）
     */
    private Paint mPaint = new Paint();

    /**
     * 扫描框ViewId
     */
    private int mFrameViewId;

    public MaskConstraintLayout(Context context) {
        super(context);
    }

    public MaskConstraintLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public MaskConstraintLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attributeSet) {
        TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.MaskConstraintLayout);
        this.mFrameOutsideColor = typedArray.getColor(R.styleable.MaskConstraintLayout_frame_outsideColor, 0x60000000);
        this.mFrameViewId = typedArray.getResourceId(R.styleable.MaskConstraintLayout_frame_viewid, 0);
        typedArray.recycle();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (child != null && child.getVisibility() != View.GONE) {
            if (mFrameViewId != 0) {
                if (child.getId() == mFrameViewId) {
                    drawFrameOutside(canvas, child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                }
            } else if (child instanceof ScannerFrameView) {
                drawFrameOutside(canvas, child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            }
        }
        return more;
    }

    private void drawFrameOutside(Canvas canvas, int childLeft, int childTop, int childRight, int childBottom) {
        int width = Math.min(getMeasuredWidth(), canvas.getWidth());
        int height = Math.min(getMeasuredHeight(), canvas.getHeight());

        // 画出扫描框外面的阴影部分，共四个部分
        mPaint.setColor(mFrameOutsideColor);
        canvas.drawRect(0, 0, width, childTop, mPaint);//上部分
        canvas.drawRect(0, childTop, childLeft, childBottom, mPaint);//左部分
        canvas.drawRect(childRight, childTop, width, childBottom, mPaint);//右部分
        canvas.drawRect(0, childBottom, width, height, mPaint);//下部分
    }

    /**
     * 设置扫描框外部填充色
     *
     * @param frameOutsideColor 十六进制色值
     */
    public void setFrameOutsideColor(@ColorInt int frameOutsideColor) {
        this.mFrameOutsideColor = frameOutsideColor;
    }

}
