package cn.simonlee.demo.xcodescanner;

import android.app.Application;

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
