package com.demo.camera2;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;

import com.simonlee.scanner.core.Camera2Scanner;
import com.simonlee.scanner.core.GraphicDecoder;
import com.simonlee.scanner.core.ZBarDecoder;
import com.simonlee.scanner.view.AutoFitTextureView;
import com.simonlee.scanner.view.ScannerFrameView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public class ScanActivity extends AppCompatActivity implements Camera2Scanner.CameraDeviceListener, TextureView.SurfaceTextureListener, GraphicDecoder.DecodeListener {

    private AutoFitTextureView mTextureView;
    private ScannerFrameView mScannerFrameView;

    private Camera2Scanner mCameraScanner;
    private ZBarDecoder mZBarDecoder;

    private final String TAG = "Camera2Scanner";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra("type", R.id.btn_scan_relative);
        setContentView(id == R.id.btn_scan_constraint ? R.layout.activity_scan_constraint : R.layout.activity_scan_relative);

        mScannerFrameView = findViewById(R.id.scannerframe);
        mTextureView = findViewById(R.id.textureview);

        mCameraScanner = new Camera2Scanner(this);
        mCameraScanner.setCameraDeviceListener(this);
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    protected void onRestart() {
        mCameraScanner.openCamera();
        super.onRestart();
    }

    @Override
    protected void onStop() {
        mCameraScanner.closeCamera();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mCameraScanner.setGraphicDecoder(null);
        if (mZBarDecoder != null) {
            mZBarDecoder.detach();
        }
        mCameraScanner.detach();
        super.onDestroy();
    }

    @Override
    public void openCameraSuccess(int surfaceWidth, int surfaceHeight, int orientation) {
        Log.d(TAG, getClass().getName() + ".openCameraSuccess()");
        mTextureView.setAspectRatio(surfaceWidth, surfaceHeight, orientation);
        if (mZBarDecoder == null) {
            mZBarDecoder = new ZBarDecoder(this);
        }
        mCameraScanner.setGraphicDecoder(mZBarDecoder);
    }

    @Override
    public void openCameraError() {
        ToastHelper.showToast("出错了", ToastHelper.LENGTH_SHORT);
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
    public void noCameraPermission() {
        ToastHelper.showToast("没有权限", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void cameraDisconnected() {
        ToastHelper.showToast("断开了连接", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, getClass().getName() + ".onSurfaceTextureAvailable() width = " + width + " , height = " + height);
        mCameraScanner.setSurfaceHolder(surface);
        mCameraScanner.setPreviewSize(width, height);
        mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
        mCameraScanner.openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, getClass().getName() + ".onSurfaceTextureSizeChanged() width = " + width + " , height = " + height);
        mCameraScanner.setSurfaceHolder(surface);
        mCameraScanner.setPreviewSize(width, height);
        mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override// 每有一帧画面，都会回调一次此方法
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

}
