package com.simonlee.scanner.core;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @createdTime 2018-04-13 16:02
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NewCameraScanner implements CameraScanner, BaseHandler.BaseHandlerListener {

    private final int HANDLER_SUCCESS_OPEN = 70001;
    private final int HANDLER_FAIL_CLOSED = 80001;
    private final int HANDLER_FAIL_OPEN = 80002;
    private final int HANDLER_FAIL_CREATSESSION = 80003;
    private final int HANDLER_FAIL_CONFIG = 80004;
    private final int HANDLER_FAIL_DISCONNECTED = 80005;
    private final int HANDLER_FAIL_NO_PERMISSION = 80006;

    private SurfaceTexture mSurfaceTexture;

    private GraphicDecoder mGraphicDecoder;//图像解码器

    private final String TAG = "CameraScanner";

    private int mOrientation;//设备方向 0 朝上 1朝左 2朝下 3朝右

    private Size mPreviewSize;//预览View的尺寸
    private Size mImageFrameSize;//图像帧的尺寸
    private RectF mFrameRectRatio;//扫码框区域相对于图像帧所占比例，且已根据mOrientation做校正

    private BaseHandler mCurThreadHandler;//实例化线程对应的handler
    private BaseHandler mBackgroundHandler;//子线程对应的handler
    private HandlerThread mBackgroundThread;//子线程

    private CameraDeviceListener mCameraDeviceListener;//相机设备回调

    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewBuilder;

    private String mCameraId;//相机ID

    private TextureReader mTextureReader;//用于获取帧数据

    private CameraDevice.StateCallback mDeviceStateCallback;
    private CameraCaptureSession.StateCallback mSessionStateCallback;
    private TextureReader.OnFrameAvailableListener mOnFrameAvailableListener;

    private Semaphore mCameraLock;

    private volatile static NewCameraScanner instance;

    public static CameraScanner getInstance() {
        if (instance == null) {
            synchronized (CameraScanner.class) {
                if (instance == null) {
                    instance = new NewCameraScanner();
                }
            }
        }
        return instance;
    }

    private NewCameraScanner() {
    }

    @Override
    public void openCamera(Context context) {
        Log.d(TAG, getClass().getName() + ".openCamera()");
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
                if (mCameraManager == null) {
                    mCameraManager = (CameraManager) finalContext.getSystemService(Context.CAMERA_SERVICE);
                }
                try {
                    //获取Semaphore锁
                    if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    //获取后置摄像头配置
                    StreamConfigurationMap configurationMap = getBackCameraStreamConfigurationMap();
                    Size[] outputSizes = configurationMap.getOutputSizes(SurfaceTexture.class);
                    //初始化图像帧尺寸
                    initImageFrameSize(outputSizes, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    //初始化TextureReader
                    initTextureReader(outputSizes);
                    //开启摄像头
                    mCameraManager.openCamera(mCameraId, getDeviceStateCallback(), mBackgroundHandler);//开启相机
                } catch (Exception e) {//开启相机失败
                    Log.e(TAG, getClass().getName() + ".openCamera() : " + e);
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                }
            }
        });
    }

    /**
     * 启动子线程
     */
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

    /**
     * 结束子线程
     */
    private void stopBackgroundThread() {
        if (mCurThreadHandler != null) {
            mCurThreadHandler.clear();
            mCurThreadHandler = null;
        }
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
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
//        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            mOrientation = windowManager.getDefaultDisplay().getRotation();
        }
        Log.d(TAG, getClass().getName() + ".fetchOrientation() mOrientation = " + mOrientation);
    }

    /**
     * 检查相机权限
     */
    private boolean checkCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 获取后置摄像头配置及cameraId
     */
    private StreamConfigurationMap getBackCameraStreamConfigurationMap() throws NullPointerException, CameraAccessException {
        for (String cameraId : mCameraManager.getCameraIdList()) {//查询CameraID
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                mCameraId = cameraId;
                return cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            }
        }
        throw new NullPointerException("No Back Camera Stream Configuration Map found!");
    }

    /**
     * 初始化图像帧的尺寸大小
     */
    private void initImageFrameSize(Size[] sizeArray, int previewWidth, int previewHeight) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {//TODO 这里可能要改
            mImageFrameSize = getBigEnoughSize(sizeArray, previewHeight, previewWidth);
        } else {
            mImageFrameSize = getBigEnoughSize(sizeArray, previewWidth, previewHeight);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            mSurfaceTexture.setDefaultBufferSize(mImageFrameSize.getWidth(), mImageFrameSize.getHeight());
        }
        Log.d(TAG, getClass().getName() + ".initImageFrameSize() mImageFrameSize = " + mImageFrameSize.toString());
    }

    /**
     * 初始化TextureReader
     */
    private void initTextureReader(Size[] outputSizes) {
        if (mTextureReader == null) {
            //像素过大会导致二维码解析失败！此处限制为1080P，即2073600像素大小
            Size size = getMaxSuitSize(outputSizes, mImageFrameSize, 2073600L);
            mTextureReader = new TextureReader(size.getWidth(), size.getHeight());
            mTextureReader.setOnFrameAvailableListener(getFrameAvailableListener());
            Log.d(TAG, getClass().getName() + ".initTextureReader() mTextureReader = " + size.toString());
        }
    }

    @Override
    public void closeCamera() {
        Log.d(TAG, getClass().getName() + ".closeCamera()");
        try {
            mCameraLock.acquire();
            if (mCaptureSession != null) {
//                mCaptureSession.stopRepeating();
//                mCaptureSession.abortCaptures();
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
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
        if (mTextureReader != null) {
            mTextureReader.close();
            mTextureReader = null;
        }
        mCameraId = null;
        mCameraLock = null;
        mPreviewSize = null;
        mCameraManager = null;
        mPreviewBuilder = null;
        mFrameRectRatio = null;
        mImageFrameSize = null;
        mDeviceStateCallback = null;
        mSessionStateCallback = null;
        mCameraDeviceListener = null;
        mOnFrameAvailableListener = null;
    }

    @Override
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
        Log.e(TAG, getClass().getName() + ".setFrameRect() mOrientation = "+mOrientation+" 上下左右："+frameTop+" / "+frameBottom
                +" / "+frameLeft+" / "+frameRight);
//        if (mFrameRectRatio == null) {
//            mFrameRectRatio = new RectF();
//        }
//        if (frameLeft >= frameRight || frameTop >= frameBottom) {
//            mFrameRectRatio.setEmpty();
//            return;
//        }
//      1. Surface.ROTATION_0  OK
//        float width = mImageFrameSize.getWidth();
//        float height = mImageFrameSize.getHeight();
//        float leftRatio = calculateRatio(frameLeft / height);
//        float rightRatio = calculateRatio(frameRight / height);
//        float topRatio = calculateRatio(frameTop / width);
//        float bottomRatio = calculateRatio(frameBottom / width);
//        mFrameRectRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
//      2. Surface.ROTATION_90
//        float width = mImageFrameSize.getWidth();
//        float height = mImageFrameSize.getHeight();
//        float leftRatio = calculateRatio(frameLeft / height);
//        float rightRatio = calculateRatio(frameRight / height);
//        float topRatio = calculateRatio(frameTop / width);
//        float bottomRatio = calculateRatio(frameBottom / width);
//        mFrameRectRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
//      3. Surface.ROTATION_180
//        float width = mImageFrameSize.getWidth();
//        float height = mImageFrameSize.getHeight();
//        float leftRatio = calculateRatio(frameLeft / height);
//        float rightRatio = calculateRatio(frameRight / height);
//        float topRatio = calculateRatio(frameTop / width);
//        float bottomRatio = calculateRatio(frameBottom / width);
//        mFrameRectRatio.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
//      4. Surface.ROTATION_270
//        float width = mImageFrameSize.getWidth();
//        float height = mImageFrameSize.getHeight();
//        float leftRatio = calculateRatio(frameLeft / width);
//        float rightRatio = calculateRatio(frameRight / width);
//        float topRatio = calculateRatio(frameTop / height);
//        float bottomRatio = calculateRatio(frameBottom / height);
//        mFrameRectRatio.set(1-rightRatio, 1-bottomRatio, 1-leftRatio, 1-topRatio);
//        mFrameRectRatio.set(1-bottomRatio, leftRatio, 1-topRatio, rightRatio);
//        mFrameRectRatio.set(topRatio, 1-rightRatio, bottomRatio, 1-leftRatio);
//        mFrameRectRatio.set(leftRatio, topRatio, rightRatio, bottomRatio);


//        Log.e(TAG, getClass().getName() + ".setFrameRect() WH = " + width+"x"+height+" 上下左右比："+topRatio+" / "+bottomRatio
//                +" / "+leftRatio+" / "+rightRatio);
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
//        Log.e(TAG, getClass().getName() + ".setFrameRectRatio() mFrameRectRatio = " + mFrameRectRatio);
    }


    private CameraDevice.StateCallback getDeviceStateCallback() {
        if (mDeviceStateCallback == null) {
            mDeviceStateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {//成功打开摄像头
                    Log.d(TAG, getClass().getName() + ".onOpened()");
                    mCameraDevice = camera;
                    mCameraLock.release();
                    createCaptureSession();//创建会话
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {//未能连接到摄像头，提示是否被占用
                    Log.e(TAG, getClass().getName() + ".onDisconnected()");
                    mCameraLock.release();
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_DISCONNECTED));
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {//出错
                    Log.e(TAG, getClass().getName() + ".onError() error = " + error);
                    mCameraLock.release();
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                }
            };
        }
        return mDeviceStateCallback;
    }

    private void createCaptureSession() {
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            List<Surface> surfaceList = new ArrayList<>();
            Surface surface = new Surface(mSurfaceTexture);
            mPreviewBuilder.addTarget(surface);//添加预览的Surface
            surfaceList.add(surface);
            if (mTextureReader != null) {
                Surface rendererSurface = mTextureReader.getSurface();
                mPreviewBuilder.addTarget(rendererSurface);
                surfaceList.add(rendererSurface);
            }
            mCameraDevice.createCaptureSession(surfaceList, getSessionStateCallback(), mBackgroundHandler);//创建会话
        } catch (CameraAccessException e) {//创建会话失败
            Log.e(TAG, getClass().getName() + ".createCaptureSession() : " + e);
            mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CREATSESSION));
        }
    }

    private CameraCaptureSession.StateCallback getSessionStateCallback() {
        if (mSessionStateCallback == null) {
            mSessionStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {//会话配置完成
                    Log.d(TAG, getClass().getName() + ".onConfigured()");
                    mCaptureSession = session;
                    try {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);//自动对焦
                        //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//自动闪光灯
                        mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                    } catch (CameraAccessException e) {
                        Log.e(TAG, getClass().getName() + ".onConfigured() : " + e);
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, getClass().getName() + ".onConfigureFailed()");
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
                }
            };
        }
        return mSessionStateCallback;
    }

    private TextureReader.OnFrameAvailableListener getFrameAvailableListener() {
        if (mOnFrameAvailableListener == null) {
            mOnFrameAvailableListener = new TextureReader.OnFrameAvailableListener() {

                @Override
                public void onFrameAvailable(byte[] frameData, int width, int height) {
                    Log.d(TAG, getClass().getName() + ".onFrameAvailable() frameData.length = "+frameData.length+" , width = "+width+" , height = "+height);
                    if (mGraphicDecoder != null) {
                        mGraphicDecoder.decode(frameData, width, height, mFrameRectRatio);
                    }
                }
            };
        }
        return mOnFrameAvailableListener;
    }

    private float calculateRatio(float ratio) {
        if (ratio > 1) {
            return 1;
        } else if (ratio < 0) {
            return 0;
        }
        return ratio;
    }

    /**
     * 返回sizes中宽高大于最小值的最小尺寸
     */
    private Size getBigEnoughSize(Size[] sizeArray, int minWidth, int minHeight) {
        Log.d(TAG, getClass().getName() + ".getBigEnoughSize() minWidth = " + minWidth + " , minHeight = " + minHeight);
        Size curSize = sizeArray[0];
        boolean curBigEnough = curSize.getWidth() >= minWidth && curSize.getHeight() >= minHeight;
        for (int i = 1; i < sizeArray.length; i++) {
            Size nextSize = sizeArray[i];
            boolean nextBigEnough = nextSize.getWidth() >= minWidth && nextSize.getHeight() >= minHeight;
            if (!curBigEnough && nextBigEnough) {//curSize尺寸不够，nextSize够
                curBigEnough = true;
                curSize = nextSize;
            } else if (curBigEnough ^ !nextBigEnough) {//curSize与nextSize尺寸同够或同不够
                long curPixels = (long) curSize.getWidth() * curSize.getHeight();
                long nextPixels = (long) nextSize.getWidth() * nextSize.getHeight();
                if (curBigEnough ^ (curPixels < nextPixels)) {//尺寸同够且curSize不小于nextSize 或 尺寸同不够且curSize小于nextSize
                    curSize = nextSize;
                }
            }
        }
        return curSize;
    }

    /**
     * 返回sizes中 1.宽高比与similarSize相同，2.像素不超过suitPixels 的最大尺寸
     */
    private Size getMaxSuitSize(Size[] sizeArray, final Size similarSize, final Long maxPixels) {
        Size curSize = sizeArray[0];
        boolean curSimilar = similarSize != null && similarSize.getHeight() * curSize.getWidth() == similarSize.getWidth() * curSize.getHeight();
        for (int i = 1; i < sizeArray.length; i++) {
            Size nextSize = sizeArray[i];
            boolean nextSimilar = similarSize != null && similarSize.getHeight() * nextSize.getWidth() == similarSize.getWidth() * nextSize.getHeight();
            if (!curSimilar && nextSimilar) {//curSize不相似，nextSize相似
                curSimilar = true;
                curSize = nextSize;
            } else if (!curSimilar ^ nextSimilar) {//同为相似或者同为不相似，进行尺寸筛选
                long curPixels = (long) curSize.getWidth() * curSize.getHeight();
                long nextPixels = (long) nextSize.getWidth() * nextSize.getHeight();
                boolean curBigger = curPixels > nextPixels;
                if (maxPixels != null) {
                    if ((curBigger && curPixels > maxPixels) || (!curBigger && nextPixels <= maxPixels)) {
                        curSize = nextSize;
                    }
                } else if (!curBigger) {
                    curSize = nextSize;
                }
            }
        }
        return curSize;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {//开启成功
                if (mCameraDeviceListener != null) {
                    //这里将宽高对调因为Camera2将图像顺时针旋转过90度
                    mCameraDeviceListener.openCameraSuccess(mImageFrameSize.getHeight(), mImageFrameSize.getWidth(), (4 - mOrientation) % 4 * 90);
                }
                break;
            }
            case HANDLER_FAIL_CLOSED: {//已被关闭
                closeCamera();
                break;
            }
            case HANDLER_FAIL_OPEN://开启失败
            case HANDLER_FAIL_CREATSESSION://会话创建失败
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

}
