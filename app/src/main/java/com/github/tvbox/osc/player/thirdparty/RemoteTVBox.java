package com.github.tvbox.osc.player.thirdparty;

import android.app.Activity;
import android.text.TextUtils;
import com.github.tvbox.osc.base.App; // 1. 确保引入 App 类
import com.github.tvbox.osc.bean.IpScanningVo;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.IpScanning;
import com.orhanobut.hawk.Hawk;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteTVBox {
    public static boolean run(Activity activity, String url, String title, String subtitle, HashMap<String, String> headers) {
        String actionUrl = getAvalibleActionUrl();
        if (TextUtils.isEmpty(actionUrl)) {
            return false;
        }
        try {
            if (headers != null && headers.size() > 0) {
                url = url + "|";
                int idx = 0;
                for (String hk : headers.keySet()) {
                    url += hk + "=" + URLEncoder.encode(headers.get(hk), "UTF-8");
                    if (idx < headers.keySet().size() -1) {
                        url += "&";
                    }
                    idx ++;
                }
            }
            Map<String ,String> params = new HashMap<>();
            params.put("do", "push");
            params.put("url", url);
            post(actionUrl, params, new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String pushResult = response.body().string();
                    if (pushResult.equals("ok")) {
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private static int avalibleFailNum;
    private static int avalibleSuccessNum;
    private static int avalibleIpNum;

    // 2. 修改方法注释和内部逻辑
    public static void searchAvalible(Callback callback) {
        avalibleFailNum = 0;
        avalibleSuccessNum = 0;
        String localIp = RemoteServer.getLocalIPAddress(App.getInstance());
        List<IpScanningVo> searchList = new IpScanning().search(localIp, false);
        avalibleIpNum = searchList.size();
        int port = 9978;
        for(IpScanningVo one : searchList) {
            String ip = one.getIp();
            if (ip.equals(localIp)) {
                avalibleIpNum --;
                continue;
            }
            String actionUrl = "http://" + ip + ":" + port + "/action";
            String viewHost = ip + ":" + port;
            
            // --- 核心修改：获取设备名称 ---
            // 注意：这里我们传入 viewHost 和一个占位符，实际设备名需要在回调里获取
            // 因为设备名是存储在 App 里的静态变量
            try {
                post(actionUrl, null, new okhttp3.Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        avalibleFailNum++;
                        callback.fail(avalibleFailNum == avalibleIpNum, (avalibleSuccessNum + avalibleFailNum) == avalibleIpNum);
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        avalibleSuccessNum ++;
                        String result = response.body().string();
                        if (result.equals("ok")) {
                            // 3. 这里获取 App.deviceName 并传给回调
                            // 如果 App.deviceName 还没加载出来，可以显示 "Unknown" 或者直接显示 IP
                            String deviceName = App.deviceName != null ? App.deviceName : "jvhuiys";
                            callback.found(viewHost, deviceName, (avalibleSuccessNum + avalibleFailNum) == avalibleIpNum);
                        }
                    }
                });
            } catch (Exception e) { }
        }
        return;
    }

    public static String getAvalible() {
        return Hawk.get(HawkConfig.REMOTE_TVBOX, null);
    }

    public static String getAvalibleActionUrl() {
        if (getAvalible() == null) {
            return "";
        }
        return "http://" + getAvalible() + "/action";
    }

    public static void setAvalible(String viewHost) {
        Hawk.put(HawkConfig.REMOTE_TVBOX, viewHost);
    }

    public static void post(String url, Map<String, String> params, okhttp3.Callback callback) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(1000, TimeUnit.MILLISECONDS);
        builder.writeTimeout(1000, TimeUnit.MILLISECONDS);
        builder.connectTimeout(1000, TimeUnit.MILLISECONDS);
        OkHttpClient client = builder.build();
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        if (params != null && params.size() > 0) {
            for(Map.Entry<String, String> entry : params.entrySet()) {
                formBodyBuilder.add(entry.getKey(), entry.getValue());
            }
        }
        FormBody formBody = formBodyBuilder.build();
        client.newCall(new Request.Builder().url(url).post(formBody).build()).enqueue(callback);
    }

    // 4. 修改 Callback 接口，增加 deviceName 参数
    public abstract class Callback {
        // 原来是 public abstract void found(String viewHost, boolean end);
        public abstract void found(String viewHost, String deviceName, boolean end);
        public abstract void fail(boolean all, boolean end);
    }
}
