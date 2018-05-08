package cn.simonlee.xcodescanner.core;

import android.content.Context;
import android.graphics.SurfaceTexture;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-04-13  11:24
 */
public interface CameraScanner {

    /**
     * 开启相机，未避免内存泄漏，这里建议传ApplicationContext
     */
    void openCamera(Context context);

    /**
     * 关闭相机
     */
    void closeCamera();

    /**
     * 打开闪光灯
     */
    void openFlash();

    /**
     * 关闭闪光灯
     */
    void closeFlash();

    void detach();

    /**
     * 设置预览View的尺寸
     */
    void setPreviewSize(int width, int height);

    /**
     * 设置SurfaceTexture
     */
    void setSurfaceTexture(SurfaceTexture surfaceTexture);

    /**
     * 设置相机事件监听
     */
    void setCameraListener(CameraListener cameraListener);

    /**
     * 设置图像解码器
     */
    void setGraphicDecoder(GraphicDecoder graphicDecoder);

    /**
     * 设置扫码识别区域
     * 计算识别区域在图像帧中的位置，并根据屏幕方向进行校正
     * 注意：该区域坐标相对于图像帧的左上角顶点
     */
    void setFrameRect(int frameLeft, int frameTop, int frameRight, int frameBottom);

    interface CameraListener {

        void openCameraSuccess(int surfaceWidth, int surfaceHeight, int surfaceDegree);

        void openCameraError();

        void noCameraPermission();

        void cameraDisconnected();
    }

}
