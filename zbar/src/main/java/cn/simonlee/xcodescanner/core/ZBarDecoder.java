package cn.simonlee.xcodescanner.core;

import android.graphics.RectF;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * 存在的问题：
 * 1.像素太高时会导致二维码无法识别，限制为1920*1080暂无问题
 * 2.条码误读为DataBar(RSS-14)格式，此格式不常见，屏蔽即可
 * 3.条码误读为UPC-E格式，此格式常用性一般，按需求决定是否开放，并结合精度进行判断
 * 4.EAN-13格式的条码部分情况下识别出现错误，表现在a.解析成其他格式 b.解析出错误条码，如6920586221399，与算法及分辨率有关，与条码图像无关
 */
@SuppressWarnings("unused")
public class ZBarDecoder implements GraphicDecoder, BaseHandler.BaseHandlerListener {

    /**
     * Symbol detected but not decoded.
     */
    public static final int PARTIAL = 1;
    /**
     * EAN-8.
     */
    public static final int EAN8 = 8;
    /**
     * UPC-E.
     */
    public static final int UPCE = 9;
    /**
     * ISBN-10 (from EAN-13).
     */
    public static final int ISBN10 = 10;
    /**
     * UPC-A.
     */
    public static final int UPCA = 12;
    /**
     * EAN-13.
     */
    public static final int EAN13 = 13;
    /**
     * ISBN-13 (from EAN-13).
     */
    public static final int ISBN13 = 14;
    /**
     * Interleaved 2 of 5.
     */
    public static final int I25 = 25;
    /**
     * DataBar (RSS-14).
     */
    public static final int DATABAR = 34;
    /**
     * DataBar Expanded.
     */
    public static final int DATABAR_EXP = 35;
    /**
     * Codabar.
     */
    public static final int CODABAR = 38;
    /**
     * Code 39.
     */
    public static final int CODE39 = 39;
    /**
     * PDF417.
     */
    public static final int PDF417 = 57;
    /**
     * QR Code.
     */
    public static final int QRCODE = 64;
    /**
     * Code 93.
     */
    public static final int CODE93 = 93;
    /**
     * Code 128.
     */
    public static final int CODE128 = 128;

    private Image mZBarImage;
    private ImageScanner mImageScanner;

    private final String TAG = "XCodeScanner";

    private final Object decodeLock = new Object();//互斥锁

    private BaseHandler mCurThreadHandler;

    private DecodeListener mDecodeListener;

    private ThreadPoolExecutor mExecutorService;
    private ArrayBlockingQueue<Runnable> mArrayBlockingQueue;

    private final int HANDLER_DECODE_DELAY = 60001;
    private final int HANDLER_DECODE_SUCCESS = 60002;

    private volatile boolean isDecodeEnabled;//解码开关，默认为true

    /**
     * 如果要指定扫码格式，请使用含参构造方法.
     */
    public ZBarDecoder() {
        this(null);
    }

