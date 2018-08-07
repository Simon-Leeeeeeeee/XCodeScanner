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
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import cn.simonlee.xcodescanner.core.GraphicDecoder;
import cn.simonlee.xcodescanner.core.ZBarDecoder;

/**
 * 主界面
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class MainActivity extends BaseActivity implements View.OnClickListener, GraphicDecoder.DecodeListener {

    private final int API_OLD = 0;
    private final int API_NEW = 1;

    private GraphicDecoder mGraphicDecoder;
    private List<RadioButton> mTypeRadioList = new ArrayList<>();
    private int[] mCodeTypeArray;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = getToolbar();
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
        }

        findViewById(R.id.btn_local).setOnClickListener(this);
        findViewById(R.id.btn_scan_oldapi).setOnClickListener(this);
        findViewById(R.id.btn_scan_newapi).setOnClickListener(this);

        findViewById(R.id.btn_jianshu).setOnClickListener(this);
        findViewById(R.id.btn_juejin).setOnClickListener(this);
        findViewById(R.id.btn_github).setOnClickListener(this);

        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_codabar));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_code39));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_code93));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_code128));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_databar));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_databar_exp));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_ean8));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_ean13));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_i25));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_isbn10));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_isbn13));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_pdf417));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_qrcode));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_upca));
        mTypeRadioList.add((RadioButton) findViewById(R.id.main_radio_codetype_upce));

        mCodeTypeArray = new int[]{ZBarDecoder.CODABAR, ZBarDecoder.CODE39, ZBarDecoder.CODE93, ZBarDecoder.CODE128, ZBarDecoder.DATABAR, ZBarDecoder.DATABAR_EXP
                , ZBarDecoder.EAN8, ZBarDecoder.EAN13, ZBarDecoder.I25, ZBarDecoder.ISBN10, ZBarDecoder.ISBN13, ZBarDecoder.PDF417, ZBarDecoder.QRCODE
                , ZBarDecoder.UPCA, ZBarDecoder.UPCE};
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_scan_oldapi: {
                startScan(API_OLD);
                break;
            }
            case R.id.btn_scan_newapi: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    ToastHelper.showToast(this, "新API需要Android5.0及以上", ToastHelper.LENGTH_SHORT);
                } else {
                    startScan(API_NEW);
                }
                break;
            }
            case R.id.btn_local: {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 999);
                break;
            }
            case R.id.btn_jianshu: {
                Uri uri = Uri.parse("https://www.jianshu.com/p/65df16604646");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
            case R.id.btn_juejin: {
                Uri uri = Uri.parse("https://juejin.im/post/5adf0f166fb9a07ac23a62d1");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
            case R.id.btn_github: {
                Uri uri = Uri.parse("https://github.com/Simon-Leeeeeeeee/XCodeScanner");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
        }
    }

    private void startScan(int api) {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra("newAPI", api == API_NEW);
            intent.putExtra("codeType", getCodeType());
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, api);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 999 && resultCode == Activity.RESULT_OK && data != null) {
            if (mGraphicDecoder == null) {
                mGraphicDecoder = new ZBarDecoder(this, getCodeType());//使用带参构造方法可指定条码识别的类型
            } else {
                mGraphicDecoder.setCodeTypes(getCodeType());//指定条码识别的类型
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
            ToastHelper.showToast(this, "请开启相机权限", ToastHelper.LENGTH_SHORT);
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

    @Override
    public void decodeComplete(String result, int type, int quality, int requestCode) {
        if (result == null) {
            ToastHelper.showToast(this, "未识别到指定类型条码", ToastHelper.LENGTH_SHORT);
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

    private int[] getCodeType() {
        int count = 0;
        for (RadioButton radioButton : mTypeRadioList) {
            if (radioButton.isChecked()) {
                count++;
            }
        }
        int[] typeArray = new int[count];
        for (int index = 0; index < mTypeRadioList.size(); index++) {
            if (mTypeRadioList.get(index).isChecked()) {
                typeArray[--count] = mCodeTypeArray[index];
            }
        }
        return typeArray;
    }

}
