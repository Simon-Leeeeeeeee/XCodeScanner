package cn.simonlee.xcodescanner.core;

import android.Manifest;
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
import android.os.Handler;
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
 * 扫码核心类，新API（SDK21及以上）
 * 完成相机预览、图像帧数据采集、闪光灯控制等功能
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-02-02
 */
@android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NewCameraScanner implements CameraScanner, Handler.Callback {

    /**
     * 相机ID
     */
    private String mCameraId;

    /**
     * 相机设备
     */
    private CameraDevice mCameraDevice;

    /**
     * 相机管理
     */
    private CameraManager mCameraManager;

    /**
     * 相机会话
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * 预览Builder
     */
    private CaptureRequest.Builder mPreviewBuilder;

    /**
     * 相机状态回调
     */
    private CameraDevice.StateCallback mDeviceStateCallback;

    /**
     * 会话状态回调
     */
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    /**
     * 预览的SurfaceTexture
     */
    private SurfaceTexture mPreviewTexture;

    /**
     * 条码&二维码图像解析工具
     */
    private GraphicDecoder mGraphicDecoder;

    /**
     * Surface列表
     */
    private List<Surface> mSurfaceList = new ArrayList<>();

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
     * 实例化线程对应的handler
     */
    private Handler mCurThreadHandler;

    /**
     * 子线程对应的handler
     */
    private Handler mBackgroundHandler;

    /**
     * 子线程
     */
    private HandlerThread mBackgroundThread;

    /**
     * 相机事件监听回调
     */
    private CameraListener mCameraListener;

    /**
     * 相机锁，操作相机前都必须申请信号量，防止多个实例同时操作相机引发异常
     */
    private Semaphore mCameraLock;

    /**
     * 用于获取帧数据
     */
    private TextureReader mTextureReader;

    /**
     * 图像帧回调
     */
    private TextureReader.OnImageAvailableListener mOnImageAvailableListener;

    /**
     * 启用亮度回馈标志
     */
    private boolean isBrightnessFeedbackEnabled;

    public NewCameraScanner(CameraListener cameraListener) {
        this.mCameraListener = cameraListener;
        this.mCurThreadHandler = new Handler(this);
        this.mBackgroundThread = new HandlerThread("NewCameraScanner");
        mBackgroundThread.start();
        this.mBackgroundHandler = new Handler(mBackgroundThread.getLooper(), null);
        this.mCameraLock = CameraLock.getInstance();
        this.isBrightnessFeedbackEnabled = true;
    }

    @Override
    public void openCamera(Context context) {
        Log.d(TAG, getClass().getName() + ".openCamera()");
        final Context finalContext = context.getApplicationContext();
        takeOrientation(finalContext);//获取设备方向
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (ContextCompat.checkSelfPermission(finalContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    //权限不足
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_NO_PERMISSION));
                    }
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
                    initSurfaceSize(outputSizes, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    //初始化TextureReader
                    initTextureReader(outputSizes);
                    //开启摄像头
                    mCameraManager.openCamera(mCameraId, getDeviceStateCallback(), mBackgroundHandler);//开启相机
                } catch (Exception e) {//开启相机失败
                    Log.e(TAG, getClass().getName() + ".openCamera() : " + e);
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                    }
                }
            }
        });
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
            for (Surface surface : mSurfaceList) {
                surface.release();
            }
            mSurfaceList.clear();
            mPreviewBuilder = null;
            mCaptureSession = null;
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void openFlash() {
        try {
            mCameraLock.acquire();
            try {
                if (mCaptureSession != null && mPreviewBuilder != null) {
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
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
            try {
                if (mCaptureSession != null && mPreviewBuilder != null) {
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isFlashOpened() {
        if (mCaptureSession != null && mPreviewBuilder != null) {
            mPreviewBuilder.get(CaptureRequest.FLASH_MODE);
            Integer flashMode = mPreviewBuilder.get(CaptureRequest.FLASH_MODE);
            if (flashMode != null)
                return CaptureRequest.FLASH_MODE_OFF != flashMode;
        }
        return false;
    }

    @Override
    public void detach() {
        Log.d(TAG, getClass().getName() + ".detach()");
        closeCamera();
        if (mCurThreadHandler != null) {
            mCurThreadHandler.removeCallbacksAndMessages(null);
            mCurThreadHandler = null;
        }
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
            mBackgroundHandler = null;
        }
        if (mBackgroundThread != null) {
            mBackgroundThread.quit();
        }
        if (mPreviewTexture != null) {
            mPreviewTexture.release();
            mPreviewTexture = null;
        }
        if (mTextureReader != null) {
            mTextureReader.close();
            mTextureReader = null;
        }
        if (mGraphicDecoder != null) {
            mGraphicDecoder.detach();
            mGraphicDecoder = null;
        }
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
    private void initSurfaceSize(Size[] sizeArray, int previewWidth, int previewHeight) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {
            mSurfaceSize = getBigEnoughSize(sizeArray, previewHeight, previewWidth);
        } else {
            mSurfaceSize = getBigEnoughSize(sizeArray, previewWidth, previewHeight);
        }
        mPreviewTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        Log.d(TAG, getClass().getName() + ".initSurfaceSize() mSurfaceSize = " + mSurfaceSize.toString());
    }

    /**
     * 初始化TextureReader
     */
    private void initTextureReader(Size[] outputSizes) {
        if (mTextureReader == null) {
            //像素过大会导致二维码解析失败！此处限制为1080P，即2073600像素大小
            Size size = getMaxSuitSize(outputSizes, mSurfaceSize, 2073600L);
            mTextureReader = new TextureReader(size.getWidth(), size.getHeight());
            mTextureReader.setOnImageAvailableListener(getImageAvailableListener());
            Log.d(TAG, getClass().getName() + ".initTextureReader() mTextureReader = " + size.toString());
        }
    }

    private CameraDevice.StateCallback getDeviceStateCallback() {
        if (mDeviceStateCallback == null) {
            mDeviceStateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {//成功打开摄像头
                    Log.d(TAG, getClass().getName() + ".onOpened()");
                    mCameraDevice = camera;
                    createCaptureSession();//创建会话
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {//未能连接到摄像头，提示是否被占用
                    Log.e(TAG, getClass().getName() + ".onDisconnected()");
                    mCameraLock.release();
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_DISCONNECTED));
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {//出错
                    Log.e(TAG, getClass().getName() + ".onError() error = " + error);
                    mCameraLock.release();
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_OPEN));
                    }
                }
            };
        }
        return mDeviceStateCallback;
    }

    private void createCaptureSession() {
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mSurfaceList.clear();
            Surface surface = new Surface(mPreviewTexture);
            mPreviewBuilder.addTarget(surface);//添加预览的Surface
            mSurfaceList.add(surface);
            if (mTextureReader != null) {
                Surface rendererSurface = new Surface(mTextureReader.getSurfaceTexture());
                mPreviewBuilder.addTarget(rendererSurface);
                mSurfaceList.add(rendererSurface);
            }
            mCameraDevice.createCaptureSession(mSurfaceList, getSessionStateCallback(), mBackgroundHandler);//创建会话
        } catch (CameraAccessException e) {//创建会话失败
            if (mCameraLock.availablePermits() < 1) {
                mCameraLock.release();
            }
            Log.e(TAG, getClass().getName() + ".createCaptureSession() : " + e);
            if (mCurThreadHandler != null) {
                mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CREATSESSION));
            }
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
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);//自动曝光
                        mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
                        if (mCurThreadHandler != null) {
                            mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                        }
                    } catch (CameraAccessException e) {
                        Log.e(TAG, getClass().getName() + ".onConfigured() : " + e);
                        if (mCurThreadHandler != null) {
                            mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
                        }
                    }
                    if (mCameraLock.availablePermits() < 1) {
                        mCameraLock.release();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, getClass().getName() + ".onConfigureFailed()");
                    if (mCameraLock.availablePermits() < 1) {
                        mCameraLock.release();
                    }
                    if (mCurThreadHandler != null) {
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
                    }
                }
            };
        }
        return mSessionStateCallback;
    }

    private TextureReader.OnImageAvailableListener getImageAvailableListener() {
        if (mOnImageAvailableListener == null) {
            mOnImageAvailableListener = new TextureReader.OnImageAvailableListener() {

                @Override
                public void onFrameAvailable(byte[] frameData, int width, int height) {
                    if (mGraphicDecoder != null) {
                        if (mClipRectRatio == null || mClipRectRatio.isEmpty()) {//当未设置图像识别剪裁时，应以View的大小进行设置，防止未显示的图像被误识别
                            setFrameRect(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                        }
                        mGraphicDecoder.decode(frameData, width, height, mClipRectRatio);
                    }
                    if (isBrightnessFeedbackEnabled && mCameraListener != null) {//启用亮度回馈
                        //采集步长，总共采集100个像素点
                        final int length = width * height;
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
        return mOnImageAvailableListener;
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
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {//开启成功
                if (mCameraListener != null) {
                    //这里将宽高对调因为Camera2将图像顺时针旋转过90度
                    mCameraListener.openCameraSuccess(mSurfaceSize.getHeight(), mSurfaceSize.getWidth(), (4 - mOrientation) % 4 * 90);
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
            case HANDLER_FAIL_CREATSESSION://会话创建失败
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

}
