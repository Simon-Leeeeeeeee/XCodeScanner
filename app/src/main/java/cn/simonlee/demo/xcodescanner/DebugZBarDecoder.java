package cn.simonlee.demo.xcodescanner;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;

import java.util.LinkedList;

import cn.simonlee.xcodescanner.core.ZBarDecoder;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class DebugZBarDecoder extends ZBarDecoder {

    private final String TAG = "XCodeScanner-Debug";
    private Handler mHandler;
    private ZBarDebugListener mZBarDebugListener;
    private int FPS;
    private long totalTimeExpend;
    private LinkedList<Long> timeExpendList = new LinkedList<>();

    /**
     * 如果要指定扫码格式，请使用含参构造方法.
     */
    public DebugZBarDecoder() {
        this(null);
    }

    /**
     * 如果要指定扫码格式，请使用含参构造方法.
     */
    public DebugZBarDecoder(int[] symbolTypeArray) {
        super(symbolTypeArray);
        this.mHandler = new Handler(this);
        mHandler.sendEmptyMessageDelayed(1991, 1000);
    }

    @Override
    public synchronized void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp) {
        FPS++;
        super.decode(frameData, width, height, rectClipRatio, System.currentTimeMillis());
    }

    @Override
    public void decodeComplete(String result, int type, int quality, int requestCode, long beginTimeStamp) {
        super.decodeComplete(result, type, quality, requestCode, beginTimeStamp);
        if (timeExpendList.size() >= 60) {
            totalTimeExpend -= timeExpendList.removeFirst();
        }
        long timeExpend = System.currentTimeMillis() - beginTimeStamp;
        timeExpendList.add(timeExpend);
        totalTimeExpend += timeExpend;
    }

    public void setZBarDebugListener(ZBarDebugListener listener) {
        this.mZBarDebugListener = listener;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == 1991) {
            mHandler.sendEmptyMessageDelayed(1991, 1000);
            long decodeExpendTime = timeExpendList.size() == 0 ? -1 : (totalTimeExpend / timeExpendList.size());
            mZBarDebugListener.ZBarDebug(FPS, decodeExpendTime);
            FPS = 0;
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

    public interface ZBarDebugListener {
        void ZBarDebug(int fps, long decodeExpendTime);
    }
}
