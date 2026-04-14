package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.crash.CrashLogUtil;

public class CrashActivity extends BaseActivity {

    private TextView tvLog;
    private TextView tvRestart;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_crash;
    }

    @Override
    protected void init() {
        tvLog = findViewById(R.id.tvCrashLog);
        tvRestart = findViewById(R.id.tvRestart);

        tvLog.setText(CrashLogUtil.get(this));

        // 默认焦点给重启按钮
        tvRestart.requestFocus();

        tvRestart.setOnClickListener(v -> restartApp());
        tvRestart.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                restartApp();
                return true;
            }
            return false;
        });
    }

    private void restartApp() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
