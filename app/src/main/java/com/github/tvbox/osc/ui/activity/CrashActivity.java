package com.github.tvbox.osc.ui.activity;

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
    private static final int MAX_DISPLAY_LENGTH = 8000;
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
        tvShare.setOnClickListener(v -> shareCrashLog());

        // 设置按键监听器
        tvRestart.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                restartApp();
                return true;
            }
            return false;
        });

        tvShare.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                shareCrashLog();
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
                            "完整日志可通过\"分享日志\"按钮导出",
                            crashLog.length(),
                            summary
                        );
                        tvLog.setText(displayText);
                    } else {
                        tvLog.setText(crashLog);
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
     * 提取错误摘要（最后30行）
     */
    private String extractErrorSummary(String fullLog) {
        if (fullLog == null || fullLog.isEmpty()) {
            return "无错误信息";
        }
        
        String[] lines = fullLog.split("\n");
        StringBuilder summary = new StringBuilder();
        int linesToShow = Math.min(30, lines.length);
        
        for (int i = Math.max(0, lines.length - linesToShow); i < lines.length; i++) {
            summary.append(lines[i]).append("\n");
        }
        
        return summary.toString();
    }

    /**
     * 分享崩溃日志到文件
     */
    private void shareCrashLog() {
        if (crashLog == null || crashLog.isEmpty()) {
            Toast.makeText(this, "没有可分享的日志", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // 创建临时文件
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = getCacheDir();
            }
            
            File logFile = new File(cacheDir, LOG_FILE_NAME);
            
            // 写入日志到文件
            FileWriter writer = new FileWriter(logFile);
            writer.write(crashLog);
            writer.close();
            
            // 创建FileProvider URI
            Uri fileUri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".provider",
                logFile
            );
            
            // 创建分享Intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "应用崩溃日志");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "这是应用的崩溃日志文件，请查看附件。");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 启动分享选择器
            Intent chooser = Intent.createChooser(shareIntent, "分享崩溃日志");
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "没有找到可用的分享应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
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
