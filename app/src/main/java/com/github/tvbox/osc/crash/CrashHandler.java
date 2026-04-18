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
 * 全局崩溃捕获 - 优化版本
 * 修复了原版本处理大日志时卡死的问题
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final CrashHandler INSTANCE = new CrashHandler();
    private static final int MAX_LOG_SIZE = 100 * 1024; // 限制日志最大100KB
    private static final int MAX_LINES = 400; // 最多保存400行

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

        // 1. 异步保存日志，避免阻塞主线程
        new Thread(() -> {
            saveCrashLogOptimized(ex);
            
            // 2. 跳转至崩溃页面
            Intent intent = new Intent(context, CrashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

            // 3. 结束当前进程
            Process.killProcess(Process.myPid());
        }).start();
        
        // 给异步线程一点时间启动
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 优化后的日志保存方法
     * 1. 使用流式处理，避免一次性处理大字符串
     * 2. 限制总日志大小
     * 3. 限制行数
     */
    private void saveCrashLogOptimized(Throwable ex) {
        try {
            // 获取崩溃时间
            String crashTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            
            // 使用StringWriter获取堆栈信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.flush();
            pw.close();
            
            // 使用StringBuilder处理，但分块处理
            StringBuilder limitedLog = new StringBuilder();
            limitedLog.append("===== Crash Time: ").append(crashTime).append(" =====\n");
            
            // 逐行处理，避免split("\n")的性能问题
            String fullLog = sw.toString();
            int lineCount = 0;
            int totalSize = 0;
            int start = 0;
            
            for (int i = 0; i < fullLog.length() && lineCount < MAX_LINES && totalSize < MAX_LOG_SIZE; i++) {
                if (fullLog.charAt(i) == '\n') {
                    // 提取一行
                    String line = fullLog.substring(start, i);
                    limitedLog.append(line).append("\n");
                    
                    lineCount++;
                    totalSize += line.length() + 1; // +1 for newline
                    start = i + 1;
                    
                    if (lineCount >= MAX_LINES || totalSize >= MAX_LOG_SIZE) {
                        limitedLog.append("\n[日志被截断，原始日志共")
                                 .append(countLines(fullLog))
                                 .append("行，")
                                 .append(fullLog.length())
                                 .append("字符]\n");
                        break;
                    }
                }
            }
            
            // 处理最后一行（如果没有以换行符结尾）
            if (start < fullLog.length() && lineCount < MAX_LINES && totalSize < MAX_LOG_SIZE) {
                String lastLine = fullLog.substring(start);
                limitedLog.append(lastLine);
            }
            
            // 保存日志
            CrashLogUtil.save(context, limitedLog.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }
    
    /**
     * 高效计算行数的方法
     */
    private int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
