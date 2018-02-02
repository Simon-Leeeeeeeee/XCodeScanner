package com.demo.camera2;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

public class APP extends Application {
    private static APP mAPP;

    @Override
    public void onCreate() {
        super.onCreate();
        mAPP = this;
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
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
