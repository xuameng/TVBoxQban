package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.crash.CrashLogUtil;

/**
 * xuameng
 * 全局崩溃捕获
 * @version 1.0.0 <br/>
 */
public class CrashActivity extends Activity {

    private TextView tvLog;
    private TextView tvRestart;
    private TextView tvCopyLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        tvLog = findViewById(R.id.tvCrashLog);
        tvRestart = findViewById(R.id.tvRestart);
        tvCopyLog = findViewById(R.id.tvCopyLog);

        // 显示崩溃日志（绝不抛异常）
        try {
            tvLog.setText(CrashLogUtil.get(this));
        } catch (Exception ignored) {
            tvLog.setText("崩溃日志读取失败");
        }

        // 默认焦点给重启按钮（最安全）
        tvRestart.requestFocus();

        // 复制日志
        tvCopyLog.setOnClickListener(v -> copyLogSafe());
        tvCopyLog.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                copyLogSafe();
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

    private void copyLogSafe() {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;

            cm.setPrimaryClip(
                    ClipData.newPlainText("crash_log", tvLog.getText())
            );

            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // 复制失败 → 静默处理
        }
    }

    private void restartApp() {
        try {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
            finish();
        } catch (Exception ignored) {
            // 极端情况下兜底
            finish();
        }
    }
}
