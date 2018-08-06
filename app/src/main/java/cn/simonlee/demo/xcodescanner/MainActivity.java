package cn.simonlee.demo.xcodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import cn.simonlee.xcodescanner.core.GraphicDecoder;
import cn.simonlee.xcodescanner.core.ZBarDecoder;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener, GraphicDecoder.DecodeListener {

    private final int MODE_RELEASE = 0;
    private final int MODE_DEBUG = 1;

    private boolean newAPI = false;
    private boolean constraintLayout = false;

    private GraphicDecoder mGraphicDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_local).setOnClickListener(this);
        findViewById(R.id.btn_scan).setOnClickListener(this);
        findViewById(R.id.btn_scan_debug).setOnClickListener(this);

        findViewById(R.id.btn_jianshu).setOnClickListener(this);
        findViewById(R.id.btn_juejin).setOnClickListener(this);
        findViewById(R.id.btn_github).setOnClickListener(this);

        ((RadioGroup) findViewById(R.id.radiogroup_api)).setOnCheckedChangeListener(this);
        ((RadioGroup) findViewById(R.id.radiogroup_layout)).setOnCheckedChangeListener(this);

        StringBuilder ABIBuilder = new StringBuilder();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            String[] ABIS = Build.SUPPORTED_ABIS;
            for (String ABI : ABIS) {
                ABIBuilder.append(ABI).append("/");
            }
        }
        ((TextView) findViewById(R.id.textview_abi)).setText(ABIBuilder.append(android.os.Build.CPU_ABI));
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.radio_api_old: {
                newAPI = false;
                break;
            }
            case R.id.radio_api_new: {
                newAPI = true;
                break;
            }
            case R.id.radio_layout_relative: {
                constraintLayout = false;
                break;
            }
            case R.id.radio_layout_constraint: {
                constraintLayout = true;
                break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_local: {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 999);
                return;
            }
            case R.id.btn_jianshu: {
                Uri uri = Uri.parse("https://www.jianshu.com/p/65df16604646");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return;
            }
            case R.id.btn_juejin: {
                Uri uri = Uri.parse("https://juejin.im/post/5adf0f166fb9a07ac23a62d1");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return;
            }
            case R.id.btn_github: {
                Uri uri = Uri.parse("https://github.com/Simon-Leeeeeeeee/XCodeScanner");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return;
            }
        }
        int mode = v.getId() == R.id.btn_scan_debug ? MODE_DEBUG : MODE_RELEASE;

        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            startScan(mode);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, mode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 999 && resultCode == Activity.RESULT_OK && data != null) {
            if (mGraphicDecoder == null) {
                mGraphicDecoder = new ZBarDecoder();//使用带参构造方法可指定条码识别的格式
                mGraphicDecoder.setDecodeListener(this);
            }
            mGraphicDecoder.decodeForResult(this, data.getData(), 999);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan(requestCode);
        } else {
            ToastHelper.showToast(this,"请开启相机权限", ToastHelper.LENGTH_SHORT);
        }
    }

    @Override
    public void onDestroy() {
        if (mGraphicDecoder != null) {
            mGraphicDecoder.setDecodeListener(null);
            mGraphicDecoder.detach();
        }
        super.onDestroy();
    }

    private void startScan(int mode) {
        if (newAPI && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            ToastHelper.showToast(this,"新API需要Android5.0及以上", ToastHelper.LENGTH_SHORT);
            return;
        }
        Intent intent = new Intent(this, mode == MODE_DEBUG ? DebugScanActivity.class : ScanActivity.class);
        intent.putExtra("newAPI", newAPI);
        intent.putExtra("constraintLayout", constraintLayout);
        startActivity(intent);
    }

    @Override
    public void decodeComplete(String result, int type, int quality, int requestCode) {
        if (result == null) {
            ToastHelper.showToast(this, "未识别到条码呀", ToastHelper.LENGTH_SHORT);
        } else {
            if (quality < 10) {
                ToastHelper.showToast(this, "[类型" + type + "/精度00" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
            } else if (quality < 100) {
                ToastHelper.showToast(this, "[类型" + type + "/精度0" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
            } else {
                ToastHelper.showToast(this, "[类型" + type + "/精度" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
            }
        }
    }
}
