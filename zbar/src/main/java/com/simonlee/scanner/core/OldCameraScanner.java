package com.simonlee.scanner.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @createdTime 2018-04-13 11:26
 */
public class OldCameraScanner implements CameraScanner, BaseHandler.BaseHandlerListener {

    private final int HANDLER_SUCCESS_OPEN = 70001;
    private final int HANDLER_FAIL_CLOSED = 80001;
    private final int HANDLER_FAIL_OPEN = 80002;
    private final int HANDLER_FAIL_CONFIG = 80003;
    private final int HANDLER_FAIL_DISCONNECTED = 80004;
    private final int HANDLER_FAIL_NO_PERMISSION = 80005;
    private final int HANDLER_FAIL_TIMEOUT = 80006;

    private Camera mCamera;

    private SurfaceTexture mSurfaceTexture;

    private GraphicDecoder mGraphicDecoder;//图像解码器

    private final String TAG = "CameraScanner";

    private int mOrientation;//设备方向 0 朝上 1朝左 2朝下 3朝右

    private Size mSurfaceSize;//图像帧的尺寸
    private Size mPreviewSize;//预览View的尺寸
    private RectF mFrameRectRatio;//扫码框区域相对于图像帧所占比例，且已根据mOrientation做校正

    private BaseHandler mCurThreadHandler;//实例化线程对应的handler
    private BaseHandler mBackgroundHandler;//子线程对应的handler
    private HandlerThread mBackgroundThread;//子线程

    private CameraDeviceListener mCameraDeviceListener;//相机设备回调

    private Semaphore mCameraLock;

    private Camera.PreviewCallback mPreviewCallback;

    private volatile static OldCameraScanner instance;

    public static CameraScanner getInstance() {
        if (instance == null) {
            synchronized (CameraScanner.class) {
                if (instance == null) {
                    instance = new OldCameraScanner();
                }
            }
        }
        return instance;
    }

    private OldCameraScanner() {
    }

