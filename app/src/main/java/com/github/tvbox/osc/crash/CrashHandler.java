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

        // 1. 保存日志
        saveCrashLog(ex);

        // 2. 跳转至崩溃页面
        Intent intent = new Intent(context, CrashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // 3. 结束当前进程
        Process.killProcess(Process.myPid());
    }

private void saveCrashLog(Throwable ex) {
    try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        // ❌ 原代码：尝试打印所有堆栈（危险）
        // ex.printStackTrace(pw); 
        
        // ✅ 新代码：只打印异常信息和前几行堆栈（安全）
        pw.println(ex); // 打印异常类型和消息
        StackTraceElement[] elements = ex.getStackTrace();
        int maxLines = Math.min(100, elements.length); // 只取前20行
        for (int i = 0; i < maxLines; i++) {
            pw.println("    at " + elements[i]);
        }
        pw.println("... <StackOverflow Truncated> ..."); // 标记被截断
        
        String crashTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String log = "===== Crash Time: " + crashTime + " =====\n" + sw.toString();
        
        CrashLogUtil.save(context, log);
        
    } catch (Exception e) {
        Log.e(TAG, "保存崩溃日志失败", e);
    }
}

}
