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
 * xuameng
 * 全局崩溃捕获
 * @version 1.0.0 <br/>
 */

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static CrashHandler instance = new CrashHandler();

    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {}

    public static CrashHandler getInstance() {
        return instance;
    }

    public void init(Context context) {
        this.context = context;
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }


@Override
public void uncaughtException(Thread thread, Throwable ex) {
    Log.e(TAG, "应用崩溃", ex);

    saveCrashLog(ex);

    Intent intent = new Intent(context, CrashActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    context.startActivity(intent);

    // 防止 CrashActivity 再崩溃
    Process.killProcess(Process.myPid());
}
    private void saveCrashLog(Throwable ex) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);

            String crashTime = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            ).format(new Date());

            String log = "===== Crash Time: " + crashTime + " =====\n"
                    + sw.toString();

            // 保存到文件（你也可以存 SharedPreferences）
            CrashLogUtil.save(context, log);

        } catch (Exception e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }
}
