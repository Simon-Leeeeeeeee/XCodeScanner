package com.demo.camera2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
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
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author lixiaomeng
 * @WeChat 13510219066
 * 坑
 * 1.使用旧CameraAPI，mCamera.setAutoFocusMoveCallback(...)在有些设备上并不能很好地自动对焦。原因：未知，可能是碎片化导致。解决：无法解决
 * 2.换用新camera2API,适用于android5.0及以上。
 * 3.图像出现锯齿，且变形严重。原因：未调用mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
 * 4.无实时byte[]数据。原因：未调用mPreviewBuilder.addTarget(mImageReader.getSurface());
 * 5.实时byte[]数据获取导致严重丢帧。原因：未知，可能是mImageReader解析数据阻塞线程。解决：调整mImageReader尺寸，使像素密度小于75W
 * 6.无法正确解析结果。原因：a.原始数据格式为YUV，目标格式为Y8。应舍弃UV分量，仅传入Y分量灰度图。b.目标size不应为mPreviewSize
 * <p>
 * 后续...
 * 问题5只是治标不治本，也许还能通过更好的办法获取byte[]
 */

@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Scanner implements BaseHandler.BaseHandlerListener {

    private final int HANDLER_SUCCESS_OPEN = 80001;
    private final int HANDLER_SUCCESS_RESULT = 80002;
    private final int HANDLER_FAIL_OPEN = 90002;
    private final int HANDLER_FAIL_CONFIG = 90003;
    private final int HANDLER_FAIL_DISCONNECTED = 90004;
    private final int HANDLER_FAIL_NO_PERMISSION = 90005;

    private SurfaceTexture mSurfaceTexture;

    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewBuilder;

    private ImageReader mImageReader;
    private volatile ImageScanner mImageScanner;
    private ExecutorService mExecutorService;

    private final String TAG = "Camera2Scanner";
    private String mCameraId;
    private Size mPreviewSize;
    private BaseHandler mSubHandler;//子线程Handler
    private volatile BaseHandler mMainHandler;//主线程Handler

    private WeakReference<ScannerListener> weakReference;//弱引用，防止内存泄漏
    private final Object lock_Zbar = new Object();//互斥锁
    private final Object lock_Handler = new Object();//互斥锁
    private String mResult;
    private int count;

//    private RenderScript mRenderScript;

    public interface ScannerListener {

        void openCameraError();

        void openCameraSuccess(int width, int height);

        void scanSuccess(String result);

        void noCameraPermission();

        void cameraDisconnected();
    }

    public Camera2Scanner(ScannerListener ScannerListener) {
        HandlerThread handlerThread = new HandlerThread("Camera2Scanner");
        handlerThread.start();
        this.mMainHandler = new BaseHandler(this);
        this.mSubHandler = new BaseHandler(null, handlerThread.getLooper());
//        this.mMainHandler = new BaseHandler(this,APP.getApp().getMainLooper());
        this.weakReference = new WeakReference<>(ScannerListener);
    }

    public void detach() {
        if (mSubHandler != null) {
            closeCamera();
        }
        if (mMainHandler != null) {
            synchronized (lock_Handler) {
                mMainHandler.clear();
                mMainHandler = null;
            }
        }
        if (weakReference != null) {
            weakReference.clear();
            weakReference = null;
        }
    }

    public void setSurfaceHolder(SurfaceTexture surfaceTexture) {
        this.mSurfaceTexture = surfaceTexture;
    }

    public void openCamera(final int width, final int height) {
//      mRenderScript = renderScript;
        mSubHandler.post(new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(APP.getApp(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_NO_PERMISSION));
                    return;//没有权限，返回
                }
                mCameraManager = (CameraManager) APP.getApp().getSystemService(Context.CAMERA_SERVICE);
                if (mCameraManager != null) {
                    try {
                        StreamConfigurationMap map = null;
                        for (String cameraId : mCameraManager.getCameraIdList()) {//查询CameraID
                            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                            Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                                mCameraId = cameraId;
                                map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                                break;
                            }
                        }
                        if (map != null) {
                            initImageSize(map, width, height);//初始化mImageReader，及预览尺寸
                            mCameraManager.openCamera(mCameraId, mDeviceStateCallback, mSubHandler);
                            return;//未产生异常，返回。
                        }
                    } catch (Exception ignored) {
                    }
                }
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
            }
        });
    }

    private void closeCamera() {
        mSubHandler.removeAll();
        mSubHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCaptureSession != null) {
                    try {
                        mCaptureSession.stopRepeating();
                        mCaptureSession.abortCaptures();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                    mSurfaceTexture = null;
                }
                if (mImageReader != null) {
                    mImageReader.close();
                    mImageReader = null;
                }
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (mExecutorService != null) {
                    mExecutorService.shutdownNow();
                    mExecutorService = null;
                }
                if (mImageScanner != null) {
                    synchronized (lock_Zbar) {
                        mImageScanner.destroy();
                        mImageScanner = null;
                    }
                }
                mSubHandler.clear();
                mSubHandler = null;
            }
        });
    }

    private void initImageSize(StreamConfigurationMap map, int width, int height) {
        //从支持的尺寸列表中获取密度小于750000的最大尺寸(该尺寸为实时获取byte[]的尺寸，750000上限足够解析出数据，过大会导致预览画面严重丢帧)
        Size streamSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)), new Comparator<Size>() {
            @Override
            public int compare(Size cur, Size pre) {//返回值大于0，则保留cur
                int preSize = pre.getWidth() * pre.getHeight();
                int curSize = cur.getWidth() * cur.getHeight();
                if (preSize > 750000) {
                    return preSize - curSize;//上一个密度大于750000，取最小的个
                } else if (curSize > 750000) {//上一个密度小于750000，当前密度大于750000，取上一个
                    return -1;
                } else {//上一个密度小于750000，当前密度也小于750000，取最大的个
                    return curSize - preSize;
                }
            }
        });
        float ratio_wh = 1F * streamSize.getWidth() / streamSize.getHeight();
        mImageReader = ImageReader.newInstance(streamSize.getWidth(), streamSize.getHeight(), ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mSubHandler);
        mPreviewSize = findOptimalPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height, ratio_wh);
        Log.d(TAG, getClass().getName() + ".initImageSize() streamSize = " + streamSize + " , previewSize = " + mPreviewSize);
    }

    private void initExecutorService() {
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
            mImageScanner = new ImageScanner();
            mImageScanner.setConfig(0, Config.X_DENSITY, 3);
            mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
            mImageScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
            mImageScanner.setConfig(Symbol.PARTIAL, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.EAN8, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.UPCE, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.ISBN10, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.UPCA, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.EAN13, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.ISBN13, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.I25, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.DATABAR, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.DATABAR_EXP, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.CODABAR, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.CODE39, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.PDF417, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.QRCODE, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.CODE93, Config.ENABLE, 1);
            mImageScanner.setConfig(Symbol.CODE128, Config.ENABLE, 1);
        }
    }

    //获取大于View的尺寸，保证清晰度
    private Size findOptimalPreviewSize(Size[] outputSizes, int width, int height, float ratio_wh) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size size : outputSizes) {
            if (size.getWidth() == size.getHeight() * ratio_wh && size.getWidth() >= width && size.getHeight() >= height) {
                bigEnough.add(size);
            }
        }
        Size optimalPreviewSize = null;
        if (bigEnough.size() > 0) {
            optimalPreviewSize = Collections.min(bigEnough, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                            (long) rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        bigEnough.clear();
        if (optimalPreviewSize != null) {
            return optimalPreviewSize;
        } else {
            return outputSizes[0];
        }
    }

    //开始预览
    private void startPreview() throws CameraAccessException {//开始预览
        Log.e(TAG, getClass().getName() + ".startPreview()");
        initExecutorService();//初始化线程池及zBar扫描器
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());//设置预览尺寸
        Surface surface = new Surface(mSurfaceTexture);
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

//        mProcessor = new Progress(mRenderScript, mPreviewSize);
//        mProcessor.setOutputSurface(surface);
//        mInputSurface = mProcessor.getInputNormalSurface();
//        mPreviewBuilder.addTarget(mInputSurface);
//        camera.createCaptureSession(Arrays.asList(mInputSurface), mCameraCaptureSessionStateCallback, null);
        mPreviewBuilder.addTarget(surface);//添加预览的Surface
        mPreviewBuilder.addTarget(mImageReader.getSurface());//添加ImageReader的Surface
        List<Surface> surfaceList = Arrays.asList(surface, mImageReader.getSurface());
        mCameraDevice.createCaptureSession(surfaceList, mSessionStateCallback, mSubHandler);//创建会话
    }

    //摄像头设备状态回调
    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {//成功打开摄像头
            Log.e(TAG, getClass().getName() + ".onOpened()");
            try {
                mCameraDevice = camera;
                startPreview();//开始预览
            } catch (CameraAccessException ignored) {//预览出错
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {//未能连接到摄像头，提示是否被占用
            Log.e(TAG, getClass().getName() + ".onDisconnected()");
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_DISCONNECTED));
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {//出错
            Log.e(TAG, getClass().getName() + ".onError()");
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
        }
    };

    //会话状态回调
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {//会话配置完成
            Log.e(TAG, getClass().getName() + ".onConfigured()");
            mCaptureSession = session;
            try {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);//自动对焦
                //mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//自动闪光灯
                mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mSubHandler);//无限次的重复获取图像
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_SUCCESS_OPEN));
            } catch (CameraAccessException ignored) {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_OPEN));
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, getClass().getName() + ".onConfigureFailed()");
            mMainHandler.sendMessage(mMainHandler.obtainMessage(HANDLER_FAIL_CONFIG));
        }
    };

    //ImageAvailable监听
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();//ImageFormat.YUV_420_888
                if (planes != null && planes.length == 3) {//YUV三个分量 Y:灰度 UV:色度
                    ByteBuffer byteBuffer = planes[0].getBuffer();//取Y分量
                    if (byteBuffer != null) {
                        byte[] data = new byte[byteBuffer.remaining()];
                        byteBuffer.get(data);
                        if (mExecutorService != null) {
                            mExecutorService.execute(new ResolveRunnable(data, image.getWidth(), image.getHeight()));
                        }
                        byteBuffer.clear();
                    }
                }
                image.close();
            }
        }

    };

    private class ResolveRunnable implements Runnable {

        private final byte[] data;
        private final int width;
        private final int height;

        private ResolveRunnable(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        @Override
        public void run() {
            net.sourceforge.zbar.Image barcode = new net.sourceforge.zbar.Image(width, height, "Y800");//Y800为zBar规定格式，同ImageFormat.Y8
            barcode.setData(data);
            SymbolSet symbolSet = null;
            synchronized (lock_Zbar) {
                if (mImageScanner != null && mImageScanner.scanImage(barcode) != 0) {
                    symbolSet = mImageScanner.getResults();
                }
            }
            if (symbolSet == null) return;
            for (Symbol symbol : symbolSet) {
                String result = symbol.getData();
                if (result != null && result.length() > 0) {
                    synchronized (lock_Handler) {
                        if (mMainHandler != null) {
                            Message msg = mMainHandler.obtainMessage(HANDLER_SUCCESS_RESULT);
                            msg.obj = result;
                            mMainHandler.sendMessage(msg);
                        }
                    }
                    break;
                }
            }

        }

    }

    @Override
    public void handleMessage(Message msg) {
        Log.d(TAG, getClass().getName() + ".handleMessage()");
        switch (msg.what) {
            case HANDLER_SUCCESS_OPEN: {
                if (weakReference.get() != null) {
                    int orientation = APP.getApp().getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        weakReference.get().openCameraSuccess(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        weakReference.get().openCameraSuccess(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                }
                break;
            }
            case HANDLER_SUCCESS_RESULT: {
                if (msg.obj.equals(mResult)) {
                    count++;
                    if (count > 2 && weakReference.get() != null) {
                        weakReference.get().scanSuccess((String) msg.obj);
                        count = 0;
                    }
                } else {
                    mResult = (String) msg.obj;
                    count = 0;
                }
                Log.d(TAG, getClass().getName() + ".handleMessage() result = " + msg.obj + " , count = " + count);
                break;
            }
            case HANDLER_FAIL_OPEN:
            case HANDLER_FAIL_CONFIG: {
                closeCamera();
                if (weakReference.get() != null) {
                    weakReference.get().openCameraError();
                }
                break;
            }
            case HANDLER_FAIL_DISCONNECTED: {
                closeCamera();
                if (weakReference.get() != null) {
                    weakReference.get().cameraDisconnected();
                }
                break;
            }
            case HANDLER_FAIL_NO_PERMISSION: {
                closeCamera();
                if (weakReference.get() != null) {
                    weakReference.get().noCameraPermission();
                }
                break;
            }
            default: {
                break;
            }
        }
    }

}
