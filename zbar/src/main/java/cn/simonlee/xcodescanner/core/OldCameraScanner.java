package cn.simonlee.xcodescanner.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 扫码核心类，旧API
 * 完成相机预览、图像帧数据采集、闪光灯控制等功能
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-04-13
 */
public class OldCameraScanner implements CameraScanner, Handler.Callback {

    /**
     * 相机实例
     */
    private Camera mCamera;

    /**
     * 预览的SurfaceTexture
     */
    private SurfaceTexture mPreviewTexture;

    /**
     * 条码&二维码图像解析工具
     */
    private GraphicDecoder mGraphicDecoder;

    /**
     * 设备方向 0 朝上 1朝左 2朝下 3朝右
     */
    private int mOrientation;

    /**
     * 图像帧的尺寸
     */
    private Size mSurfaceSize;

    /**
     * 预览View的尺寸
     */
    private Size mPreviewSize;

    /**
     * 扫码框区域相对于图像帧所占比例，且已根据mOrientation做校正
     */
    private RectF mClipRectRatio;

    /**
     * Handler
     */
    private Handler mCurThreadHandler;

    /**
     * 相机事件监听回调
     */
    private CameraListener mCameraListener;

    /**
     * 相机锁，操作相机前都必须申请信号量，防止多个实例同时操作相机引发异常
     */
    private Semaphore mCameraLock;

    /**
     * 预览回调，用于接收图像帧数据
     */
    private Camera.PreviewCallback mPreviewCallback;

    /**
     * 启用亮度回馈标志
     */
    private boolean isBrightnessFeedbackEnabled;

    public OldCameraScanner(CameraListener cameraListener) {
        this.mCameraListener = cameraListener;
        this.mCurThreadHandler = new Handler(this);
        this.mCameraLock = CameraLock.getInstance();
        this.isBrightnessFeedbackEnabled = true;
    }

