package com.github.tvbox.osc.crash;

import android.content.Context;
import android.content.SharedPreferences;

public class CrashLogUtil {

    private static final String FILE_NAME = "crash_log.txt";
    private static final String SP_NAME = "crash_sp";

    public static void save(Context context, String log) {
        SharedPreferences sp =
                context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString("last_crash", log).apply();
    }

    public static String get(Context context) {
        SharedPreferences sp =
                context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString("last_crash", "Œﬁ±¿¿£»’÷æ");
    }
}