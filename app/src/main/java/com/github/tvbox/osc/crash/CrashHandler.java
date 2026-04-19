package com.github.tvbox.osc.crash;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import com.github.tvbox.osc.ui.activity.CrashActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获
 *
 * @author xuameng
 * @version 2.0.0
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final CrashHandler INSTANCE = new CrashHandler();

    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        this.context = context;
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

@Override
public void uncaughtException(Thread thread, Throwable ex) {
    Log.e(TAG, "应用崩溃", ex);

    // 1. 保存日志 (同步操作)
    saveCrashLog(ex);

    // 2. 跳转至崩溃页面
    try {
        Intent intent = new Intent(context, CrashActivity.class);
        // 确保 flags 正确
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    } catch (Exception e) {
        Log.e(TAG, "启动崩溃页失败", e);
    }

    // 3. 【关键修改】延迟自杀
    try {
        // 等待 500 毫秒，给系统时间启动和显示 CrashActivity
        // 这段时间足够让 Activity 完成 onCreate 和 setContentView
        Thread.sleep(500);
    } catch (InterruptedException e) {
        Log.e(TAG, "Sleep interrupted", e);
    }

    // 4. 杀死进程
    // 此时 CrashActivity 应该已经显示出来了，再杀进程就不会触发系统的“启动失败”重启机制
    Process.killProcess(Process.myPid());
    System.exit(10); // 确保彻底退出
}

    private void saveCrashLog(Throwable ex) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.flush();

            String fullLog = sw.toString();

            // 限制最多 400 行
            String[] lines = fullLog.split("\n");
            StringBuilder limitedLog = new StringBuilder();
            int maxLines = Math.min(400, lines.length);

            for (int i = 0; i < maxLines; i++) {
                limitedLog.append(lines[i]).append("\n");
            }

            String crashTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            // 拼接日志头
            String log = "===== Crash Time: " + crashTime + " =====\n" + limitedLog.toString();

            CrashLogUtil.save(context, log);

        } catch (Exception e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }
}
