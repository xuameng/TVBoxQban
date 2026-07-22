package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);//设置刘海
        super.onCreate(savedInstanceState);
    }

@Override
public void show() {
    // 防止重复触发
    if (isShowing()) return;

    // 先清焦点标记，防止输入法/焦点抢窗口
    getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    );

    // 先隐藏系统栏
    hideSysBar();

    // 监听系统 UI 是否真正隐藏
    getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
            new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    boolean fullscreen =
                            (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                    boolean navHidden =
                            (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;

                    if (fullscreen && navHidden) {
                        // ✅ 系统栏已完全隐藏
                        getWindow().getDecorView()
                                .setOnSystemUiVisibilityChangeListener(null);

                        // ✅ 再真正显示 Dialog
                        BaseDialog.super.show();
                        getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        );
                    }
                }
            }
    );

}

    @Override
    public void dismiss() {
        super.dismiss(); // XUAMENG必须调用父类方法
    }

    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}
