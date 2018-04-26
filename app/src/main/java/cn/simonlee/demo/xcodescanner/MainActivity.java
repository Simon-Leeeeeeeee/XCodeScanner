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
import android.widget.Button;

import cn.simonlee.xcodescanner.core.OldCameraScanner;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

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
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            startScan(v.getId());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, v.getId());
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

    private void startScan(int buttonId) {
        int api = 0;//0为旧API  1为新API
        int layout = 0;//0为RelativeLayout  1为ConstraintLayout
        switch (buttonId) {
            case R.id.btn_scan_relative_old: {
                break;
            }
            case R.id.btn_scan_constraint_old: {
                layout = 1;
                break;
            }
            case R.id.btn_scan_relative_new: {
                api = 1;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    ToastHelper.showToast("新API需要Android5.0及以上", ToastHelper.LENGTH_SHORT);
                    return;
                }
                break;
            }
            case R.id.btn_scan_constraint_new: {
                api = 1;
                layout = 1;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    ToastHelper.showToast("新API需要Android5.0及以上", ToastHelper.LENGTH_SHORT);
                    return;
                }
                break;
            }
        }
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra("layout", layout);
        intent.putExtra("api", api);
        startActivity(intent);
    }

}
