package cn.simonlee.xcodescanner.core;

import android.graphics.RectF;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public interface GraphicDecoder {

    void setDecodeListener(DecodeListener listener);

    void decode(byte[] frameData, int width, int height, RectF rectClipRatio);

    void detach();

    interface DecodeListener {
        void decodeSuccess(int type, int quality, String result);
    }

}
