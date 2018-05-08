package cn.simonlee.xcodescanner.core;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-02-02
 */
public class BaseHandler extends Handler {

    private WeakReference<BaseHandlerListener> weakReference;

    public BaseHandler(BaseHandlerListener listener) {
        this.weakReference = new WeakReference<>(listener);
    }

    public BaseHandler(BaseHandlerListener listener, Looper looper) {
        super(looper);
        this.weakReference = new WeakReference<>(listener);
    }

    @Override
    public void handleMessage(Message msg) {
        if (this.weakReference != null) {
            BaseHandlerListener listener = weakReference.get();
            if (listener != null) {
                listener.handleMessage(msg);
            }
        }
    }

    public void clear() {
        removeCallbacksAndMessages(null);
        if (weakReference != null) {
            weakReference.clear();
            weakReference = null;
        }
    }

    public interface BaseHandlerListener {
        void handleMessage(Message msg);
    }
}
