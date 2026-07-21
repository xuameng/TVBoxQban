package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {

    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
        init();
    }

    public BaseDialog(Context context, int themeResId) {
        super(context, themeResId);
        init();
    }

    private void init() {
        // 关键：Dialog 不要自己决定 fitsSystemWindows
        Window window = getWindow();
        if (window != null) {
            window.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window == null) return;

        // 刘海适配（只做，不抢布局）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CutoutUtil.adaptCutoutAboveAndroidP(this, true);
        }

        // ✅ 横屏非全屏：只隐藏，不占坑
        hideSysBarSafe();
    }

    @Override
    public void show() {
        super.show();
        hideSysBarSafe();
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    /**
     * ✅ 横屏非全屏唯一正确的沉浸式写法
     */
    private void hideSysBarSafe() {
        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {      //安卓11
            window.setDecorFitsSystemWindows(false);
            window.getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars()
            );
            window.getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //安卓4.4
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            // ❌ 不要 LAYOUT_FULLSCREEN
            // ❌ 不要 LAYOUT_HIDE_NAVIGATION
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            // ✅ Android 4.3 / 4.2 / 4.1
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * ✅ 切前台：只补刀，不 layout
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSysBarSafe();
        }
    }
}
