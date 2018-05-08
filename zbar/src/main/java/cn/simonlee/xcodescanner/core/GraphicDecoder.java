package cn.simonlee.xcodescanner.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-03-14
 */
@SuppressWarnings("unused")
public interface GraphicDecoder {

    void setDecodeListener(DecodeListener listener);

    void stopDecode();

    void startDecode();

    void startDecodeDelay(int delay);

    void decodeForResult(Context context, Uri uri, int requestCode);

    /**
     * 注意：1立即回收bitmap对象会报错 2.解码结束会自动回收该对象
     */
    void decodeForResult(Bitmap bitmap, RectF rectClipRatio, int requestCode);

    void decodeForResult(int[] pixels, int width, int height, RectF rectClipRatio, int requestCode);

    void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp);

    void detach();

    interface DecodeListener {
        void decodeComplete(String result, int type, int quality, int requestCode);
    }

}
