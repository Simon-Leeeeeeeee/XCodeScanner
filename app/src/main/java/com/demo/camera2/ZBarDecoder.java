package com.demo.camera2;

import android.graphics.RectF;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

@SuppressWarnings("unused")
public class ZBarDecoder extends GraphicDecoder implements BaseHandler.BaseHandlerListener {

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
//    /**
//     * DataBar (RSS-14).
//     */
//    public static final int DATABAR = 34;//不常用且易误判，取消此种格式的条码识别
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

    private ExecutorService mExecutorService;

    private final String TAG = "Camera2Scanner";

    private final Object lock_Decode = new Object();//互斥锁

    private int[] mSymbolTypes;//要识别的条码格式

    private final BaseHandler mHandler;

    private volatile boolean isDetached;

    public ZBarDecoder(DecodeListener listener) {
        super(listener);
        this.mHandler = new BaseHandler(this);
        this.isDetached = false;
    }

    @Override
    public synchronized void decode(byte[] data, int width, int height, RectF frameRatioRect) {
        if (isDetached) return;
        if (mImageScanner == null) {
            mImageScanner = new ImageScanner();
            mImageScanner.setConfig(0, Config.X_DENSITY, 3);
            mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
            mImageScanner.setConfig(0, Config.ENABLE, 0);
            for (int symbolType : getSymbolTypes()) {
                mImageScanner.setConfig(symbolType, Config.ENABLE, 1);
            }
        }
        if (mZBarImage == null) {
            mZBarImage = new Image("Y800");
        }
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mExecutorService.execute(new DecodeRunnable(data, width, height, frameRatioRect));
    }

    /**
     * 获取条码格式
     */
    public int[] getSymbolTypes() {
        if (mSymbolTypes == null) {
            mSymbolTypes = new int[]{PARTIAL, EAN8, UPCE, ISBN10, UPCA, EAN13, ISBN13, I25//, DATABAR
                    , DATABAR_EXP, CODABAR, CODE39, PDF417, QRCODE, CODE93, CODE128};
        }
        return mSymbolTypes;
    }

    /**
     * 设置条码格式
     */
    public void setSymbolTypes(int[] symbolTypes) {
        this.mSymbolTypes = symbolTypes;
    }

    @Override
    public synchronized void detach() {
        synchronized (lock_Decode) {
            isDetached = true;
            if (mZBarImage != null) {
                mZBarImage.destroy();
                mZBarImage = null;
            }
            if (mImageScanner != null) {
                mImageScanner.destroy();
                mImageScanner = null;
            }
            if (mExecutorService != null) {
                mExecutorService.shutdownNow();
                mExecutorService = null;
            }
        }
        super.detach();
    }

    @Override
    public void handleMessage(Message msg) {
        deliverResult(msg.arg1, msg.arg2, (String) msg.obj);
    }

    private SymbolSet ZBarDecode(byte[] data, int width, int height, RectF frameRatioRect) {
        synchronized (lock_Decode) {
            if (isDetached) return null;
            mZBarImage.setSize(width, height);
            if (frameRatioRect != null) {
                int frameLeft = (int) (frameRatioRect.left * width);
                int frameTop = (int) (frameRatioRect.top * height);
                int frameWidth = (int) (frameRatioRect.width() * width);
                int frameHeight = (int) (frameRatioRect.height() * height);
                mZBarImage.setCrop(frameLeft, frameTop, frameWidth, frameHeight);
            }
            mZBarImage.setData(data);
            if (mImageScanner.scanImage(mZBarImage) != 0) {
                return mImageScanner.getResults();
            }
        }
        return null;
    }

    private class DecodeRunnable implements Runnable {

        private final byte[] data;
        private final int width;
        private final int height;
        private final RectF frameRatioRect;

        private DecodeRunnable(byte[] data, int width, int height, RectF frameRatioRect) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.frameRatioRect = frameRatioRect;
        }

        @Override
        public void run() {
            SymbolSet symbolSet = ZBarDecode(data, width, height, frameRatioRect);
            if (symbolSet == null) return;
            Log.d(TAG, getClass().getName() + ".zbarDecode() : symbolSet.size() = " + symbolSet.size());
            for (Symbol symbol : symbolSet) {
                Log.e(TAG, getClass().getName() + ".zbarDecode()========");
                String result = symbol.getData();
                if (result != null && result.length() > 0) {
//                    byte[] dataBytes = symbol.getDataBytes();
//                    int[] bounds = symbol.getBounds();
//                    int count = symbol.getCount();
                    int type = symbol.getType();
                    int quality = symbol.getQuality();
                    Log.d(TAG, getClass().getName() + ".zbarDecode() : type = " + type
                            + " , quality = " + quality + " , result = " + result);
                    mHandler.sendMessage(mHandler.obtainMessage(0, type, quality, result));
//                    break;
                }
            }
        }

    }

}
