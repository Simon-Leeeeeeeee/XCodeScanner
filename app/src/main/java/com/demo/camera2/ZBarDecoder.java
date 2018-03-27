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

    private ExecutorService mExecutorService;

    private final String TAG = "Camera2Scanner";

    private final Object lock_Decode = new Object();//互斥锁

    private int mQualityRequire = 10;//解码质量要求

    private final BaseHandler mHandler;

    public ZBarDecoder(DecodeListener listener) {
        super(listener);
        initZBar();
        this.mHandler = new BaseHandler(this);
    }

    /**
     * 初始化，配置条码格式
     */
    private void initZBar() {
        mImageScanner = new ImageScanner();
        mImageScanner.setConfig(0, Config.X_DENSITY, 3);
        mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
        mImageScanner.setConfig(0, Config.ENABLE, 0);
        for (int symbolType : getSymbolTypes()) {
            mImageScanner.setConfig(symbolType, Config.ENABLE, 1);
        }
    }

    /**
     * 初始化，配置条码格式，可复写
     */
    public int[] getSymbolTypes() {
        return new int[]{PARTIAL, EAN8, UPCE, ISBN10, UPCA, EAN13, ISBN13, I25, DATABAR
                , DATABAR_EXP, CODABAR, CODE39, PDF417, QRCODE, CODE93, CODE128};
    }

    /**
     * 设置质量要求
     */
    public void setBarCodeQualityRequire(int qualityRequire) {
        this.mQualityRequire = qualityRequire;
    }

    @Override
    public void decode(byte[] data, int width, int height, RectF frameRatioRect) {
        if (mImageScanner == null) return;
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        if (mZBarImage == null) {
            mZBarImage = new Image("Y800");
        }
        mExecutorService.execute(new DecodeRunnable(data, width, height, frameRatioRect));
    }

    @Override
    public void handleMessage(Message msg) {
        deliverResult((String) msg.obj);
    }

    @Override
    public void detach() {
        super.detach();
        synchronized (lock_Decode) {
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
            SymbolSet symbolSet = null;
            synchronized (lock_Decode) {
                if (mZBarImage == null) return;
                mZBarImage.setSize(width, height);
                if (frameRatioRect != null) {
                    int frameLeft = (int) (frameRatioRect.left * width);
                    int frameTop = (int) (frameRatioRect.top * height);
                    int frameWidth = (int) (frameRatioRect.width() * width);
                    int frameHeight = (int) (frameRatioRect.height() * height);
                    mZBarImage.setCrop(frameLeft, frameTop, frameWidth, frameHeight);
                }
                mZBarImage.setData(data);
                if (mImageScanner != null && mImageScanner.scanImage(mZBarImage) != 0) {
                    symbolSet = mImageScanner.getResults();
                }
            }
            if (symbolSet == null) return;
            for (Symbol symbol : symbolSet) {
                String result = symbol.getData();
                if (result != null && result.length() > 0) {
                    int types = symbol.getType();
                    int quality = symbol.getQuality();
                    Log.d(TAG, getClass().getName() + ".zbarDecode() : types = " + types
                            + " , quality = " + quality + " , result = " + result);
                    if (quality > mQualityRequire) {
                        Message msg = mHandler.obtainMessage(0, result);
                        mHandler.sendMessage(msg);
                    }
//                    byte[] dataBytes = symbol.getDataBytes();
//                    int[] bounds = symbol.getBounds();
//                    int count = symbol.getCount();
                    break;
                }
            }
        }

    }

}
