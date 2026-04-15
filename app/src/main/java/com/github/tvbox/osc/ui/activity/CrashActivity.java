package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.crash.CrashLogUtil;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.ui.activity.HomeActivity;

import java.io.File;
import java.io.FileWriter;

/**
 * xuameng
 * 全局崩溃捕获 - 修复日志过长卡死问题
 * @version 2.0.0
 */
public class CrashActivity extends BaseActivity {

    private TextView tvLog;
    private TextView tvRestart;
    private TextView tvShare;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private String crashLog = "";
    private static final int MAX_DISPLAY_LENGTH = 50;  //最多50行
    private static final String LOG_FILE_NAME = "crash_log.txt";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_crash;
    }

    @Override
    protected void init() {
        tvLog = findViewById(R.id.tvCrashLog);
        tvRestart = findViewById(R.id.tvRestart);
        tvShare = findViewById(R.id.tvShare);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);

        // 显示加载中状态
        progressBar.setVisibility(View.VISIBLE);
        tvLog.setText("正在加载崩溃日志...");
        
        // 隐藏分享按钮直到日志加载完成
        tvShare.setVisibility(View.GONE);
        tvRestart.setVisibility(View.GONE);

        // 异步加载日志
        loadCrashLogAsync();

        // 设置点击监听器
        tvRestart.setOnClickListener(v -> restartApp());
        tvShare.setOnClickListener(v -> copyCrashLogToClipboard());

        // 设置按键监听器
        tvRestart.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
				CrashLogUtil.deleteCrashLog(this);  //删除日志
                restartApp();
                return true;
            }
            return false;
        });

        tvShare.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                copyCrashLogToClipboard();
                return true;
            }
            return false;
        });
    }

    /**
     * 异步加载崩溃日志
     */
    private void loadCrashLogAsync() {
        new Thread(() -> {
            try {
                // 获取完整崩溃日志
                crashLog = CrashLogUtil.get(CrashActivity.this);
                
                // 在主线程更新UI
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (crashLog.length() > MAX_DISPLAY_LENGTH) {
                        // 只显示错误摘要
                        String summary = extractErrorSummary(crashLog);
                        String displayText = String.format(
                            "检测到详细错误日志（%d 字符）\n\n" +
                            "主要错误信息：\n%s\n\n" +
                            "完整日志可通过\"复制日志\"按钮保存到系统剪切版",
                            crashLog.length(),
                            summary
                        );
                        tvLog.setText(displayText);
                    } else {
                        // 只显示错误摘要
                        String summary = extractErrorSummary(crashLog);
                        String displayText = String.format(
                            "检测到详细错误日志（%d 字符）\n\n" +
                            "完整错误信息：\n%s\n\n" +
                            "日志可通过\"复制日志\"按钮保存到系统剪切版",
                            crashLog.length(),
                            summary
                        );
                        tvLog.setText(displayText);
                    }
                    
                    // 显示操作按钮
                    tvShare.setVisibility(View.VISIBLE);
                    tvRestart.setVisibility(View.VISIBLE);
                    
                    // 设置焦点到重启按钮
                    tvRestart.requestFocus();
                    
                    // 滚动到底部查看最新日志
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvLog.setText("加载日志失败：" + e.getMessage());
                    tvRestart.setVisibility(View.VISIBLE);
                    tvRestart.requestFocus();
                });
            }
        }).start();
    }

    /**
     * 提取错误摘要（前50行）
     */
    private String extractErrorSummary(String fullLog) {
        if (fullLog == null || fullLog.isEmpty()) {
            return "无错误信息";
        }

        String[] lines = fullLog.split("\n");
        StringBuilder summary = new StringBuilder();
        int linesToShow = Math.min(50, lines.length);

        for (int i = 0; i < linesToShow; i++) {
            summary.append(lines[i]).append("\n");
        }

        return summary.toString();
    }

    private void copyCrashLogToClipboard() {
        if (crashLog == null || crashLog.isEmpty()) {
            App.showToastShort(this, "没有可复制的日志");
            return;
        }

        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            android.content.ClipData clip =
                    android.content.ClipData.newPlainText("Crash Log", crashLog);

            clipboard.setPrimaryClip(clip);

            App.showToastShort(this, "崩溃日志已复制到剪切板");
        } catch (Exception e) {
            e.printStackTrace();
            App.showToastShort(this, "崩溃日志复制失败：" + e.getMessage());
        }
    }

    /**
     * 重启应用
     */
    private void restartApp() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        
        // 强制停止当前进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
