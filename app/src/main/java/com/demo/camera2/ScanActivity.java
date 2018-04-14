package com.demo.camera2;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;

import com.simonlee.scanner.core.CameraScanner;
import com.simonlee.scanner.core.GraphicDecoder;
import com.simonlee.scanner.core.NewCameraScanner;
import com.simonlee.scanner.core.OldCameraScanner;
import com.simonlee.scanner.core.ZBarDecoder;
import com.simonlee.scanner.view.AdjustTextureView;
import com.simonlee.scanner.view.ScannerFrameView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public class ScanActivity extends AppCompatActivity implements CameraScanner.CameraDeviceListener, TextureView.SurfaceTextureListener, GraphicDecoder.DecodeListener {

    private AdjustTextureView mTextureView;
    private ScannerFrameView mScannerFrameView;

    private CameraScanner mCameraScanner;
    private ZBarDecoder mZBarDecoder;

    private final String TAG = "CameraScanner";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, getClass().getName() + ".onCreate()");
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra("type", R.id.btn_scan_relative);
        setContentView(id == R.id.btn_scan_constraint ? R.layout.activity_scan_constraint : R.layout.activity_scan_relative);

        mScannerFrameView = findViewById(R.id.scannerframe);
        mTextureView = findViewById(R.id.textureview);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mCameraScanner = OldCameraScanner.getInstance();
        } else {
            mCameraScanner = NewCameraScanner.getInstance();
        }

        mCameraScanner.setCameraDeviceListener(this);
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, getClass().getName() + ".onRestart()");
        if (mTextureView.isAvailable()) {
            //部分机型转到后台不会走onSurfaceTextureDestroyed()，因此isAvailable()一直为true，转到前台后不会再调用onSurfaceTextureAvailable()
            //因此需要手动开启相机
            mCameraScanner.setSurfaceTexture(mTextureView.getSurfaceTexture());
            mCameraScanner.setPreviewSize(mTextureView.getWidth(), mTextureView.getHeight());
            mCameraScanner.openCamera(this.getApplicationContext());
        }
        super.onRestart();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, getClass().getName() + ".onPause()");
        mCameraScanner.closeCamera();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, getClass().getName() + ".onDestroy()");
        mCameraScanner.setGraphicDecoder(null);
        if (mZBarDecoder != null) {
            mZBarDecoder.detach();
        }
        mCameraScanner.detach();
        super.onDestroy();
    }

    @Override
    public void openCameraSuccess(int frameWidth, int frameHeight, int frameDegree) {
        Log.e(TAG, getClass().getName() + ".openCameraSuccess() frameWidth = " + frameWidth + " , frameHeight = " + frameHeight + " , frameDegree = " + frameDegree);
        mTextureView.setImageFrameMatrix(frameWidth, frameHeight, frameDegree);
        if (mZBarDecoder == null) {
            mZBarDecoder = new ZBarDecoder(this);
        }
        //该区域坐标为相对于父容器的左上角顶点。
        //TODO 应考虑TextureView与ScannerFrameView的Margin与padding的情况
        mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
        mCameraScanner.setGraphicDecoder(mZBarDecoder);
    }

    @Override
    public void openCameraError() {
        ToastHelper.showToast("出错了", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void noCameraPermission() {
        ToastHelper.showToast("没有权限", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void cameraDisconnected() {
        ToastHelper.showToast("断开了连接", ToastHelper.LENGTH_SHORT);
    }

    int mCount = 0;
    String mResult = null;

    @Override
    public void decodeSuccess(int type, int quality, String result) {
        if (result.equals(mResult)) {
            if (++mCount > 3) {//连续四次相同，则显示结果（主要防止误读）
                if (quality < 10) {
                    ToastHelper.showToast("[" + type + "/00" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                } else if (quality < 100) {
                    ToastHelper.showToast("[" + type + "/0" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                } else {
                    ToastHelper.showToast("[" + type + "/" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                }
            }
        } else {
            mCount = 1;
            mResult = result;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, getClass().getName() + ".onSurfaceTextureAvailable() width = " + width + " , height = " + height);
        mCameraScanner.setSurfaceTexture(surface);
        mCameraScanner.setPreviewSize(width, height);
        mCameraScanner.openCamera(this.getApplicationContext());
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, getClass().getName() + ".onSurfaceTextureSizeChanged() width = " + width + " , height = " + height);
        // TODO 当View大小发生变化时，要进行调整。
//        mTextureView.setImageFrameMatrix();
//        mCameraScanner.setPreviewSize(width, height);
//        mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, getClass().getName() + ".onSurfaceTextureDestroyed()");
        return true;
    }

    @Override// 每有一帧画面，都会回调一次此方法
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

}
