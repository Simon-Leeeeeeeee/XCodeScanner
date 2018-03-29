package com.demo.camera2;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

public class ScanActivity extends AppCompatActivity implements Camera2Scanner.CameraDeviceListener, TextureView.SurfaceTextureListener, GraphicDecoder.DecodeListener {

    private AutoFitTextureView mPreview;
    private ScannerFrameLayout mScannerFrameLayout;
    private ScannerFrameView mScannerFrameView;
    private Camera2Scanner mCameraScanner;

    private ZBarDecoder mZBarDecoder;

    private final String TAG = "Camera2Scanner";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mScannerFrameLayout = findViewById(R.id.layout_scannerframe);
        mScannerFrameView = findViewById(R.id.scannerframe);
        mPreview = findViewById(R.id.camera_preview);

        mCameraScanner = new Camera2Scanner(this);
        mCameraScanner.setCameraDeviceListener(this);
        mPreview.setSurfaceTextureListener(this);
    }

    @Override
    public void onBackPressed() {
        mCameraScanner.setGraphicDecoder(null);
        mCameraScanner.closeCamera();
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        if (mZBarDecoder != null) {
            mZBarDecoder.detach();
        }
        mCameraScanner.detach();
        super.onDestroy();
    }

    @Override
    public void openCameraSuccess(int surfaceWidth, int surfaceHeight, int orientation) {
        mPreview.setAspectRatio(surfaceWidth, surfaceHeight, orientation);
        mScannerFrameLayout.setBackground(null);//去掉背景，防止过度绘制。
        if (mZBarDecoder == null) {
            mZBarDecoder = new ZBarDecoder(this);
        }
        mCameraScanner.setGraphicDecoder(mZBarDecoder);
    }

    @Override
    public void openCameraError() {
        ToastHelper.showToast("出错了", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void decodeSuccess(int type, int quality, String result) {
        ToastHelper.showToast(result, ToastHelper.LENGTH_SHORT);
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
        mCameraScanner.setSurfaceHolder(surface);
        mCameraScanner.setPreviewSize(width, height);
        mCameraScanner.openCamera();
        int left = mScannerFrameView.getLeft();
        int top = mScannerFrameView.getTop();
        int right = mScannerFrameView.getRight();
        int bottom = mScannerFrameView.getBottom();
        mCameraScanner.setFrameRatioRect(left, top, right, bottom);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, getClass().getName() + ".onSurfaceTextureSizeChanged() width = " + width + " , height = " + height);
        mCameraScanner.setSurfaceHolder(surface);
        mCameraScanner.setPreviewSize(width, height);
        int left = mScannerFrameView.getLeft();
        int top = mScannerFrameView.getTop();
        int right = mScannerFrameView.getRight();
        int bottom = mScannerFrameView.getBottom();
        mCameraScanner.setFrameRatioRect(left, top, right, bottom);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override// 每有一帧画面，都会回调一次此方法
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

}
