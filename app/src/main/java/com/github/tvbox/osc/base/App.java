package com.github.tvbox.osc.base;

import android.app.Activity;
import android.os.SystemProperties; // 新增：用于读取系统属性
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.callback.ConfigCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.whl.quickjs.android.QuickJSLoader;
import com.github.catvod.crawler.JsLoader;
import com.kingja.loadsir.core.LoadSir;
import com.github.tvbox.osc.crash.CrashHandler;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;

import java.io.File;
import android.content.Context;
import android.widget.Toast;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author xuameng
 * @date :2026/04/14
 * @description: 增加崩溃 配置中心等
 */
public class App extends MultiDexApplication {
    private static App instance;
    private static Toast mToast;
    private static P2PClass p;
    public static String burl;
    private static String dashData;

    // 用于存储设备名称
    public static String deviceName = "TVBox";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // 全局崩溃捕获
        CrashHandler.getInstance().init(this);
        
        // 初始化配置
        initParams();
        
        // OKGo & Epg
        OkGoHelper.init();
        EpgUtil.init();
        
        // 初始化Web服务器
        ControlManager.init(this);
        
        // 初始化数据库
        AppDataManager.init();
        
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .addCallback(new ConfigCallback())
                .commit();
                
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
                
        PlayerHelper.init();
        QuickJSLoader.init();
        
        // 清理缓存逻辑
        FileUtils.cleanPlayerCache();
        String cachePath = FileUtils.getCachePath();
        File cacheDir = new File(cachePath);
        if (cacheDir.exists()) {
            new Thread(() -> {
                try {
                    FileUtils.cleanDirectory(cacheDir);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // --- 获取设备名称逻辑 ---
        new Thread(() -> {
            try {
                // 优先获取市场名称 (如 "Redmi Box")，如果没有则使用默认型号 (如 "24069RA21C")
                String name = getMarketName();
                if (name == null || name.isEmpty()) {
                    name = android.os.Build.MODEL;
                }
                deviceName = name;
          //      LOG.i("App", "设备名称获取成功: " + deviceName);
            } catch (Exception e) {
           //     LOG.e("获取设备名称失败: " + e.getMessage());
            }
        }).start();
        // --- 代码结束 ---
    }

    /**
     * 获取设备市场名称
     * 优先读取 ro.product.marketname，解决 Build.MODEL 返回认证型号的问题
     */
    private String getMarketName() {
        try {
            // 尝试读取 ro.product.marketname 属性
            String marketName = SystemProperties.get("ro.product.marketname", "");
            if (!marketName.isEmpty()) {
                return marketName.trim();
            }
            // 某些设备可能在 ro.product.name 中存储了可读名称
            String productName = SystemProperties.get("ro.product.name", "");
            if (!productName.isEmpty()) {
                 // 简单过滤，避免返回奇怪的代号
                 return productName.trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        putDefault(HawkConfig.PLAY_TYPE, 1);
        putDefault(HawkConfig.HOME_REC, 0);
        putDefault(HawkConfig.IJK_CODEC, "硬解码");
        putDefault(HawkConfig.HISTORY_NUM, 3);
        putDefault(HawkConfig.SHOW_PREVIEW, true);
        putDefault(HawkConfig.SEARCH_VIEW, 1);
        putDefault(HawkConfig.PLAY_SCALE, 3);
        putDefault(HawkConfig.DOH_URL, 0);
        putDefault(HawkConfig.FAST_SEARCH_MODE, true);
        putDefault(HawkConfig.IJK_CACHE_PLAY, false);
        putDefault(HawkConfig.HOME_REC_STYLE, true);
        putDefault(HawkConfig.HOME_DEFAULT_SHOW, false);
        putDefault(HawkConfig.M3U8_PURIFY, false);
        putDefault(HawkConfig.PLAY_RENDER, 0);
        putDefault(HawkConfig.LIVE_MUSIC_ANIMATION, false);
        putDefault(HawkConfig.VOD_MUSIC_ANIMATION, false);
        putDefault(HawkConfig.EXO_PLAY_SELECTCODE, 0);
        putDefault(HawkConfig.EXO_PLAYER_DECODE, false);
        putDefault(HawkConfig.VOD_SWITCHDECODE, false);
        putDefault(HawkConfig.VOD_SWITCHPLAYER, true);
    }

    public static App getInstance() {
        return instance;
    }

    private void putDefault(String key, Object value) {
        if (!Hawk.contains(key)) {
            Hawk.put(key, value);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
    }

    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                p = new P2PClass(instance.getCacheDir().getAbsolutePath());
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }

    public static void showToastShort(Context context, String msg) {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        } 
        mToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public static void showToastLong(Context context, String msg) {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        } 
        mToast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        mToast.show();
    }
	
    public static void HideToast() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }
}
