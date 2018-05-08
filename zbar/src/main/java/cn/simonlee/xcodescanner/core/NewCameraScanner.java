package cn.simonlee.xcodescanner.core;

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
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-02-02
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

    private final String TAG = "XCodeScanner";

    private int mOrientation;//设备方向 0 朝上 1朝左 2朝下 3朝右

    private Size mSurfaceSize;//图像帧的尺寸
    private Size mPreviewSize;//预览View的尺寸
    private RectF mRectClipRatio;//扫码框区域相对于图像帧所占比例，且已根据mOrientation做校正

    private BaseHandler mCurThreadHandler;//实例化线程对应的handler
    private BaseHandler mBackgroundHandler;//子线程对应的handler
    private HandlerThread mBackgroundThread;//子线程

    private CameraListener mCameraListener;//相机设备回调

    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewBuilder;

    private String mCameraId;//相机ID

    private TextureReader mTextureReader;//用于获取帧数据

    private CameraDevice.StateCallback mDeviceStateCallback;
    private CameraCaptureSession.StateCallback mSessionStateCallback;
    private TextureReader.OnImageAvailableListener mOnImageAvailableListener;

    private final Semaphore mCameraLock;

    private volatile static NewCameraScanner instance;

    public static NewCameraScanner getInstance() {
        if (instance == null) {
            synchronized (NewCameraScanner.class) {
                if (instance == null) {
                    instance = new NewCameraScanner();
                }
            }
        }
        return instance;
    }

    private NewCameraScanner() {
        mCameraLock = new Semaphore(1);
    }

    @Override
    public void openCamera(Context context) {
        Log.d(TAG, getClass().getName() + ".openCamera()");
        final Context finalContext = context.getApplicationContext();
        startBackgroundThread();
        takeOrientation(finalContext);//获取设备方向
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
                    initSurfaceSize(outputSizes, mPreviewSize.getWidth(), mPreviewSize.getHeight());
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
            mBackgroundThread = new HandlerThread("NewCameraScanner");
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
    private void takeOrientation(Context context) {
        //TODO
//        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            mOrientation = windowManager.getDefaultDisplay().getRotation();
        }
        Log.d(TAG, getClass().getName() + ".takeOrientation() mOrientation = " + mOrientation);
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
    private void initSurfaceSize(Size[] sizeArray, int previewWidth, int previewHeight) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {
            mSurfaceSize = getBigEnoughSize(sizeArray, previewHeight, previewWidth);
        } else {
            mSurfaceSize = getBigEnoughSize(sizeArray, previewWidth, previewHeight);
        }
        mSurfaceTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
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
            mPreviewBuilder = null;
            mCaptureSession = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCameraLock.release();
    }

    @Override
    public void openFlash() {
        try {
            mCameraLock.acquire();
            if (mCaptureSession != null && mPreviewBuilder != null) {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void closeFlash() {
        try {
            mCameraLock.acquire();
            if (mCaptureSession != null && mPreviewBuilder != null) {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
            }
            mCameraLock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
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
        mSurfaceSize = null;
        mPreviewSize = null;
        mCameraManager = null;
        mRectClipRatio = null;
        mPreviewBuilder = null;
        mDeviceStateCallback = null;
        mSessionStateCallback = null;
        mCameraListener = null;
        mOnImageAvailableListener = null;
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
    public void setCameraListener(CameraListener cameraListener) {
        this.mCameraListener = cameraListener;
    }

    @Override
    public void setGraphicDecoder(GraphicDecoder graphicDecoder) {
        this.mGraphicDecoder = graphicDecoder;
    }

    @Override
    public void setFrameRect(int frameLeft, int frameTop, int frameRight, int frameBottom) {
        Log.d(TAG, getClass().getName() + ".setFrameRect() mOrientation = " + mOrientation + " frameRect = " + frameLeft + "-" + frameTop
                + "-" + frameRight + "-" + frameBottom);
        if (mRectClipRatio == null) {
            mRectClipRatio = new RectF();
        }
        if (frameLeft >= frameRight || frameTop >= frameBottom) {
            mRectClipRatio.setEmpty();
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
        float leftRatio = calculateRatio(ratio * frameLeft / surfaceWidth);//计算扫描框的左边在图像帧中所处的位置
        float rightRatio = calculateRatio(ratio * frameRight / surfaceWidth);//计算扫描框的右边在图像帧中所处的位置
        float topRatio = calculateRatio(ratio * frameTop / surfaceHeight);//计算扫描框的顶边在图像帧中所处的位置
        float bottomRatio = calculateRatio(ratio * frameBottom / surfaceHeight);//计算扫描框的底边在图像帧中所处的位置
        switch (mOrientation) {//根据旋转角度对位置进行校正
            case Surface.ROTATION_0: {
                mRectClipRatio.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
                break;
            }
            case Surface.ROTATION_90: {
                mRectClipRatio.set(leftRatio, topRatio, rightRatio, bottomRatio);
                break;
            }
            case Surface.ROTATION_180: {
                mRectClipRatio.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
                break;
            }
            case Surface.ROTATION_270: {
                mRectClipRatio.set(1 - rightRatio, 1 - bottomRatio, 1 - leftRatio, 1 - topRatio);
                break;
            }
        }
        Log.d(TAG, getClass().getName() + ".setFrameRect() mRectClipRatio = " + mRectClipRatio);
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
                Surface rendererSurface = new Surface(mTextureReader.getSurfaceTexture());
                mPreviewBuilder.addTarget(rendererSurface);
                surfaceList.add(rendererSurface);
            }
            mCameraDevice.createCaptureSession(surfaceList, getSessionStateCallback(), mBackgroundHandler);//创建会话
        } catch (CameraAccessException e) {//创建会话失败
            mCameraLock.release();
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
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);//自动曝光
                        mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);//无限次的重复获取图像
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                    } catch (CameraAccessException e) {
                        Log.e(TAG, getClass().getName() + ".onConfigured() : " + e);
                        mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
                    }
                    mCameraLock.release();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, getClass().getName() + ".onConfigureFailed()");
                    mCameraLock.release();
                    mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_FAIL_CONFIG));
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
                        if (mRectClipRatio == null || mRectClipRatio.isEmpty()) {//当未设置图像识别剪裁时，应以View的大小进行设置，防止未显示的图像被误识别
                            setFrameRect(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                        }
                        mGraphicDecoder.decode(frameData, width, height, mRectClipRatio, 0);
                    }
                }
            };
        }
        return mOnImageAvailableListener;
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
    }

}
