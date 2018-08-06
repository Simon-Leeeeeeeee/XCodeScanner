package cn.simonlee.demo.xcodescanner;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * 启动页面
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class SplashActivity extends BaseActivity implements Handler.Callback {

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //休眠800毫秒后进入主界面
        mHandler.sendEmptyMessageDelayed(0, 800);
    }

    @Override
    protected void onPause() {
        // 防止启动界面退出后过一会又进入登录页面
        mHandler.removeMessages(0);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler = null;
        super.onDestroy();
    }

    @Override
    public boolean handleMessage(Message msg) {
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return true;
    }

}
