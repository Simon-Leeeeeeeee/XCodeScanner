package com.simonlee.demo.camerascanner;

import android.annotation.SuppressLint;
import android.support.annotation.StringRes;
import android.widget.Toast;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 */

@SuppressWarnings("unused")
@SuppressLint("ShowToast")
public class ToastHelper {

    private static Toast mToast = null;

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    /**
     * 弹出Toast
     *
     * @param resId    提示文本的资源id
     * @param duration 持续时间（0：短；1：长）
     */
    public static void showToast(@StringRes int resId, int duration) {
        if (mToast == null) {
            mToast = Toast.makeText(APP.getApp(), resId, duration);
        } else {
            mToast.setText(resId);
            mToast.setDuration(duration);
        }
        mToast.show();
    }

    /**
     * 弹出Toast
     *
     * @param text     提示文本
     * @param duration 持续时间（0：短；1：长）
     */
    public static void showToast(String text, int duration) {
        if (mToast == null) {
            mToast = Toast.makeText(APP.getApp(), text, duration);
        } else {
            mToast.setText(text);
            mToast.setDuration(duration);
        }
        mToast.show();
    }

    /**
     * 弹出Toast
     *
     * @param resId    提示文本的资源id
     * @param duration 持续时间（0：短；1：长）
     * @param gravity  位置（Gravity.CENTER;Gravity.TOP;...）
     */
    public static void showToast(@StringRes int resId, int duration, int gravity) {
        if (mToast == null) {
            mToast = Toast.makeText(APP.getApp(), resId, duration);
        } else {
            mToast.setText(resId);
            mToast.setDuration(duration);
        }
        mToast.setGravity(gravity, 0, 0);
        mToast.show();
    }

    /**
     * 弹出Toast
     *
     * @param text     提示文本
     * @param duration 持续时间（0：短；1：长）
     * @param gravity  位置（Gravity.CENTER;Gravity.TOP;...）
     */
    public static void showToast(String text, int duration, int gravity) {
        if (mToast == null) {
            mToast = Toast.makeText(APP.getApp(), text, duration);
        } else {
            mToast.setText(text);
            mToast.setDuration(duration);
        }
        mToast.setGravity(gravity, 0, 0);
        mToast.show();
    }

    /**
     * 关闭Toast
     */
    public static void cancelToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

}
