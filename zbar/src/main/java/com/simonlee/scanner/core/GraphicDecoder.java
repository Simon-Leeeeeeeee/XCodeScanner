package com.simonlee.scanner.core;

import android.graphics.RectF;
import android.support.annotation.CallSuper;

import java.lang.ref.WeakReference;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public interface GraphicDecoder {

    void decode(byte[] frameData, int width, int height, RectF rectClipRatio);

    void detach();

    interface DecodeListener {
        void decodeSuccess(int type, int quality, String result);
    }

}
