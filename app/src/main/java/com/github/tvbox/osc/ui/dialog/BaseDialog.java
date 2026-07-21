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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CutoutUtil.adaptCutoutAboveAndroidP(this, true);
        }

        fixDialogSize(); // ✅ 初始化时锁定尺寸
        hideSysBarSafe();
    }

    @Override
    public void show() {
        super.show();
        fixDialogSize(); // ✅ 显示时锁定尺寸
        hideSysBarSafe();
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    /**
     * ✅ 修复切前台尺寸变形
     */
    private void fixDialogSize() {
        Window window = getWindow();
        if (window == null) return;
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(layoutParams);
    }

    private void hideSysBarSafe() {
        Window window = getWindow();
        if (window == null) return;
        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            window.getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars()

                            | android.view.WindowInsets.Type.navigationBars()
            );
            window.getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                            | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            fixDialogSize(); // ✅ 切前台时锁定尺寸
            hideSysBarSafe();
        }
    }
}
