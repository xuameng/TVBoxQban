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
    if (window == null) return;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false);

        // ✅ 关键：强制窗口认为系统栏不存在
        window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
    } else {
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

    // ✅ Android 11+：立即隐藏
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.getInsetsController().hide(
                android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars()
        );
        window.getInsetsController().setSystemBarsBehavior(
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    // ✅ 低版本兜底
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        hideSysBarSafe();
    }
}

@Override
public void show() {
    super.show();
    // ✅ Android 11+：绝对不要 hide / requestLayout
}

    @Override
    public void dismiss() {
        super.dismiss();
    }

    /**
     * ✅ 横屏非全屏：只隐藏，不影响测量基准
     */
    private void hideSysBarSafe() {
        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars()
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            int uiOptions =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * ✅ 不再依赖 focus 变化
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }
}
