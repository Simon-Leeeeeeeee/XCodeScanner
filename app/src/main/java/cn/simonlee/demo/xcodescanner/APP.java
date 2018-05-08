package cn.simonlee.demo.xcodescanner;

import android.app.Application;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class APP extends Application {
    private static APP mAPP;

    @Override
    public void onCreate() {
        super.onCreate();
        mAPP = this;
    }

    @Override
    public void onTerminate() {
        ToastHelper.cancelToast();
        super.onTerminate();
    }

    public static APP getApp() {
        return mAPP;
    }

}
