package com.demo.camera2;

import android.graphics.RectF;
import android.support.annotation.CallSuper;

import java.lang.ref.WeakReference;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

public abstract class GraphicDecoder {

    private final WeakReference<DecodeListener> mWeakReference;//弱引用，防止内存泄漏

    public GraphicDecoder(DecodeListener listener) {
        this.mWeakReference = new WeakReference<>(listener);
    }

    public abstract void decode(byte[] data, int width, int height, RectF frameRatioRect);

    public final void deliverResult(int type, int quality, String result) {
        DecodeListener listener = mWeakReference.get();
        if (listener != null) {
            listener.decodeSuccess(type, quality, result);
        }
    }

    @CallSuper
    public void detach() {
        mWeakReference.clear();
    }

    interface DecodeListener {
        void decodeSuccess(int type, int quality, String result);
    }

}
