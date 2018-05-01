package cn.simonlee.xcodescanner.core;

import android.graphics.RectF;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */
public interface GraphicDecoder {

    void setDecodeListener(DecodeListener listener);

    void stopDecode();

    void startDecode();

    void startDecodeDelay(int delay);

    void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp);

    void detach();

    interface DecodeListener {
        void decodeSuccess(int type, int quality, String result);
    }

}
