package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.app.Activity;
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
    if (isShowing()) return;

    // 1️⃣ 先让 Activity 进入稳定全屏（关键）
    if (getContext() instanceof Activity) {
        ((Activity) getContext()).getWindow()
                .getDecorView()
                .setSystemUiVisibility(getFullscreenFlags());
    }

    // 2️⃣ 防止 Dialog 抢焦点导致输入法/闪动
    getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    );

    // 3️⃣ 真正 show（此时 Window insets 已稳定）
    super.show();

    // 4️⃣ Dialog 自己再补一刀
    hideSysBar();

    // 5️⃣ 延迟一帧再恢复焦点，确保布局不再变化
    getWindow().getDecorView().post(() ->
        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    );
}

private int getFullscreenFlags() {
    return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
         | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
         | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
         | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
         | View.SYSTEM_UI_FLAG_FULLSCREEN
         | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
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
