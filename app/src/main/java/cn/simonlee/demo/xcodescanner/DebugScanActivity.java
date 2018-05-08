package cn.simonlee.demo.xcodescanner;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
public class DebugScanActivity extends ScanActivity implements DebugZBarDecoder.ZBarDebugListener {

    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TAG = "XCodeScanner-Debug";
        super.onCreate(savedInstanceState);
        mTextView = findViewById(R.id.textview);
    }

    @Override
    public void openCameraSuccess(int frameWidth, int frameHeight, int frameDegree) {
        if (mGraphicDecoder == null) {
            mGraphicDecoder = new DebugZBarDecoder();
            ((DebugZBarDecoder) mGraphicDecoder).setZBarDebugListener(this);
            mGraphicDecoder.setDecodeListener(this);
        }
        super.openCameraSuccess(frameWidth, frameHeight, frameDegree);
    }

    @Override
    public void ZBarDebug(int fps, long decodeExpendTime) {
        Log.e(TAG, getClass().getName() + ".ZBarDebug() FPS = " + fps + " , decodeExpendTime = " + decodeExpendTime);
        mTextView.setText(String.format(getResources().getString(R.string.tips_scan_debug), fps, decodeExpendTime));
    }

}
