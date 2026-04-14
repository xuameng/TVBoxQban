package com.github.tvbox.osc.crash;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * xuameng
 * 全局崩溃捕获
 * @version 1.0.0 <br/>
 */
public class CrashLogUtil {

 private static final String TAG = "CrashLogUtil";
 private static final String FILE_NAME = "xuameng_crash.txt";
 private static final ExecutorService executor = Executors.newSingleThreadExecutor();
 private static final Handler handler = new Handler(Looper.getMainLooper());

 public static void save(Context context, String log) {
 executor.execute(() -> {
 try {
 File dir = context.getExternalFilesDir(null);
 if (dir == null) return;

 File file = new File(dir, FILE_NAME);
 FileOutputStream fos = new FileOutputStream(file, false);
 fos.write(log.getBytes(StandardCharsets.UTF_8));
 fos.close();
 } catch (Exception e) {
 handler.post(() -> Log.e(TAG, "保存崩溃日志失败", e));
 }
 });
 }

 public static void get(Context context, OnLogReadListener listener) {
 executor.execute(() -> {
 String result;
 try {
 File dir = context.getExternalFilesDir(null);
 if (dir == null) {
 result = "无崩溃日志";
 } else {
 File file = new File(dir, FILE_NAME);
 if (!file.exists()) {
 result = "无崩溃日志";
 } else {
 result = new String(
 java.nio.file.Files.readAllBytes(file.toPath()),
 StandardCharsets.UTF_8
 );
 }
 }
 } catch (Exception e) {
 result = "读取崩溃日志失败";
 handler.post(() -> Log.e(TAG, "读取崩溃日志失败", e));
 }
 handler.post(() -> listener.onLogRead(result));
 });
 }

 public interface OnLogReadListener {
 void onLogRead(String log);
 }
}
