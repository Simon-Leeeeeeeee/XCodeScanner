package cn.simonlee.xcodescanner.view;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-02-02
 */
@SuppressWarnings("unused")
public class AdjustTextureView extends TextureView {

    private int mFrameWidth;
    private int mFrameHeight;
    private int mFrameDegree;

    private final String TAG = "XCodeScanner";

    public AdjustTextureView(Context context) {
        this(context, null);
    }

    public AdjustTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdjustTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setImageFrameMatrix();
    }

    /**
     * 根据图像帧的宽高及角度进行校正
     */
    public void setImageFrameMatrix() {
        Log.d(TAG, getClass().getName() + ".setImageFrameMatrix()");
        setImageFrameMatrix(mFrameWidth, mFrameHeight, mFrameDegree);
    }

    /**
     * 根据图像帧的宽高及角度进行校正
     *
     * @param frameWidth  图像帧的宽
     * @param frameHeight 图像帧的高
     * @param frameDegree 图像帧需要旋转的角度(0/90/180/270)
     */
    public void setImageFrameMatrix(int frameWidth, int frameHeight, int frameDegree) {
        Log.d(TAG, getClass().getName() + ".setImageFrameMatrix() frameWH = " + frameWidth + "x" + frameHeight + " , frameDegree = " + frameDegree);
        if (frameWidth <= 0 || frameHeight <= 0) return;
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        mFrameDegree = frameDegree;

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        Matrix matrix = new Matrix();
        matrix.setRotate(frameDegree, getWidth() * 0.5F, getHeight() * 0.5F);//以中心点进行旋转
        if (frameDegree % 180 == 0) {//未发生垂直旋转，仅需调整宽高的缩放
            if (width * frameHeight < frameWidth * height) {//判断宽高比，宽被压缩
                Log.d(TAG, getClass().getName() + ".setImageFrameMatrix()A XY = " + (1F * frameWidth * height / (width * frameHeight)) + " : 1");
                matrix.postScale(1F * frameWidth * height / (width * frameHeight), 1F, 0, 0);//将宽拉伸
            } else {//高被压缩
                Log.d(TAG, getClass().getName() + ".setImageFrameMatrix()B XY = " + "1 : " + (1F * width * frameHeight / (frameWidth * height)));
                matrix.postScale(1F, 1F * width * frameHeight / (frameWidth * height), 0, 0);//将高拉伸
            }
        } else {//发生了垂直旋转，宽高都需要进行缩放，并且要进行平移操作（图像以左上角原点对齐）
            if (width * frameWidth < frameHeight * height) {//判断旋转后的宽高比
                float scaleW = 1F * frameHeight / frameWidth;
                float scaleH = 1F * height / width;
                matrix.postScale(scaleW, scaleH, width * 0.5F, height * 0.5F);
                matrix.postTranslate((scaleW * height - width) * 0.5F, 0);
            } else {
                float scaleW = 1F * width / height;
                float scaleH = 1F * frameWidth / frameHeight;
                matrix.postScale(scaleW, scaleH, width * 0.5F, height * 0.5F);
                matrix.postTranslate(0, (scaleH * width - height) * 0.5F);
            }
        }
        setTransform(matrix);
    }

}