    @Override
    public void openCamera(Context context) {
        final Context finalContext = context.getApplicationContext();
        startBackgroundThread();
        fetchOrientation(finalContext);//获取设备方向
        if (mCameraLock == null) mCameraLock = new Semaphore(1);
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!checkCameraPermission(finalContext)) {//权限不足
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_NO_PERMISSION));
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
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                    //设置预览Texture
                    mCamera.setPreviewTexture(mSurfaceTexture);
                    //开始预览
                    mCamera.startPreview();
                    //设置预览回调
                    mCamera.setPreviewCallback(getPreviewCallback());
                } catch (RuntimeException e) {//超时
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_TIMEOUT));
                } catch (Exception exception) {//开启相机失败
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                }
                mCameraLock.release();
            }
        });
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        mCameraLock.release();
    }

    @Override
    public void detach() {
        Log.d(TAG, getClass().getName() + ".detach()");
        closeCamera();
        stopBackgroundThread();
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mGraphicDecoder != null) {
            mGraphicDecoder.detach();
            mGraphicDecoder = null;
        }
        mCameraDeviceListener = null;
        mPreviewCallback = null;
        mFrameRectRatio = null;
        mSurfaceSize = null;
        mPreviewSize = null;
        mCameraLock = null;
    }

    @Override//TODO 如果相机已经打开，这里应该重设相机参数
    public void setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
        Log.d(TAG, getClass().getName() + ".setPreviewSize() mPreviewSize = " + mPreviewSize.toString());
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        this.mSurfaceTexture = surfaceTexture;
    }

    @Override
    public void setCameraDeviceListener(CameraDeviceListener cameraDeviceListener) {
        this.mCameraDeviceListener = cameraDeviceListener;
    }

    @Override
    public void setGraphicDecoder(GraphicDecoder graphicDecoder) {
        this.mGraphicDecoder = graphicDecoder;
    }

    @Override
    public void setFrameRect(int frameLeft, int frameTop, int frameRight, int frameBottom) {

        Log.e(TAG, getClass().getName() + ".setFrameRect() mOrientation = " + mOrientation + " 上下左右：" + frameTop + " / " + frameBottom
                + " / " + frameLeft + " / " + frameRight);
        if (mFrameRectRatio == null) {
            mFrameRectRatio = new RectF();
        }
        if (frameLeft >= frameRight || frameTop >= frameBottom) {
            mFrameRectRatio.setEmpty();
            return;
        }
        int previewWidth = mPreviewSize.getWidth();
        int previewHeight = mPreviewSize.getHeight();
        int surfaceWidth = mSurfaceSize.getWidth();
        int surfaceHeight = mSurfaceSize.getHeight();
        Log.e(TAG, getClass().getName() + ".setFrameRect() previewWh = " + previewWidth + "X" + previewHeight + " , surfaceWH = " + surfaceWidth
                + "X" + surfaceHeight);
        if (mOrientation % 2 == 0) {
            int temp = surfaceWidth;
            surfaceWidth = surfaceHeight;
            surfaceHeight = temp;
        }
        float ratio;
        if (previewWidth * surfaceHeight < surfaceWidth * previewHeight) {//图像帧的宽超出了View的左边，以高计算缩放比例
            Log.e(TAG, getClass().getName() + ".setFrameRect() A surfaceHeight = " + surfaceHeight + " , previewHeight = " + previewHeight);
            ratio = 1F * surfaceHeight / previewHeight;
            Log.e(TAG, getClass().getName() + ".setFrameRect() A ratio = " + ratio);
        } else {//图像帧的高超出了View的底边，以宽计算缩放比例
            ratio = 1F * surfaceWidth / previewWidth;
            Log.e(TAG, getClass().getName() + ".setFrameRect() B ratio = " + ratio);
        }
        float leftRatio = calculateRatio(ratio * frameLeft / surfaceWidth);
        float rightRatio = calculateRatio(ratio * frameRight / surfaceWidth);
        float topRatio = calculateRatio(ratio * frameTop / surfaceHeight);
        float bottomRatio = calculateRatio(ratio * frameBottom / surfaceHeight);


        switch (mOrientation) {
            case Surface.ROTATION_0: {
                mFrameRectRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
                break;
            }
            case Surface.ROTATION_90: {
                mFrameRectRatio.set(leftRatio, topRatio, rightRatio, bottomRatio);
                break;
            }
            case Surface.ROTATION_180: {
                mFrameRectRatio.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
                break;
            }
            case Surface.ROTATION_270: {
                mFrameRectRatio.set(1 - rightRatio, 1 - bottomRatio, 1 - leftRatio, 1 - topRatio);
                break;
            }
        }
        Log.e(TAG, getClass().getName() + ".setFrameRectRatio() mFrameRectRatio = " + mFrameRectRatio);


//        if (mFrameRectRatio == null) {
//            mFrameRectRatio = new RectF();
//        }
//        if (frameLeft >= frameRight || frameTop >= frameBottom) {
//            mFrameRectRatio.setEmpty();
//            return;
//        }
//        float width = mSurfaceSize.getWidth();
//        float height = mSurfaceSize.getHeight();
//        float leftRatio = calculateRatio(frameLeft / width);
//        float rightRatio = calculateRatio(frameRight / width);
//        float topRatio = calculateRatio(frameTop / height);
//        float bottomRatio = calculateRatio(frameBottom / height);
//
//        switch (mOrientation) {//TODO 方向可能有问题
//            case Surface.ROTATION_0: {
//                mFrameRectRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
//                break;
//            }
//            case Surface.ROTATION_90: {
//                mFrameRectRatio.set(leftRatio, topRatio, rightRatio, bottomRatio);
//                break;
//            }
//            case Surface.ROTATION_180: {
//                mFrameRectRatio.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
//                break;
//            }
//            case Surface.ROTATION_270: {
//                mFrameRectRatio.set(1 - rightRatio, 1 - bottomRatio, 1 - leftRatio, 1 - topRatio);
//                break;
//            }
//        }
//        Log.d(TAG, getClass().getName() + ".setFrameRectRatio() mFrameRectRatio = " + mFrameRectRatio);
    }

    private void startBackgroundThread() {
        if (mCurThreadHandler == null) {
            mCurThreadHandler = new BaseHandler(this);
        }
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("CameraScanner");
            mBackgroundThread.start();
            mBackgroundHandler = new BaseHandler(null, mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundThread.quitSafely();
            } else {
                mBackgroundThread.quit();
            }
            try {
                mBackgroundThread.join();
                mBackgroundHandler.clear();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取window方向
     */
    private void fetchOrientation(Context context) {
        //TODO 这个方向，应该使用
//        Camera.CameraInfo info = new Camera.CameraInfo();
//        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
//        Log.d(TAG, getClass().getName() + ".closeCamera()" + info.orientation);


        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            mOrientation = windowManager.getDefaultDisplay().getRotation();
        }
        Log.d(TAG, getClass().getName() + ".fetchOrientation() mOrientation = " + mOrientation);
    }

    private float calculateRatio(float ratio) {
        if (ratio > 1) {
            return 1;
        } else if (ratio < 0) {
            return 0;
        }
        return ratio;
    }

    private boolean checkCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 初始化图像帧的尺寸大小
     */
    private void initSurfaceSize(List<Camera.Size> sizeList, int previewWidth, int previewHeight) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {//TODO 这里可能要改
            mSurfaceSize = getBigEnoughSize(sizeList, previewHeight, previewWidth);
        } else {
            mSurfaceSize = getBigEnoughSize(sizeList, previewWidth, previewHeight);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            mSurfaceTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
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
                    if (frameData == null) {
                        Log.e(TAG, getClass().getName() + ".onPreviewFrame() fuck");
                    }
                    Log.e(TAG, getClass().getName() + ".onPreviewFrame() frameData.length = " + frameData.length);
                    if (mGraphicDecoder != null) {
                        mGraphicDecoder.decode(frameData, mSurfaceSize.getWidth(), mSurfaceSize.getHeight(), mFrameRectRatio);
                    }
                }
            };
        }
        return mPreviewCallback;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {//开启成功
                if (mCameraDeviceListener != null) {
                    mCameraDeviceListener.openCameraSuccess(mSurfaceSize.getWidth(), mSurfaceSize.getHeight(), (5 - mOrientation) % 4 * 90);
                }
                break;
            }
            case HANDLER_FAIL_CLOSED: {//已被关闭
                closeCamera();
                break;
            }
            case HANDLER_FAIL_OPEN://开启失败
            case HANDLER_FAIL_CONFIG: {//配置失败
                closeCamera();
                if (mCameraDeviceListener != null) {
                    mCameraDeviceListener.openCameraError();
                }
                break;
            }
            case HANDLER_FAIL_DISCONNECTED: {//断开连接
                closeCamera();
                if (mCameraDeviceListener != null) {
                    mCameraDeviceListener.cameraDisconnected();
                }
                break;
            }
            case HANDLER_FAIL_NO_PERMISSION: {//没有权限
                closeCamera();
                if (mCameraDeviceListener != null) {
                    mCameraDeviceListener.noCameraPermission();
                }
                break;
            }
            default: {
                break;
            }
        }
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
