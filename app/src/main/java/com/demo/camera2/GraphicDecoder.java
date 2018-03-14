package com.demo.camera2;

import android.graphics.RectF;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.CallSuper;

import java.lang.ref.WeakReference;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

public abstract class GraphicDecoder implements BaseHandler.BaseHandlerListener {

    private final BaseHandler mHandler;
    private WeakReference<DecodeListener> weakReference;//弱引用，防止内存泄漏

    public GraphicDecoder(DecodeListener listener) {
        this.weakReference = new WeakReference<>(listener);
        this.mHandler = new BaseHandler(this, Looper.getMainLooper());
    }

    public abstract void decode(byte[] data, int width, int height, RectF frameRatioRect);

    public final void deliverResult(String result) {
        if (weakReference != null) {
            Message msg = mHandler.obtainMessage();
            msg.obj = result;
            mHandler.sendMessage(msg);
        }
    }

    @CallSuper
    public void detach() {
        mHandler.clear();
        if (weakReference != null) {
            weakReference.clear();
            weakReference = null;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (weakReference != null) {
            DecodeListener listener = weakReference.get();
            if (listener != null) {
                listener.decodeSuccess((String) msg.obj);
            }
        }
    }

    interface DecodeListener {
        void decodeSuccess(String result);
    }

}
