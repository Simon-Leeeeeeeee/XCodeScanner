package cn.simonlee.demo.xcodescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button1 = findViewById(R.id.btn_scan_relative);
        Button button2 = findViewById(R.id.btn_scan_constraint);
        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra("type", v.getId());
            startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 666);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 666) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
            } else {
                ToastHelper.showToast("请开启相机权限", ToastHelper.LENGTH_SHORT);
            }
        }
    }

}
