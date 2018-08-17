package cn.simonlee.demo.xcodescanner;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import cn.simonlee.widget.swipeback.SwipeBackHelper;

/**
 * Activity基类
 *
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 */
@SuppressWarnings({"RestrictedApi", "unused", "NewApi"})
public abstract class BaseActivity extends AppCompatActivity {

    private SwipeBackHelper mSwipeBackHelper;
    private Toolbar mToolbar;

    @Override
    @CallSuper
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportSwipeBack();//开启侧滑返回
        fixToolbarPadding();//校正Toolbar的paddingTop
    }

    @Override
    @CallSuper
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        initToolbar();//初始化Toolbar
        fixToolbarPadding();//校正Toolbar的paddingTop
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mSwipeBackHelper != null) {
            mSwipeBackHelper.dispatchTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        hideInputSoft();//隐藏输入法
        if (mSwipeBackHelper != null) {
            mSwipeBackHelper.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        hideInputSoft();//隐藏输入法
        if (!moveTaskToBack(false)) {//APP切入后台
            super.onBackPressed();//非根Activity，切入后台失败，执行返回操作
        }
    }

    /**
     * 初始化Toolbar
     */
    private void initToolbar() {
        mToolbar = findViewById(R.id.base_toolbar);
        if (mToolbar == null) return;
        Menu menu = mToolbar.getMenu();
        if (menu != null && menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    /**
     * 校正Toolbar的paddingTop
     */
    private void fixToolbarPadding() {
        if (mSwipeBackHelper != null && mToolbar != null) {
            int paddingTop = 0;
            if (mSwipeBackHelper.isStatusBarTransparent()) {
                paddingTop = getStatusBarHeight();
            }
            mToolbar.setPadding(0, paddingTop, 0, 0);
            ViewGroup.LayoutParams lp = mToolbar.getLayoutParams();
            lp.height = paddingTop + getToolBarHeight();
        }
    }

    /**
     * 获取ToolBar的高度（即ActionBar）
     */
    public int getToolBarHeight() {
        TypedArray values = obtainStyledAttributes(new int[]{android.R.attr.actionBarSize});
        try {
            return values.getDimensionPixelSize(0, 0);//第一个参数数组索引，第二个参数 默认值
        } finally {
            values.recycle();
        }
    }

    /**
     * 获取状态栏的高度
     */
    public int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        try {
            return getResources().getDimensionPixelSize(resourceId);
        } catch (Resources.NotFoundException e) {
            return 0;
        }
    }

    /**
     * 开启侧滑返回
     */
    public void supportSwipeBack() {
        if (mSwipeBackHelper == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mSwipeBackHelper = new SwipeBackHelper(this);
//            //设置窗口背景颜色，覆盖不可见区域出现的黑色（不可见区域常见为当输入法及导航栏变化时的背景）
            mSwipeBackHelper.setWindowBackgroundColor(getResources().getColor(R.color.colorWindowBackground));
        }
    }

    /**
     * 隐藏输入法
     */
    public void hideInputSoft() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            currentFocus.clearFocus();
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

}
