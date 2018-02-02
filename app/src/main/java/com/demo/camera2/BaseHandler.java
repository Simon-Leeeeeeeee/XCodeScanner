package com.demo.camera2;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * Created by LiXiaomeng on 2017-09-14.
 */
public class BaseHandler extends Handler {

    private WeakReference<BaseHandlerListener> weakReference;

    public BaseHandler(BaseHandlerListener listener) {
        this.weakReference = new WeakReference<>(listener);
    }

    public BaseHandler(BaseHandlerListener listener, Looper looper) {
        super(looper);
        this.weakReference = new WeakReference<>(listener);
    }

    @Override
    public void handleMessage(Message msg) {
        if (this.weakReference.get() != null) this.weakReference.get().handleMessage(msg);
    }

    public void removeAll() {
        removeCallbacksAndMessages(null);
    }

    public void clear() {
        removeAll();
        if (weakReference != null) {
            weakReference.clear();
            weakReference = null;
        }
    }

    public interface BaseHandlerListener {
        void handleMessage(Message msg);
    }
}
