package cn.simonlee.demo.xcodescanner;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import cn.simonlee.xcodescanner.core.CameraScanner;
import cn.simonlee.xcodescanner.core.GraphicDecoder;
import cn.simonlee.xcodescanner.core.NewCameraScanner;
import cn.simonlee.xcodescanner.core.OldCameraScanner;
import cn.simonlee.xcodescanner.core.ZBarDecoder;
import cn.simonlee.xcodescanner.view.AdjustTextureView;
import cn.simonlee.xcodescanner.view.ScannerFrameView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class ScanActivity extends BaseActivity implements CameraScanner.CameraListener, TextureView.SurfaceTextureListener, GraphicDecoder.DecodeListener, View.OnClickListener {

    private AdjustTextureView mTextureView;
    private ScannerFrameView mScannerFrameView;

    private CameraScanner mCameraScanner;
    protected GraphicDecoder mGraphicDecoder;

    protected String TAG = "XCodeScanner";
    private Button mButton_Flash;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, getClass().getName() + ".onCreate()");
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        boolean newAPI = intent.getBooleanExtra("newAPI", false);
        boolean constraintLayout = intent.getBooleanExtra("constraintLayout", false);

        setContentView(constraintLayout ? R.layout.activity_scan_constraint : R.layout.activity_scan_relative);

        Toolbar toolbar = getToolbar();
        if (toolbar != null) {
            toolbar.setTitle(R.string.scan);
            toolbar.setNavigationOnClickListener(this);
        }

        mTextureView = findViewById(R.id.textureview);
        mScannerFrameView = findViewById(R.id.scannerframe);
        mButton_Flash = findViewById(R.id.btn_flash);
        mButton_Flash.setOnClickListener(this);

        /*
        * 注意，SDK21的设备是可以使用NewCameraScanner的，但是可能存在对新API支持不够的情况，比如红米Note3（双网通Android5.0.2）
        * 开发者可自行配置使用规则，比如针对某设备型号过滤，或者针对某SDK版本过滤
        * */
        if (newAPI && Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            mCameraScanner = new NewCameraScanner(this);
        } else {
            mCameraScanner = new OldCameraScanner(this);
        }

        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, getClass().getName() + ".onRestart()");
        if (mTextureView.isAvailable()) {
            //部分机型转到后台不会走onSurfaceTextureDestroyed()，因此isAvailable()一直为true，转到前台后不会再调用onSurfaceTextureAvailable()
            //因此需要手动开启相机
            mCameraScanner.setPreviewTexture(mTextureView.getSurfaceTexture());
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
        if (mGraphicDecoder != null) {
            mGraphicDecoder.setDecodeListener(null);
            mGraphicDecoder.detach();
        }
        mCameraScanner.detach();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, getClass().getName() + ".onSurfaceTextureAvailable() width = " + width + " , height = " + height);
        mCameraScanner.setPreviewTexture(surface);
        mCameraScanner.setPreviewSize(width, height);
        mCameraScanner.openCamera(this);
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

    @Override
    public void openCameraSuccess(int frameWidth, int frameHeight, int frameDegree) {
        Log.e(TAG, getClass().getName() + ".openCameraSuccess() frameWidth = " + frameWidth + " , frameHeight = " + frameHeight + " , frameDegree = " + frameDegree);
        mTextureView.setImageFrameMatrix(frameWidth, frameHeight, frameDegree);
        if (mGraphicDecoder == null) {
            mGraphicDecoder = new ZBarDecoder();//使用带参构造方法可指定条码识别的格式
            mGraphicDecoder.setDecodeListener(this);
        }
        //该区域坐标为相对于父容器的左上角顶点。
        //TODO 应考虑TextureView与ScannerFrameView的Margin与padding的情况
        mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
        mCameraScanner.setGraphicDecoder(mGraphicDecoder);
    }

    @Override
    public void openCameraError() {
        ToastHelper.showToast(this, "出错了", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void noCameraPermission() {
        ToastHelper.showToast(this, "没有权限", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void cameraDisconnected() {
        ToastHelper.showToast(this, "断开了连接", ToastHelper.LENGTH_SHORT);
    }

    @Override
    public void cameraBrightnessChanged(int brightness) {
        if (brightness <= 40) {
            mButton_Flash.setVisibility(View.VISIBLE);
        } else if (!mCameraScanner.isFlashOpened()) {
            mButton_Flash.setVisibility(View.GONE);
        }
        Log.e(TAG, getClass().getName() + ".cameraBrightnessChanged() brightness = " + brightness);
    }

    int mCount = 0;
    String mResult = null;

    @Override
    public void decodeComplete(String result, int type, int quality, int requestCode) {
        if (result == null) return;
        if (result.equals(mResult)) {
            if (++mCount > 3) {//连续四次相同则显示结果（主要过滤脏数据，也可以根据条码类型自定义规则）
                if (quality < 10) {
                    ToastHelper.showToast(this, "[类型" + type + "/精度00" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                } else if (quality < 100) {
                    ToastHelper.showToast(this, "[类型" + type + "/精度0" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                } else {
                    ToastHelper.showToast(this, "[类型" + type + "/精度" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
                }
            }
        } else {
            mCount = 1;
            mResult = result;
        }
        Log.d(TAG, getClass().getName() + ".decodeComplete() -> " + mResult);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.base_toolbar_navigation: {
                onBackPressed();
                break;
            }
            case R.id.btn_flash: {
                if (v.isSelected()) {
                    ((Button) v).setText(R.string.flash_open);
                    v.setSelected(false);
                    mCameraScanner.closeFlash();
                } else {
                    ((Button) v).setText(R.string.flash_close);
                    v.setSelected(true);
                    mCameraScanner.openFlash();
                }
                break;
            }
        }
    }

}
