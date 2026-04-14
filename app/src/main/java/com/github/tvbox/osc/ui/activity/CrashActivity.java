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

        tvLog.setText(CrashLogUtil.get(this));

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

        tvCopyLog.setOnClickListener(v -> copyLogSafe());
        tvCopyLog.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                copyLogSafe();
                return true;
            }
            return false;
        });
    }

    private void copyLogSafe() {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(
                        ClipData.newPlainText("crash", tvLog.getText())
                );
            }
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void restartApp() {
        startActivity(new Intent(this, HomeActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                          Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
