package com.simonlee.scanner.core;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * <p>
 * 坑
 * 1.使用旧CameraAPI，mCamera.setAutoFocusMoveCallback(...)在有些设备上并不能很好地自动对焦。原因：未知，可能是碎片化导致。解决：无法解决
 * 2.换用新camera2API,适用于android5.0及以上。
 * 3.图像出现锯齿，且变形严重。原因：未调用mSurfaceTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
 * 4.无实时byte[]数据。原因：未调用mPreviewBuilder.addTarget(mImageReader.getSurface());
 * 5.无法正确解析结果。原因：a.原始数据格式为YUV，目标格式为Y8。应舍弃UV分量，仅传入Y分量灰度图。b.目标size不应为mPreviewSize
 * 6.实时byte[]数据获取导致严重丢帧。原因：未知，可能是mImageReader解析数据阻塞线程。解决：
 * <p>a.使用TextureReader解析帧数据，推荐
 * <p>b.调整mImageReader尺寸，使像素密度小于75万
 * <p>c.在onSurfaceTextureUpdated中通过TextureView获取bitmap，转YUV。
 * <p>d.RenderScript，无法解决。与ImageReader同样会降低FPS，参照createAllcation()。
 * <p>e.待发现...
 */

@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Scanner implements BaseHandler.BaseHandlerListener {

    private final int HANDLER_SUCCESS_OPEN = 70001;
    private final int HANDLER_FAIL_CLOSED = 80001;
    private final int HANDLER_FAIL_OPEN = 80002;
    private final int HANDLER_FAIL_CONFIG = 80003;
    private final int HANDLER_FAIL_DISCONNECTED = 80004;
    private final int HANDLER_FAIL_NO_PERMISSION = 80005;

    private final Context mContext;

    private SurfaceTexture mSurfaceTexture;

    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewBuilder;

    private GraphicDecoder mGraphicDecoder;//由Activity创建，持有Activity对象。仅用于解析数据

    private final String TAG = "Camera2Scanner";

    private int mOrientation;//屏幕方向 0 朝上 1朝左 2朝下 3朝右

    private String mCameraId;//相机ID
    private Size mPreviewSize;//预览尺寸
    private Size mSurfaceSize;//Surface尺寸
    private RectF mFrameRatioRect = new RectF();//扫码框区域相对于预览尺寸所占比例，且已根据mOrientation做校正

    private BaseHandler mSubHandler;//子线程Handler
    private BaseHandler mMainHandler;//主线程Handler

    private int mFrameReaderType;//帧数据获取方式 0:TextureReader(默认); 1:ImageReader(不推荐)

    private ImageReader mImageReader;//用于获取帧数据
    private long mImageReaderSuitPixels = 750000L;//ImageReader的最大尺寸

    private TextureReader mTextureReader;//用于获取帧数据

    private CameraDeviceListener mCameraDeviceListener;//相机设备回调

    private volatile boolean isCameraOpened;//相机开启状态

    public interface CameraDeviceListener {

        void openCameraError();

        void openCameraSuccess(int surfaceWidth, int surfaceHeight, int orientation);

        void noCameraPermission();

        void cameraDisconnected();
    }

    public Camera2Scanner(Context context) {
        this.mContext = context;
        HandlerThread handlerThread = new HandlerThread("Camera2Scanner");
        handlerThread.start();
        this.mMainHandler = new BaseHandler(this);
        this.mSubHandler = new BaseHandler(null, handlerThread.getLooper());
    }

    /**
     * 设置相机设备监听器
     */
    public void setCameraDeviceListener(CameraDeviceListener cameraDeviceListener) {
        this.mCameraDeviceListener = cameraDeviceListener;
    }

    /**
     * 设置图像解码器
     */
    public void setGraphicDecoder(GraphicDecoder graphicDecoder) {
        this.mGraphicDecoder = graphicDecoder;
    }

    /**
     * 设置SurfaceHolder
     */
    public void setSurfaceHolder(SurfaceTexture surfaceTexture) {
        this.mSurfaceTexture = surfaceTexture;
    }

    /**
     * 设置预览View的尺寸
     */
    public void setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
        Log.d(TAG, getClass().getName() + ".setPreviewSize() mPreviewSize = " + mPreviewSize.toString());
    }

    /**
     * 设置扫码识别区域，并根据mPreviewSize的宽高计算该区域占比，最后根据屏幕方向进行校正
     */
    public void setFrameRect(int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom || mPreviewSize == null) {
            mFrameRatioRect.setEmpty();
            return;
        }
        float width = mPreviewSize.getWidth();
        float height = mPreviewSize.getHeight();
        float leftRatio = left / width;
        float rightRatio = right / width;
        float topRatio = top / height;
        float bottomRatio = bottom / height;
        switch (mOrientation) {
            case Surface.ROTATION_0: {
                mFrameRatioRect.set(topRatio, 1 - rightRatio, bottomRatio, 1 - leftRatio);
                break;
            }
            case Surface.ROTATION_90: {
                mFrameRatioRect.set(leftRatio, topRatio, rightRatio, bottomRatio);
                break;
            }
            case Surface.ROTATION_180: {
                mFrameRatioRect.set(1 - bottomRatio, leftRatio, 1 - topRatio, rightRatio);
                break;
            }
            case Surface.ROTATION_270: {
                mFrameRatioRect.set(1 - rightRatio, 1 - bottomRatio, 1 - leftRatio, 1 - topRatio);
                break;
            }
        }
        Log.d(TAG, getClass().getName() + ".setFrameRect() mFrameRatioRect = " + mFrameRatioRect);
    }

    /**
     * 设置ImageReader的最大尺寸，在openCamera后设置无效
     */
    public void setImageReaderSuitPixels(long suitPixels) {
        this.mImageReaderSuitPixels = suitPixels;
    }

    /**
     * 设置帧数据获取方式，在openCamera后设置无效
     * 0: TextureReader(默认);
     * 1: ImageReader(不推荐)
     */
    public void setFrameReaderType(int type) {
        this.mFrameReaderType = type;
    }

    /**
     * 开启相机，参数为SurfaceTexture的宽高
     */
    public void openCamera() {
        if (isCameraOpened) return;
        isCameraOpened = true;
        setOrientation();
        mSubHandler.post(new Runnable() {
            @Override
            public void run() {
                if (checkCameraPermission()) {
                    try {
                        if (mCameraManager == null) {
                            mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                        }
                        StreamConfigurationMap configurationMap = getBackCameraStreamConfigurationMap();//获取后置摄像头配置
                        Size[] outputSizes = configurationMap.getOutputSizes(SurfaceTexture.class);
                        initSurfaceSize(outputSizes);
                        if (mFrameReaderType == 1) {
                            initImageReader(outputSizes);
                        } else {
                            initTextureReader(outputSizes);
                        }
                        mCameraManager.openCamera(mCameraId, mDeviceStateCallback, mSubHandler);//开启相机
                    } catch (Exception exception) {//开启相机失败
                        mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
                    }
                } else {//权限不足
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_NO_PERMISSION));
                }
            }
        });
    }

    /**
     * 关闭相机
     */
    public void closeCamera() {
        Log.d(TAG, getClass().getName() + ".closeCamera()");
        isCameraOpened = false;
//        mSubHandler.removeAll();
        mSubHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCaptureSession != null) {
                    try {
                        mCaptureSession.stopRepeating();
                        mCaptureSession.abortCaptures();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (mCameraDevice != null) {
                    try {
                        mCameraDevice.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mCameraDevice = null;
                }
            }
        });
    }

    public void detach() {
        Log.d(TAG, getClass().getName() + ".detach()");
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        if (mTextureReader != null) {
            mTextureReader.close();
        }
        if (mImageReader != null) {
            mImageReader.close();
        }
        if (mMainHandler != null) {
            mMainHandler.clear();
        }
    }

    /**
     * 设置window方向
     */
    private void setOrientation() {
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            mOrientation = windowManager.getDefaultDisplay().getRotation();
        }
        Log.d(TAG, getClass().getName() + ".setOrientation() mOrientation = " + mOrientation);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
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
     * 初始化SurfaceSize
     */
    private void initSurfaceSize(Size[] outputSizes) {
        if (mOrientation == Surface.ROTATION_0 || mOrientation == Surface.ROTATION_180) {
            mSurfaceSize = getBigEnoughSize(outputSizes, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        } else {
            mSurfaceSize = getBigEnoughSize(outputSizes, mPreviewSize.getWidth(), mPreviewSize.getHeight());
        }
        mSurfaceTexture.setDefaultBufferSize(mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        Log.d(TAG, getClass().getName() + ".initSurfaceSize() mSurfaceSize = " + mSurfaceSize.toString());
    }

    /**
     * 初始化ImageReader
     */
    private void initImageReader(Size[] outputSizes) {
        if (mImageReader == null) {
//            Size[] outputSizes = configurationMap.getOutputSizes(ImageFormat.YUV_420_888);
            Size size = getMaxSuitSize(outputSizes, mSurfaceSize, mImageReaderSuitPixels);//像素过大会导致卡顿严重！
            mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.YUV_420_888, 1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mSubHandler);
            Log.d(TAG, getClass().getName() + ".initImageReader() mImageReaderSize = " + size.toString());
        }
    }

    /**
     * 初始化TextureReader
     */
    private void initTextureReader(Size[] outputSizes) {
        if (mTextureReader == null) {
            Size size = getMaxSuitSize(outputSizes, mSurfaceSize, 2073600L);//像素过大会导致二维码解析失败！此处限制为1080P
            mTextureReader = new TextureReader(size.getWidth(), size.getHeight());
            mTextureReader.setOnFrameAvailableListener(mOnFrameAvailableListener);
            Log.d(TAG, getClass().getName() + ".initTextureReader() mTextureReader = " + size.toString());
        }
    }

    /**
     * 返回sizes中宽高大于最小宽高的最小尺寸
     */
    private Size getBigEnoughSize(Size[] sizes, int minWidth, int minHeight) {
        Size curSize = sizes[0];
        boolean curBigEnough = curSize.getWidth() >= minWidth && curSize.getHeight() >= minHeight;
        for (int i = 1; i < sizes.length; i++) {
            Size nextSize = sizes[i];
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
     * 返回sizes中 1.宽高比与similarSize相似，2.像素不超过suitPixels 的最大尺寸
     */
    private Size getMaxSuitSize(Size[] sizes, final Size similarSize, final Long maxPixels) {
        Size curSize = sizes[0];
        boolean curSimilar = similarSize != null && similarSize.getHeight() * curSize.getWidth() == similarSize.getWidth() * curSize.getHeight();
        for (int i = 1; i < sizes.length; i++) {
            Size nextSize = sizes[i];
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

    /**
     * 摄像头设备状态回调，成功打开摄像头后准备预览
     */
    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {//成功打开摄像头
            Log.d(TAG, getClass().getName() + ".onOpened()");
            if (isCameraOpened) {
                try {
                    mCameraDevice = camera;
                    preparePreview();//准备预览
                } catch (CameraAccessException ignored) {//预览出错
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
                }
            } else {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_CLOSED));
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {//未能连接到摄像头，提示是否被占用
            Log.e(TAG, getClass().getName() + ".onDisconnected()");
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_DISCONNECTED));
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {//出错
            Log.e(TAG, getClass().getName() + ".onError() error = " + error);
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
        }
    };

    /**
     * 准备预览
     */
    private void preparePreview() throws CameraAccessException {
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        List<Surface> surfaceList = new ArrayList<>();
        Surface surface = new Surface(mSurfaceTexture);
        mPreviewBuilder.addTarget(surface);//添加预览的Surface
        surfaceList.add(surface);
        if (mFrameReaderType == 1) {
            if (mImageReader != null) {
                Surface imageReaderSurface = mImageReader.getSurface();
                mPreviewBuilder.addTarget(imageReaderSurface);
                surfaceList.add(imageReaderSurface);
            }
        } else {
            if (mTextureReader != null) {
                Surface rendererSurface = mTextureReader.getSurface();
                mPreviewBuilder.addTarget(rendererSurface);
                surfaceList.add(rendererSurface);
            }
        }
        mCameraDevice.createCaptureSession(surfaceList, mSessionStateCallback, mSubHandler);//创建会话
    }

    /**
     * 摄像头预览配置会话状态回调，成功配置会话后设置对焦模式，开始获取图像
     */
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {//会话配置完成
            Log.d(TAG, getClass().getName() + ".onConfigured()");
            if (isCameraOpened) {
                mCaptureSession = session;
                try {
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);//自动对焦
                    //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//自动闪光灯
                    mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mSubHandler);//无限次的重复获取图像
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
                } catch (CameraAccessException ignored) {
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
                }
            } else {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_CLOSED));
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, getClass().getName() + ".onConfigureFailed()");
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_CONFIG));
        }
    };

    /**
     * ImageReader的ImageAvailable监听，当有帧图像到达时会进行回调
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();//ImageFormat.YUV_420_888
                if (planes != null && planes.length == 3) {//YUV三个分量 Y:灰度 UV:色度
                    ByteBuffer byteBuffer = planes[0].getBuffer();//取Y分量
                    if (byteBuffer != null) {
                        byte[] data = new byte[byteBuffer.remaining()];
                        byteBuffer.get(data);
                        if (mGraphicDecoder != null) {
                            mGraphicDecoder.decode(data, image.getWidth(), image.getHeight(), mFrameRatioRect);
                        }
                        byteBuffer.clear();
                    }
                }
                image.close();
            }
        }
    };

    private TextureReader.OnFrameAvailableListener mOnFrameAvailableListener = new TextureReader.OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(byte[] frameData, int width, int height) {
            if (mGraphicDecoder != null) {
                mGraphicDecoder.decode(frameData, width, height, mFrameRatioRect);
            }
        }
    };

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {//开启成功
                if (mCameraDeviceListener != null) {
                    mCameraDeviceListener.openCameraSuccess(mSurfaceSize.getWidth(), mSurfaceSize.getHeight(), mOrientation);
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

//    private void createAllcation(RenderScript renderScript, final Size streamSize) {
//        Type.Builder yuvTypeBuilder = new Type.Builder(renderScript, Element.YUV(renderScript));
//        yuvTypeBuilder.setX(streamSize.getWidth());
//        yuvTypeBuilder.setY(streamSize.getHeight());
//        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
//        Allocation allocation = Allocation.createTyped(renderScript, yuvTypeBuilder.create(),
//                Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
//        mInputSurface = allocation.getSurface();//在startPreview中addTarget(mInputSurface)即可接收Camera实时数据，但是同样会降低FPS
//        allocation.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
//            @Override
//            public void onBufferAvailable(Allocation allocation) {
//                allocation.ioReceive();
//                int size = allocation.getBytesSize();
//                int[] bytes = new int[size];
//                allocation.copyFrom(bytes);
//                //bytes即为YUV_420_888格式字节数组。
//            }
//        });
//    }

}