    @Override
    public void openCamera(Context context) {
        Log.d(TAG, getClass().getName() + ".openCamera()");
        final Context finalContext = context.getApplicationContext();
        takeOrientation(finalContext);//获取设备方向
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (ContextCompat.checkSelfPermission(finalContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    //权限不足
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_NO_PERMISSION));
                    }
                    return;
                }
                try {
                    //获取Semaphore锁
                    if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    //打开相机
                    mCamera = Camera.open();
                    //获取参数
                    Camera.Parameters parameters = mCamera.getParameters();
                    //设置对焦模式
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    //初始化图像帧尺寸
                    initSurfaceSize(parameters.getSupportedPreviewSizes(), mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    //设置预览尺寸
                    parameters.setPreviewSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
                    //设置参数
                    mCamera.setParameters(parameters);
                    //通知相机打开成功
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                    }
                    //设置预览Texture
                    mCamera.setPreviewTexture(mPreviewTexture);
                    //开始预览
                    mCamera.startPreview();
                    //设置预览回调
                    mCamera.setPreviewCallback(getPreviewCallback());
                } catch (RuntimeException e) {//超时
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_TIMEOUT));
                    }
                } catch (Exception exception) {//开启相机失败
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                    }
                }
                mCameraLock.release();
            }
        }).start();
    }

    @Override
    public void closeCamera() {
        Log.d(TAG, getClass().getName() + ".closeCamera()");
        try {
            mCameraLock.acquire();
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void openFlash() {
        try {
            mCameraLock.acquire();
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(parameters);
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void closeFlash() {
        try {
            mCameraLock.acquire();
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(parameters);
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isFlashOpened() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return !Camera.Parameters.FLASH_MODE_OFF.equals(parameters.getFlashMode());
        }
        return false;
    }

    @Override
    public void detach() {
        closeCamera();
        if (mCurThreadHandler != null) {
            mCurThreadHandler.removeCallbacksAndMessages(null);
            mCurThreadHandler = null;
        }
        if (mPreviewTexture != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                //经测试，低于4.4版本此处会报错
                mPreviewTexture.release();
            }
            mPreviewTexture = null;
        }
        if (mGraphicDecoder != null) {
            mGraphicDecoder.detach();
            mGraphicDecoder = null;
        }
        mCameraListener = null;
    }

    @Override
    public void setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
        Log.d(TAG, getClass().getName() + ".setPreviewSize() mPreviewSize = " + mPreviewSize.toString());
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) {
        this.mPreviewTexture = surfaceTexture;
    }

    @Override
    public void setCameraListener(CameraListener cameraListener) {
        this.mCameraListener = cameraListener;
    }

    @Override
    public void setGraphicDecoder(GraphicDecoder graphicDecoder) {
        this.mGraphicDecoder = graphicDecoder;
    }

    @Override
    public void enableBrightnessFeedback(boolean enable) {
        this.isBrightnessFeedbackEnabled = enable;
    }

    @Override
    public void setFrameRect(int frameLeft, int frameTop, int frameRight, int frameBottom) {
        Log.d(TAG, getClass().getName() + ".setFrameRect() mOrientation = " + mOrientation + " frameRect = " + frameLeft + "-" + frameTop
                + "-" + frameRight + "-" + frameBottom);
        if (mClipRectRatio == null) {
            mClipRectRatio = new RectF();
        }
        if (frameLeft >= frameRight || frameTop >= frameBottom) {
            mClipRectRatio.setEmpty();
            return;
        }
        int previewWidth = mPreviewSize.getWidth();
        int previewHeight = mPreviewSize.getHeight();
        int surfaceWidth = mSurfaceSize.getWidth();
        int surfaceHeight = mSurfaceSize.getHeight();
        if (mOrientation % 2 == 0) {
            int temp = surfaceWidth;
            surfaceWidth = surfaceHeight;
            surfaceHeight = temp;
        }
        float ratio;//图像帧的缩放比，比如1000*2000像素的图像显示在100*200的View上，缩放比就是10
        if (previewWidth * surfaceHeight < surfaceWidth * previewHeight) {//图像帧的宽超出了View的左边，以高计算缩放比例
            ratio = 1F * surfaceHeight / previewHeight;
        } else {//图像帧的高超出了View的底边，以宽计算缩放比例
            ratio = 1F * surfaceWidth / previewWidth;
        }
        float leftRatio = Math.max(0, Math.min(1, ratio * frameLeft / surfaceWidth));//计算扫描框的左边在图像帧中所处的位置
        float rightRatio = Math.max(0, Math.min(1, ratio * frameRight / surfaceWidth));//计算扫描框的右边在图像帧中所处的位置
        float topRatio = Math.max(0, Math.min(1, ratio * frameTop / surfaceHeight));//计算扫描框的顶边在图像帧中所处的位置
        float bottomRatio = Math.max(0, Math.min(1, ratio * frameBottom / surfaceHeight));//计算扫描框的底边在图像帧中所处的位置
        switch (mOrientation) {//根据旋转角度对位置进行校正
            case Surface.ROTATION_0: {
                mClipRectRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
                break;
            }
            case Surface.ROTATION_90: {
                mClipRectRatio.set(leftRatio, topRatio, rightRatio, bottomRatio);
                break;
            }
            case Surface.ROTATION_180: {
                mClipRectRatio.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
                break;
            }
            case Surface.ROTATION_270: {
                mClipRectRatio.set(1 - rightRatio, 1 - bottomRatio, 1 - leftRatio, 1 - topRatio);
                break;
            }
        }
        Log.d(TAG, getClass().getName() + ".setFrameRect() mClipRectRatio = " + mClipRectRatio);
    }

    /**
     * 获取window方向
     */
    private void takeOrientation(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            mOrientation = windowManager.getDefaultDisplay().getRotation();
        }
        Log.d(TAG, getClass().getName() + ".takeOrientation() mOrientation = " + mOrientation);
    }

    /**
     * 初始化图像帧的尺寸大小
     */
    private void initSurfaceSize(List<Camera.Size> sizeList, int previewWidth, int previewHeight) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {
            mSurfaceSize = getBigEnoughSize(sizeList, previewWidth, previewHeight);
        } else {
            mSurfaceSize = getBigEnoughSize(sizeList, previewHeight, previewWidth);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            mPreviewTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        }
        Log.d(TAG, getClass().getName() + ".initSurfaceSize() mSurfaceSize = " + mSurfaceSize.toString());
    }

    /**
     * 返回sizes中宽高大于最小宽高的最小尺寸
     */
    private Size getBigEnoughSize(List<Camera.Size> sizeList, int minWidth, int minHeight) {
        Camera.Size curSize = sizeList.get(0);
        boolean curBigEnough = curSize.width >= minWidth && curSize.height >= minHeight;
        for (int i = 1; i < sizeList.size(); i++) {
            Camera.Size nextSize = sizeList.get(i);
            boolean nextBigEnough = nextSize.width >= minWidth && nextSize.height >= minHeight;
            if (!curBigEnough && nextBigEnough) {//curSize尺寸不够，nextSize够
                curBigEnough = true;
                curSize = nextSize;
            } else if (curBigEnough ^ !nextBigEnough) {//curSize与nextSize尺寸同够或同不够
                long curPixels = (long) curSize.width * curSize.height;
                long nextPixels = (long) nextSize.width * nextSize.height;
                if (curBigEnough ^ (curPixels < nextPixels)) {//尺寸同够且curSize不小于nextSize 或 尺寸同不够且curSize小于nextSize
                    curSize = nextSize;
                }
            }
        }
        return new Size(curSize.width, curSize.height);
    }

    private Camera.PreviewCallback getPreviewCallback() {
        if (mPreviewCallback == null) {
            mPreviewCallback = new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] frameData, Camera camera) {
                    if (mGraphicDecoder != null) {
                        if (mClipRectRatio == null || mClipRectRatio.isEmpty()) {//当未设置图像识别剪裁时，应以View的大小进行设置，防止未显示的图像被误识别
                            setFrameRect(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                        }
                        mGraphicDecoder.decode(frameData, mSurfaceSize.getWidth(), mSurfaceSize.getHeight(), mClipRectRatio);
                    }
                    if (isBrightnessFeedbackEnabled && mCameraListener != null) {//启用亮度回馈
                        //采集步长，总共采集100个像素点
                        final int length = mSurfaceSize.getWidth() * mSurfaceSize.getHeight();
                        final int step = Math.max(1, length / 100);
                        int brightnessTotal = 0;
                        int brightnessCount = 0;
                        for (int index = 0; index < length; index += step) {
                            brightnessTotal += frameData[index] & 0xff - 16;
                            brightnessCount++;
                        }
                        if (mCurThreadHandler != null) {
                            mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_CHANGED_BRIGHTNESS, brightnessTotal, brightnessCount));
                        }
                    }
                }
            };
        }
        return mPreviewCallback;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {//开启成功
                if (mCameraListener != null) {
                    mCameraListener.openCameraSuccess(mSurfaceSize.getWidth(), mSurfaceSize.getHeight(), (5 - mOrientation) % 4 * 90);
                }
                break;
            }
            case HANDLER_FAIL_CLOSED: {//已被关闭
                closeCamera();
                break;
            }
            case HANDLER_CHANGED_BRIGHTNESS: {//亮度变化
                if (mCameraListener != null && msg.arg2 != 0) {
                    mCameraListener.cameraBrightnessChanged(msg.arg1 / msg.arg2);
                }
                break;
            }
            case HANDLER_FAIL_OPEN://开启失败
            case HANDLER_FAIL_CONFIG: {//配置失败
                closeCamera();
                if (mCameraListener != null) {
                    mCameraListener.openCameraError();
                }
                break;
            }
            case HANDLER_FAIL_DISCONNECTED: {//断开连接
                closeCamera();
                if (mCameraListener != null) {
                    mCameraListener.cameraDisconnected();
                }
                break;
            }
            case HANDLER_FAIL_NO_PERMISSION: {//没有权限
                closeCamera();
                if (mCameraListener != null) {
                    mCameraListener.noCameraPermission();
                }
                break;
            }
            default: {
                break;
            }
        }
        return true;
    }

    private class Size {

        private final int mWidth;
        private final int mHeight;

        private Size(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        private int getWidth() {
            return mWidth;
        }

        private int getHeight() {
            return mHeight;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj != null) {
                if (this == obj) return true;
                if (obj instanceof Size) {
                    Size other = (Size) obj;
                    return mWidth == other.mWidth && mHeight == other.mHeight;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return mWidth + "x" + mHeight;
        }

    }
}
