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

    String TAG = "XCodeScanner";

    /**
     * 开启相机成功
     */
    int HANDLER_SUCCESS_OPEN = 70001;
    /**
     * 开启相机失败
     */
    int HANDLER_FAIL_OPEN = 80001;
    /**
     * 相机关闭
     */
    int HANDLER_FAIL_CLOSED = 80002;
    /**
     * 相机配置失败
     */
    int HANDLER_FAIL_CONFIG = 80003;
    /**
     * 相机会话创建失败
     */
    int HANDLER_FAIL_CREATSESSION = 80004;
    /**
     * 相机断开连接
     */
    int HANDLER_FAIL_DISCONNECTED = 80005;
    /**
     * 没有相机权限
     */
    int HANDLER_FAIL_NO_PERMISSION = 80006;
    /**
     * 超时
     */
    int HANDLER_FAIL_TIMEOUT = 80007;
    /**
     * 亮度发生变化
     */
    int HANDLER_CHANGED_BRIGHTNESS = 90001;

    /**
     * 开启相机
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

    /**
     * 闪光灯是否开启
     */
    boolean isFlashOpened();

    void detach();

    /**
     * 设置预览View的尺寸
     */
    void setPreviewSize(int width, int height);

    /**
     * 设置预览SurfaceTexture
     */
    void setPreviewTexture(SurfaceTexture surfaceTexture);

    /**
     * 设置相机事件监听
     */
    void setCameraListener(CameraListener cameraListener);

    /**
     * 设置图像解码器
     */
    void setGraphicDecoder(GraphicDecoder graphicDecoder);

    /**
     * 设置是否启用亮度回馈
     */
    void enableBrightnessFeedback(boolean enable);

    /**
     * 设置扫码识别区域
     * 计算识别区域在图像帧中的位置，并根据屏幕方向进行校正
     * 注意：该区域坐标相对于图像帧的左上角顶点
     */
    void setFrameRect(int frameLeft, int frameTop, int frameRight, int frameBottom);

    interface CameraListener {

        /**
         * 当相机成功打开时触发的回调
         *
         * @param surfaceWidth  图像帧的宽
         * @param surfaceHeight 图像帧的高
         * @param surfaceDegree 图像帧需要旋转的角度(0/90/180/270)
         */
        void openCameraSuccess(int surfaceWidth, int surfaceHeight, int surfaceDegree);

        /**
         * 相机打开出错
         */
        void openCameraError();

        /**
         * 没有相机权限
         */
        void noCameraPermission();

        /**
         * 相机断开连接
         */
        void cameraDisconnected();

        /**
         * 亮度变化，每20帧更新一次
         *
         * @param brightness 20帧图像的平均亮度值，取值范围0-255
         */
        void cameraBrightnessChanged(int brightness);
    }

}
