package com.github.tvbox.osc.util;

import android.os.Handler;
import android.view.View;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
 //xuameng防动画菜单打断遥控2秒
public class FastClickCheckUtilxu {
    /**
     * 相同视图点击必须间隔1s才能有效
     *
     * @param view 目标视图
     */
    public static void check(View view) {
        check(view, 2000);
    }

    /**
     * 设置间隔点击规则，配置间隔点击时长
     *
     * @param view  目标视图
     * @param mills 点击间隔时间（毫秒）
     */
    public static void check(final View view, int mills) {
        view.setClickable(false);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                view.setClickable(true);
            }
        }, mills);
    }
}
