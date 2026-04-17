package com.github.tvbox.osc.base;

import android.app.Activity;
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.callback.ConfigCallback;  //xuameng配置中心
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
import com.github.tvbox.osc.crash.CrashHandler;  //xuameng 崩溃信息
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import java.io.File;   //xuameng清缓存
import android.content.Context;   //xuameng  Toast
import android.widget.Toast;  //xuameng  Toast

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author xuameng
 * @date :2026/04/14
 * @description:  增加崩溃  配置中心等
 */
public class App extends MultiDexApplication {
    private static App instance;
    private static Toast mToast;   //xuameng  Toast
    private static P2PClass p;
    public static String burl;
    private static String dashData;
    // xuameng存储最终的设备名称
    public static String deviceName = "jvhuiys";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        //xuameng 全局崩溃捕获（一定要放在最前面）
        CrashHandler.getInstance().init(this);  //xuameng 崩溃信息
        initParams();
        // OKGo
        OkGoHelper.init(); //台标获取
        EpgUtil.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .addCallback(new ConfigCallback())  //xuameng配置中心
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        QuickJSLoader.init();
        FileUtils.cleanPlayerCache();        //xuameng
		String cachePath = FileUtils.getCachePath();       //xuameng清空缓存
			File cacheDir = new File(cachePath);
			if (!cacheDir.exists()) return;
			new Thread(() -> {
				try {
					FileUtils.cleanDirectory(cacheDir);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}).start();
					
        // --- xuameng获取设备名称逻辑 (智能去重版) ---
        new Thread(() -> {
            try {
                // 1. 获取品牌 (例如: Redmi)
                String brand = android.os.Build.BRAND;
                
                // 2. 获取产品代号 (例如: redmi_box 或 dopods)
                String product = android.os.Build.PRODUCT;
                
                String displayName = product;

                // 3. 智能处理：如果 product 包含 brand，则去掉 brand
                if (brand != null && !brand.isEmpty() && product != null && !product.isEmpty()) {
                    // 使用正则忽略大小写替换，把品牌名替换为空
                    // 例如: "redmi_box" 去掉 "Redmi" -> 变成 "_box"
                    displayName = product.replaceAll("(?i)" + brand, "");
                    
                    // 去掉首尾可能残留的下划线或空格
                    displayName = displayName.replaceAll("^[_\\s]+|[_\\s]+$", "");
                    
                    // 如果替换后为空（说明 product 和 brand 完全一样），则回退到只显示 brand
                    if (displayName.isEmpty()) {
                        displayName = brand;
                    }
                }
                
                deviceName = displayName;
                LOG.e("App", "设备名称获取成功: " + deviceName);
            } catch (Exception e) {
                // 极端情况兜底
                deviceName = "jvhuiys";
                LOG.e("获取设备名称失败", e);
            }
        }).start();
        // --- xuameng代码结束 ---
    }

    private void initParams() {      //xuameng系统默认设置
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);      //xuameng调试模式  默认关闭   2222开启
        putDefault(HawkConfig.PLAY_TYPE, 1);         //播放器: 0=系统, 1=IJK, 2=Exo
        putDefault(HawkConfig.HOME_REC, 0);          // Home Rec 0=豆瓣, 1=推荐, 2=历史
        putDefault(HawkConfig.IJK_CODEC, "硬解码");  // IJK Render 软解码, 硬解码
        putDefault(HawkConfig.HISTORY_NUM, 3);          //历史记录 0,30,1,50,2,70 3,100
        putDefault(HawkConfig.SHOW_PREVIEW, true);   //窗口预览: true=开启, false=关闭
        putDefault(HawkConfig.SEARCH_VIEW, 1);       //搜索展示: 0=文字列表, 1=缩略图
        putDefault(HawkConfig.PLAY_SCALE, 3);		 //画面缩放: 0=默认, 1=16:9, 2=4:3, 3=填充, 4=原始, 5=裁剪
        putDefault(HawkConfig.DOH_URL, 0);          //安全DNS: 0=关闭, 1=腾讯, 2=阿里, 3=360, 4=Google, 5=AdGuard, 6=Quad9
        putDefault(HawkConfig.FAST_SEARCH_MODE, true);   //xuameng 聚合搜索  默认开启
        putDefault(HawkConfig.IJK_CACHE_PLAY, false);    //xuameng IJK缓存  默认关闭
        putDefault(HawkConfig.HOME_REC_STYLE, true);    //xuameng 首页多行  默认开启
        putDefault(HawkConfig.HOME_DEFAULT_SHOW, false);    //xuameng 直进直播  默认关闭
        putDefault(HawkConfig.M3U8_PURIFY, false);    //xuameng 去除广告  默认关闭
        putDefault(HawkConfig.PLAY_RENDER, 0);       //xuameng 渲染方式 0 TextureView 1 SurfaceView
        putDefault(HawkConfig.LIVE_MUSIC_ANIMATION, false);       //xuameng 直播音乐动画  默认关闭
        putDefault(HawkConfig.VOD_MUSIC_ANIMATION, false);       //xuameng 点播音乐动画   默认关闭
        putDefault(HawkConfig.EXO_PLAY_SELECTCODE, 0);       //xuameng exo解码动态选择  默认0为不选择
        putDefault(HawkConfig.EXO_PLAYER_DECODE, false);      //xuameng exo解码方式  false硬解  true软解
        putDefault(HawkConfig.VOD_SWITCHDECODE, false);       //xuameng解码切换  默认关闭
        putDefault(HawkConfig.VOD_SWITCHPLAYER, true);      //xuameng播放器切换  默认开启

    }

    public static App getInstance() {
        return instance;
    }

    private void putDefault(String key, Object value) {	//xuameng系统默认设置方法
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
				// p = new P2PClass(instance.getExternalCacheDir().getAbsolutePath());     
                p = new P2PClass(instance.getCacheDir().getAbsolutePath());  //xuameng修复某些设备不能建立cachedir目录
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

    public static void showToastShort(Context context, String msg) {  //xuameeng showtoast
        if (mToast != null) {
            mToast.cancel();
            mToast = null;  // 释放引用避免内存泄漏
        } 
        mToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public static void showToastLong(Context context, String msg) {  //xuameeng showtoast
        if (mToast != null) {
            mToast.cancel();
            mToast = null;  // 释放引用避免内存泄漏
        } 
        mToast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        mToast.show();
    }
	
    public static void HideToast() {   //xuameeng HideToast
        if (mToast != null) {
            mToast.cancel();
            mToast = null;  // 释放引用避免内存泄漏
        }
    }
}
