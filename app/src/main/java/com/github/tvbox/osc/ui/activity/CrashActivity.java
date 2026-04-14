package com.github.tvbox.osc.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.crash.CrashLogUtil;
import com.github.tvbox.osc.base.App;

/**
 * xuameng
 * 全局崩溃捕获
 * @version 1.0.0 <br/>
 */

public class CrashActivity extends BaseActivity {

    private TextView tvLog;
    private TextView tvRestart;
    private TextView tvCopyLog;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_crash;
    }

    @Override
    protected void init() {
        tvLog = findViewById(R.id.tvCrashLog);
        tvRestart = findViewById(R.id.tvRestart);
        tvCopyLog = findViewById(R.id.tvCopyLog);

        String crashLog = CrashLogUtil.get(this);
        tvLog.setText(crashLog);

        // 默认焦点给复制按钮（更安全）
        tvCopyLog.requestFocus();

        // 复制日志
        tvCopyLog.setOnClickListener(v -> copyLog(crashLog));
        tvCopyLog.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                copyLog(crashLog);
                return true;
            }
            return false;
        });

        // 重启应用
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

    private void copyLog(String log) {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;

            ClipData clipData = ClipData.newPlainText("crash_log", log);
            cm.setPrimaryClip(clipData);

            Toast.makeText(this, "错误日志已复制", Toast.LENGTH_SHORT).show();
            App.showToastShort(this, "错误日志已复制");
        } catch (Exception e) {
            App.showToastShort(this, "日志复制失败");
        }
    }

    private void restartApp() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
