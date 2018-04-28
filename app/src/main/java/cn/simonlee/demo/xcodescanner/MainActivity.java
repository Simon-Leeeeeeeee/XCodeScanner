package cn.simonlee.demo.xcodescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final int MODE_RO = 0;
    private final int MODE_RN = 1;
    private final int MODE_CO = 2;
    private final int MODE_CN = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_scan_relative_old).setOnClickListener(this);
        findViewById(R.id.btn_scan_relative_new).setOnClickListener(this);
        findViewById(R.id.btn_scan_constraint_old).setOnClickListener(this);
        findViewById(R.id.btn_scan_constraint_new).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int mode = 0;
        switch (v.getId()) {
            case R.id.btn_scan_relative_old: {
                mode = MODE_RO;
                break;
            }
            case R.id.btn_scan_relative_new: {
                mode = MODE_RN;
                break;
            }
            case R.id.btn_scan_constraint_old: {
                mode = MODE_CO;
                break;
            }
            case R.id.btn_scan_constraint_new: {
                mode = MODE_CN;
                break;
            }
        }
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            startScan(mode);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, mode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan(requestCode);
        } else {
            ToastHelper.showToast("请开启相机权限", ToastHelper.LENGTH_SHORT);
        }
    }

    private void startScan(int mode) {
        if (mode == MODE_RN || mode == MODE_CN) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ToastHelper.showToast("新API需要Android5.0及以上", ToastHelper.LENGTH_SHORT);
                return;
            }
        }
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

}