    /**
     * 指定扫码格式进行识别，支持的格式EAN8、ISBN10、UPCA、EAN13、ISBN13、I25、UPCE、DATABAR
     * 、DATABAR_EXP、CODABAR、CODE39、PDF417、QRCODE、CODE93、CODE128，可根据实际需要进行配置。
     */
    public ZBarDecoder(int[] symbolTypeArray) {
        this.mCurThreadHandler = new BaseHandler(this);
        startDecode();
        initZBar(symbolTypeArray);
        mArrayBlockingQueue = new ArrayBlockingQueue<>(1);
        mExecutorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, mArrayBlockingQueue);
    }

    /**
     * 初始化ImageScanner&Image
     */
    private void initZBar(int[] symbolTypeArray) {
        mImageScanner = new ImageScanner();
        mImageScanner.setConfig(0, Config.X_DENSITY, 3);
        mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
        mImageScanner.setConfig(0, Config.ENABLE, 0);//Disable all the Symbols
        if (symbolTypeArray == null) {
            symbolTypeArray = new int[]{EAN8, ISBN10, UPCA, EAN13, ISBN13, I25//, PARTIAL, UPCE, DATABAR
                    , DATABAR_EXP, CODABAR, CODE39, PDF417, QRCODE, CODE93, CODE128};
        }
        for (int symbolType : symbolTypeArray) {
            mImageScanner.setConfig(symbolType, Config.ENABLE, 1);//Only symbolType is enable
        }
        mZBarImage = new Image("Y800");
    }

    @Override
    public void setDecodeListener(DecodeListener listener) {
        this.mDecodeListener = listener;
    }

    @Override
    public void stopDecode() {
        this.isDecodeEnabled = false;
    }

    @Override
    public void startDecode() {
        this.isDecodeEnabled = true;
    }

    @Override
    public void startDecodeDelay(int delay) {
        if (mCurThreadHandler != null) {
            mCurThreadHandler.sendMessageDelayed(mCurThreadHandler.obtainMessage(HANDLER_DECODE_DELAY), delay);
        }
    }

    @Override
    public synchronized void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp) {
        if (isDecodeEnabled && mExecutorService != null && mArrayBlockingQueue != null && mArrayBlockingQueue.size() < 1) {
            mExecutorService.execute(new DecodeRunnable(frameData, width, height, rectClipRatio, timeStamp));
        }
    }

    @Override
    public void detach() {
        Log.d(TAG, getClass().getName() + ".detach()");
        synchronized (ZBarDecoder.this) {
            if (mExecutorService != null) {
                mExecutorService.shutdownNow();
                mExecutorService = null;
            }
            if (mArrayBlockingQueue != null) {
                mArrayBlockingQueue.clear();
                mArrayBlockingQueue = null;
            }
        }
        synchronized (decodeLock) {
            if (mCurThreadHandler != null) {
                mCurThreadHandler.clear();
                mCurThreadHandler = null;
            }
            if (mZBarImage != null) {
                mZBarImage.destroy();
                mZBarImage = null;
            }
            if (mImageScanner != null) {
                mImageScanner.destroy();
                mImageScanner = null;
            }
        }
    }

    /**
     * 使用zbar解析图像，返回一个Symbol集合
     *
     * @param frameData     图像的byte数组
     * @param width         图像的宽
     * @param height        图像的高
     * @param rectClipRatio 图像区域的剪裁比例
     */
    private SymbolSet decodeImage(byte[] frameData, int width, int height, RectF rectClipRatio) {
        if (mZBarImage == null || mImageScanner == null || frameData == null) return null;
        mZBarImage.setSize(width, height);
        if (rectClipRatio != null && !rectClipRatio.isEmpty()) {
            int frameLeft = (int) (rectClipRatio.left * width);
            int frameTop = (int) (rectClipRatio.top * height);
            int frameWidth = (int) (rectClipRatio.width() * width);
            int frameHeight = (int) (rectClipRatio.height() * height);
            mZBarImage.setCrop(frameLeft, frameTop, frameWidth, frameHeight);
        }
        mZBarImage.setData(frameData);
        if (mImageScanner.scanImage(mZBarImage) != 0) {
            return mImageScanner.getResults();
        }
        return null;
    }

    /**
     * 从Symbol集合中获取结果
     */
    private void takeResult(SymbolSet symbolSet, long beginTimeStamp) {
        if (symbolSet != null && mCurThreadHandler != null) {
            for (Symbol symbol : symbolSet) {
                String result = symbol.getData();
                if (result != null && result.length() > 0) {
//                  int count = symbol.getCount();
//                  int[] bounds = symbol.getBounds();
//                  byte[] dataBytes = symbol.getDataBytes();
                    int type = symbol.getType();
                    int quality = symbol.getQuality();
                    decodeSuccess(result, type, quality, beginTimeStamp);
                    return;
                }
            }
        }
        decodeSuccess(null, 0, 0, beginTimeStamp);
    }

    public void decodeSuccess(String result, int type, int quality, long beginTimeStamp) {
        if (result != null && mCurThreadHandler != null) {
            Log.d(TAG, getClass().getName() + ".decodeSuccess() result = "+result+" , type = "+type+" , quality = "+quality);
            mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(HANDLER_DECODE_SUCCESS, type, quality, result));
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HANDLER_DECODE_DELAY: {//开启解码
                startDecode();
                break;
            }
            case HANDLER_DECODE_SUCCESS: {//解码成功
                if (mDecodeListener != null && isDecodeEnabled) {
                    mDecodeListener.decodeSuccess(msg.arg1, msg.arg2, (String) msg.obj);
                }
                break;
            }
        }
    }

    private class DecodeRunnable implements Runnable {

        private final byte[] mFrameData;
        private final int mWidth;
        private final int mHeight;
        private final RectF mRectClipRatio;
        private final long mBeginTimeStamp;

        DecodeRunnable(byte[] frameData, int width, int height, RectF rectClipRatio, long beginTimeStamp) {
            this.mFrameData = frameData;
            this.mWidth = width;
            this.mHeight = height;
            this.mRectClipRatio = rectClipRatio;
            this.mBeginTimeStamp = beginTimeStamp;
        }

        @Override
        public void run() {
            synchronized (decodeLock) {
                //1.解析图像
                SymbolSet symbolSet = decodeImage(mFrameData, mWidth, mHeight, mRectClipRatio);
                //2.分析结果
                takeResult(symbolSet, mBeginTimeStamp);
            }
        }
    }
}
