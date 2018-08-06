package cn.simonlee.demo.xcodescanner;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.StringRes;
import android.widget.Toast;

/**
 * Toast帮助类
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
@SuppressWarnings({"unused", "ShowToast"})
class ToastHelper {
    private static Toast mToast = null;

    static final int LENGTH_SHORT = 0;
    static final int LENGTH_LONG = 1;

    /**
     * 弹出Toast
     *
     * @param resId    提示文本的资源id
     * @param duration 持续时间（0：短；1：长）
     */
    static void showToast(Context context, @StringRes int resId, int duration) {
        show(context, context.getText(resId), duration, null);
    }

    /**
     * 弹出Toast
     *
     * @param text     提示文本
     * @param duration 持续时间（0：短；1：长）
     */
    static void showToast(Context context, CharSequence text, int duration) {
        show(context, text, duration, null);
    }

    /**
     * 弹出Toast
     *
     * @param resId    提示文本的资源id
     * @param duration 持续时间（0：短；1：长）
     * @param gravity  位置（Gravity.CENTER;Gravity.TOP;...）
     */
    static void showToast(Context context, @StringRes int resId, int duration, int gravity) {
        show(context, context.getText(resId), duration, gravity);
    }

    /**
     * 弹出Toast
     *
     * @param text     提示文本
     * @param duration 持续时间（0：短；1：长）
     * @param gravity  位置（Gravity.CENTER;Gravity.TOP;...）
     */
    static void showToast(Context context, CharSequence text, int duration, int gravity) {
        show(context, text, duration, gravity);
    }

    /**
     * 关闭Toast
     */
    static void cancelToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    private static void show(final Context context, final CharSequence text, final int duration, final Integer gravity) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            if (mToast == null) {
                mToast = Toast.makeText(context.getApplicationContext(), text, duration);
            } else {
                mToast.setText(text);
                mToast.setDuration(duration);
            }
            if (gravity != null) {
                mToast.setGravity(gravity, 0, 0);
            }
            mToast.show();
        } else if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    show(context.getApplicationContext(), text, duration, gravity);
                }
            });
        }
    }
}
