package com.github.tvbox.osc.util;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import android.content.pm.PackageManager; // xuameng添加导入
import android.content.res.Configuration; // xuameng添加导入
import android.view.InputDevice;         // xuameng添加导入

public class ScreenUtils {

    public static double getSqrt(Activity activity) {
        WindowManager wm = activity.getWindowManager();
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
        double screenInches = Math.sqrt(x + y);// 屏幕尺寸
        return screenInches;
    }

    private static boolean checkScreenLayoutIsTv(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) > Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static boolean checkIsPhone(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public static boolean isTv(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION || (checkScreenLayoutIsTv(context) && !checkIsPhone(context));
    }

    //xuameng 判断TV 盒子
    public static boolean isTvDevice(Context context) {
       if (context == null) return false;
    
        PackageManager pm = context.getPackageManager();
        UiModeManager uiMode = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);

        // 1. 【核心判断】检查 UI 模式 (官方标准)
        if (uiMode != null && uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }

        // 2. 【核心判断】检查系统特性 (涵盖 Google TV, Fire TV, 原生 TV)
        boolean hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean hasTvFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
        boolean isFireTv = pm.hasSystemFeature("amazon.hardware.fire_tv");
    
        if (hasLeanback || hasTvFeature || isFireTv) {
            return true;
        }

        // 3. 【辅助判断】硬件特征组合拳
        // 电视通常没有触摸屏，且没有电话功能
        boolean noTouch = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        boolean noPhone = true;
    
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                noPhone = tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE;
            }
        } catch (Exception e) {
            // 忽略异常
        }

        // 如果既没有触摸屏，也不能打电话，且屏幕较大，大概率是电视盒子
        // 注意：这里不再单纯依赖 screenLayout > LARGE，因为大屏手机也会误判
        // 我们主要依赖“无触摸 + 无电话”这个组合在 TV 盒子上非常典型
        if (noTouch && noPhone) {
            return true;
        }

        // 4. 【输入设备】检测是否主要依赖 DPAD (遥控器)
        // 这是一个很强的动态特征
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev != null && (dev.getSources() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
                // 如果连接了遥控器，且前面判断过不是手机，基本可认定为 TV 环境
                return true;
            }
        }

        return false;
    }

}
