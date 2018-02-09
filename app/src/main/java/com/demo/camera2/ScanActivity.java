package com.demo.camera2;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;

/**
 * @author lixiaomeng
 * @WeChat 13510219066
 */

public class ScanActivity extends AppCompatActivity implements Camera2Scanner.ScannerListener, TextureView.SurfaceTextureListener {

    private AutoFitTextureView mPreview;

    private Camera2Scanner mCameraScanner;

    private final String TAG = "Camera2";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        mPreview = findViewById(R.id.camera_preview);
        mCameraScanner = new Camera2Scanner(this);
        mPreview.setSurfaceTextureListener(this);
    }

    @Override
    public void onDestroy() {
        mCameraScanner.detach();
        super.onDestroy();
    }

    @Override
    public void openCameraSuccess(int width, int height) {//宽高
        mPreview.setAspectRatio(width, height);
    }

    @Override
    public void openCameraError() {
        ToastHelper.showToast("出错了", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void scanSuccess(String result) {
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
        mCameraScanner.openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, getClass().getName() + ".onSurfaceTextureSizeChanged()");
        mCameraScanner.setSurfaceHolder(surface);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, getClass().getName() + ".onSurfaceTextureDestroyed()");
        return false;
    }

    @Override// 这个方法要注意一下，因为每有一帧画面，都会回调一次此方法
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

}
