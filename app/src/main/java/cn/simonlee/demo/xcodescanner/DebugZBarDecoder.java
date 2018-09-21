package cn.simonlee.demo.xcodescanner;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import cn.simonlee.xcodescanner.core.ZBarDecoder;

/**
 * debug模式，只是加入了一个FPS的Log
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class DebugZBarDecoder extends ZBarDecoder {

    private Handler mHandler;
    private int FPS_Preview;
    private int FPS_Decode;

    public DebugZBarDecoder(DecodeListener listener, int[] symbolTypeArray) {
        super(listener, symbolTypeArray);
        this.mHandler = new Handler(this);
        mHandler.sendEmptyMessageDelayed(1991, 1000);
    }

    @Override
    public synchronized void decode(byte[] frameData, int width, int height, RectF clipRectRatio) {
        FPS_Preview++;
        super.decode(frameData, width, height, clipRectRatio);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_DECODE_COMPLETE: {
                FPS_Decode++;
                break;
            }
            case 1991: {
                Log.d(TAG, getClass().getName() + ".handleMessage() 预览FPS：" + FPS_Preview + " , 解码FPS：" + FPS_Decode);
                FPS_Preview = FPS_Decode = 0;
                mHandler.sendEmptyMessageDelayed(1991, 1000);
                break;
            }
        }
        return super.handleMessage(msg);
    }

    @Override
    public void detach() {
        super.detach();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

}
