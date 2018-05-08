package cn.simonlee.xcodescanner.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import cn.simonlee.xcodescanner.R;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-02-02
 */
@SuppressWarnings("unused")
public class MaskRelativeLayout extends RelativeLayout {

    /**
     * 扫描框外部颜色
     */
    private int mFrameOutsideColor;

    /**
     * 画笔（用来绘制扫描框外部区域）
     */
    private Paint mPaint = new Paint();

    public MaskRelativeLayout(Context context) {
        super(context);
    }

    public MaskRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public MaskRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attributeSet) {
        TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.MaskRelativeLayout);
        this.mFrameOutsideColor = typedArray.getColor(R.styleable.MaskRelativeLayout_frame_outsideColor, 0x60000000);
        typedArray.recycle();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = super.drawChild(canvas, child, drawingTime);
        if (child != null && child instanceof ScannerFrameView && child.getVisibility() != GONE) {
            drawFrameOutside(canvas, child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
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
