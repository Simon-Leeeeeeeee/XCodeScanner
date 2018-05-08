package cn.simonlee.xcodescanner.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-03-14
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
    private final int HANDLER_DECODE_COMPLETE = 60002;

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
        mArrayBlockingQueue = new ArrayBlockingQueue<>(5);//等待队列最多插入5条任务
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
        if (mArrayBlockingQueue != null) {
            mArrayBlockingQueue.clear();
        }
        if (mCurThreadHandler != null) {
            mCurThreadHandler.removeMessages(HANDLER_DECODE_DELAY);
        }
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
    public synchronized void decodeForResult(Context context, Uri uri, int requestCode) {
        if (isDecodeEnabled && mExecutorService != null && mArrayBlockingQueue != null) {
            mArrayBlockingQueue.clear();
            mExecutorService.execute(new DecodeRunnable(context.getApplicationContext(), uri, requestCode, System.currentTimeMillis()));
        }
    }

    @Override
    public synchronized void decodeForResult(Bitmap bitmap, RectF rectClipRatio, int requestCode) {
        if (isDecodeEnabled && mExecutorService != null && mArrayBlockingQueue != null) {
            mArrayBlockingQueue.clear();
            mExecutorService.execute(new DecodeRunnable(bitmap, rectClipRatio, requestCode, System.currentTimeMillis()));
        }
    }

    @Override
    public synchronized void decodeForResult(int[] pixels, int width, int height, RectF rectClipRatio, int requestCode) {
        if (isDecodeEnabled && mExecutorService != null && mArrayBlockingQueue != null) {
            mArrayBlockingQueue.clear();
            mExecutorService.execute(new DecodeRunnable(pixels, width, height, rectClipRatio, requestCode, System.currentTimeMillis()));
        }
    }

    @Override
    public synchronized void decode(byte[] frameData, int width, int height, RectF rectClipRatio, long timeStamp) {
        if (isDecodeEnabled && mExecutorService != null && mArrayBlockingQueue != null && mArrayBlockingQueue.size() < 1) {//等待队列只能有一条任务
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
    private void takeResult(SymbolSet symbolSet, int requestCode, long beginTimeStamp) {
        if (symbolSet != null && mCurThreadHandler != null) {
            for (Symbol symbol : symbolSet) {
                String result = symbol.getData();
                if (result != null && result.length() > 0) {
//                  int count = symbol.getCount();
//                  int[] bounds = symbol.getBounds();
//                  byte[] dataBytes = symbol.getDataBytes();
                    int type = symbol.getType();
                    int quality = symbol.getQuality();
                    decodeComplete(result, type, quality, requestCode, beginTimeStamp);
                    return;
                }
            }
        }
        decodeComplete(null, 0, 0, requestCode, beginTimeStamp);
    }

    public void decodeComplete(String result, int type, int quality, int requestCode, long beginTimeStamp) {
        if (mCurThreadHandler != null) {
            Message message = mCurThreadHandler.obtainMessage(HANDLER_DECODE_COMPLETE);
            Bundle bundle = message.getData();
            bundle.putString("result", result);
            bundle.putInt("type", type);
            bundle.putInt("quality", quality);
            bundle.putInt("requestCode", requestCode);
            message.setData(bundle);
            mCurThreadHandler.sendMessage(message);
        }
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case HANDLER_DECODE_DELAY: {//开启解码
                startDecode();
                break;
            }
            case HANDLER_DECODE_COMPLETE: {//解码成功
                if (mDecodeListener != null && isDecodeEnabled) {
                    Bundle bundle = message.peekData();
                    if (bundle != null) {
                        mDecodeListener.decodeComplete(bundle.getString("result"), bundle.getInt("type"),
                                bundle.getInt("quality"), bundle.getInt("requestCode"));
                    }
                }
                break;
            }
        }
    }

    private class DecodeRunnable implements Runnable {

        private Uri mUri;
        private Context mContext;
        private int mRequestCode;

        private Bitmap mBitmap;
        private int[] mPixels;
        private byte[] mYUVFrameData;

        private int mWidth;
        private int mHeight;
        private RectF mRectClipRatio;
        private long mBeginTimeStamp;

        DecodeRunnable(Context context, Uri uri, int requestCode, long beginTimeStamp) {
            this.mRequestCode = requestCode;
            this.mContext = context;
            this.mUri = uri;
            this.mBeginTimeStamp = beginTimeStamp;
        }

        DecodeRunnable(Bitmap bitmap, RectF rectClipRatio, int requestCode, long beginTimeStamp) {
            this.mRequestCode = requestCode;
            this.mBitmap = bitmap;
            this.mWidth = bitmap.getWidth();
            this.mHeight = bitmap.getHeight();
            this.mRectClipRatio = rectClipRatio;
            this.mBeginTimeStamp = beginTimeStamp;
        }

        DecodeRunnable(int[] pixels, int width, int height, RectF rectClipRatio, int requestCode, long beginTimeStamp) {
            this.mRequestCode = requestCode;
            this.mPixels = pixels;
            this.mWidth = width;
            this.mHeight = height;
            this.mRectClipRatio = rectClipRatio;
            this.mBeginTimeStamp = beginTimeStamp;
        }

        DecodeRunnable(byte[] frameData, int width, int height, RectF rectClipRatio, long beginTimeStamp) {
            this.mYUVFrameData = frameData;
            this.mWidth = width;
            this.mHeight = height;
            this.mRectClipRatio = rectClipRatio;
            this.mBeginTimeStamp = beginTimeStamp;
        }

        @Override
        public void run() {
            synchronized (decodeLock) {
                //1.获取YUV图像
                if (mYUVFrameData == null) {
                    if (mPixels == null) {
                        if (mBitmap == null) {
                            mBitmap = getBitmap(mContext, mUri);
                        }
                        if (mBitmap != null) {
                            mWidth = mBitmap.getWidth();
                            mHeight = mBitmap.getHeight();
                        }
                        mPixels = getBitmapPixels(mBitmap);
                    }
                    mYUVFrameData = getYUVFrameData(mPixels, mWidth, mHeight);
                }
                //2.解析图像
                SymbolSet symbolSet = decodeImage(mYUVFrameData, mWidth, mHeight, mRectClipRatio);
                //3.分析结果
                takeResult(symbolSet, mRequestCode, mBeginTimeStamp);
            }
        }

    }

    private Bitmap getBitmap(Context context, Uri uri) {
        if (uri == null) return null;
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    private int[] getBitmapPixels(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        return pixels;
    }

    private byte[] getYUVFrameData(int[] pixels, int width, int height) {
        if (pixels == null) return null;

        int index = 0;
        int yIndex = 0;
        int R, G, B, Y, U, V;
        byte[] frameData = new byte[width * height];

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = (pixels[index] & 0xff0000) >> 16;
                G = (pixels[index] & 0xff00) >> 8;
                B = (pixels[index] & 0xff);

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
//                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
//                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                frameData[yIndex++] = (byte) (Math.max(0, Math.min(Y, 255)));
//                if (j % 2 == 0 && index % 2 == 0) {
//                    yuv420sp[uvIndex++] = (byte)(Math.max(0, Math.min(U, 255)));
//                    yuv420sp[uvIndex++] = (byte)(Math.max(0, Math.min(V, 255)));
//                }
                index++;
            }
        }
        return frameData;
    }

}
