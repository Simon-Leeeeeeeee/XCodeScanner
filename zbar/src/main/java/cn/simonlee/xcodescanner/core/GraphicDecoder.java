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

    /**
     * 设置解码监听
     */
    void setDecodeListener(DecodeListener listener);

    /**
     * 停止解码，会清空任务队列，并取消延时解码
     */
    void stopDecode();

    /**
     * 开始解码
     */
    void startDecode();

    /**
     * 延迟一段时间后开始解码，单位毫秒
     */
    void startDecodeDelay(int delay);

    /**
     * 传入本地图片的Uri进行解码
     * 注意：会清空任务队列中的所有任务
     */
    void decodeForResult(Context context, Uri uri, int requestCode);

    /**
     * 传入Bitmap对象进行解码
     * 注意：1.会清空任务队列中的所有任务 2.立即回收bitmap对象会报错 3.解码结束会自动回收该对象
     */
    void decodeForResult(Bitmap bitmap, RectF rectClipRatio, int requestCode);

    /**
     * 传入图像的像素数组及图像宽高进行解码
     * 注意：会清空任务队列中的所有任务
     */
    void decodeForResult(int[] pixels, int width, int height, RectF rectClipRatio, int requestCode);

    /**
     * 传入图片的YUV数组及图像宽高进行解码
     */
    void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp);

    void detach();

    interface DecodeListener {
        /**
         * 解码完成后会进行回调，无论是否解码成功
         */
        void decodeComplete(String result, int type, int quality, int requestCode);
    }

}
