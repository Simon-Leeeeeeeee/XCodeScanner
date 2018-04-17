package com.simonlee.scanner.core;

import android.graphics.RectF;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

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
public class ZBarDecoder extends Thread implements GraphicDecoder, BaseHandler.BaseHandlerListener {

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

    private final String TAG = "CodeScanner";

    private final Object decodeLock = new Object();//互斥锁

    private int[] mSymbolTypes;//要识别的条码格式

    private final BaseHandler mCurThreadHandler;

    private volatile boolean running = true;

    private volatile boolean frameAvailable = false;

    private volatile byte[] mFrameData;
    private volatile int mWidth;
    private volatile int mHeight;
    private volatile RectF mRectClipRatio;

    private final DecodeListener mDecodeListener;

    public ZBarDecoder(DecodeListener listener) {
        this.mCurThreadHandler = new BaseHandler(this);
        this.mDecodeListener = listener;
        super.start();
    }

    @Override
    public synchronized void decode(byte[] frameData, int width, int height, RectF rectClipRatio) {
        if (!running) return;
        mFrameData = frameData;
        mWidth = width;
        mHeight = height;
        mRectClipRatio = rectClipRatio;
        frameAvailable = true;
    }

    @Override
    public synchronized void detach() {
        running = false;
        synchronized (decodeLock) {
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

    @Override
    public final synchronized void start() {
    }

    @Override
    public void run() {
        init();
        while (running) {//循环
            if (frameAvailable) {
                SymbolSet symbolSet;
                //1.解析图像
                synchronized (decodeLock) {
                    symbolSet = decodeImage(mFrameData, mWidth, mHeight, mRectClipRatio);
                }
                //2.分析结果
                takeResult(symbolSet);
                frameAvailable = false;
            }
        }
    }

    /**
     * 初始化ImageScanner&Image
     */
    private void init() {
        mImageScanner = new ImageScanner();
        mImageScanner.setConfig(0, Config.X_DENSITY, 3);
        mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
        mImageScanner.setConfig(0, Config.ENABLE, 0);//Disable all the Symbols
        for (int symbolType : getSymbolTypes()) {
            mImageScanner.setConfig(symbolType, Config.ENABLE, 1);//Only symbolType is enable
        }
        mZBarImage = new Image("Y800");
    }

    /**
     * 获取支持的条码格式
     */
    public int[] getSymbolTypes() {
        if (mSymbolTypes == null) {
            mSymbolTypes = new int[]{EAN8, ISBN10, UPCA, EAN13, ISBN13, I25//, PARTIAL, UPCE, DATABAR
                    , DATABAR_EXP, CODABAR, CODE39, PDF417, QRCODE, CODE93, CODE128};
        }
        return mSymbolTypes;
    }

    /**
     * 设置支持的条码格式
     */
    public void setSymbolTypes(int[] symbolTypes) {
        this.mSymbolTypes = symbolTypes;
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
    private void takeResult(SymbolSet symbolSet) {
        if (symbolSet == null) return;
        for (Symbol symbol : symbolSet) {
            String result = symbol.getData();
            if (result != null && result.length() > 0) {
//                byte[] dataBytes = symbol.getDataBytes();
//                int[] bounds = symbol.getBounds();
//                int count = symbol.getCount();
                int type = symbol.getType();
                int quality = symbol.getQuality();
                Log.d(TAG, getClass().getName() + ".zbarDecode() : type = " + type + " , quality = " + quality + " , result = " + result);
                mCurThreadHandler.sendMessage(mCurThreadHandler.obtainMessage(0, type, quality, result));
                break;
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        mDecodeListener.decodeSuccess(msg.arg1, msg.arg2, (String) msg.obj);
    }

}
