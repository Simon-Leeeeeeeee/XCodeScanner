package cn.simonlee.demo.xcodescanner;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-09-10
 */
public class APP extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyDeath()
                    .penaltyDialog()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyDeath()
                    .penaltyLog()
                    .build());
        }
    }

    @Override
    public void onTerminate() {
        ToastHelper.cancelToast();
        super.onTerminate();
    }

}
