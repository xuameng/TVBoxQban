package com.github.tvbox.osc.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;  //xuameng新增

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader;
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat;
import com.google.gson.JsonArray;  //xuameng新增
import com.google.gson.JsonObject; //xuameng新增
import com.google.gson.JsonParser; //xuameng新增
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.net.InetAddress;  //xuameng新增
import java.net.UnknownHostException;  //xuameng新增
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Dns;  //xuameng新增
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

import java.util.concurrent.ExecutorService; //xuameng新增
import java.util.concurrent.Executors; //xuameng新增
import java.util.concurrent.Future; //xuameng新增
import java.util.concurrent.TimeUnit; //xuameng新增
import java.util.concurrent.TimeoutException; //xuameng新增
import java.util.concurrent.ExecutionException; //xuameng新增
import android.util.Log;


public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000;      //默认的超时时间

    // 内置doh json
    private static final String dnsConfigJson = "["   //xuameng新增
            + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
            + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
            + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
            + "]";
    static OkHttpClient ItvClient = null;   //xuameng新增完
    static void initExoOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);

        builder.retryOnConnectionFailure(true);
        builder.followRedirects(true);
        builder.followSslRedirects(true);


        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

//        builder.dns(dnsOverHttps);
        builder.dns(new CustomDns());  //xuameng新增
        ItvClient=builder.build();

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient); //xuameng新增完
    }

    public static DnsOverHttps dnsOverHttps = null;

    public static ArrayList<String> dnsHttpsList = new ArrayList<>();

    public static boolean is_doh = false;  //xuameng新增
    public static Map<String, String> myHosts = null;  //xuameng新增

public static String getDohUrl(int type) {
    if (type < 0 || type >= dnsHttpsList.size()) return "";
    
    String json = Hawk.get(HawkConfig.DOH_JSON, "");
    if (json.isEmpty()) json = DEFAULT_DNS_CONFIG_JSON;
    
    JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
    if (type == 0) return ""; // 类型0对应“默认”，返回空URL
    
    if (type - 1 < jsonArray.size()) {
        JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
        return dnsConfig.has("url") ? dnsConfig.get("url").getAsString() : "";
    }
    return "";
}


public static void setDnsList() {
    dnsHttpsList.clear();
    String json = Hawk.get(HawkConfig.DOH_JSON, "");
    
    // 修复验证逻辑：允许"url"字段为空（用于"默认"项）
    if (json.isEmpty() || !isValidDnsConfigJson(json)) {
        json = DEFAULT_DNS_CONFIG_JSON;
        Hawk.put(HawkConfig.DOH_JSON, json);
    }
    
    JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
    dnsHttpsList.add("默认"); // 只在此处添加一次
    
    // 跳过JSON中可能存在的"默认"项，避免重复
    for (int i = 0; i < jsonArray.size(); i++) {
        JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
        String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown";
        if (!"默认".equals(name)) {
            dnsHttpsList.add(name);
        }
    }
    
    // 修正索引越界检查
    int currentDohUrl = Hawk.get(HawkConfig.DOH_URL, 0);
    if (currentDohUrl >= dnsHttpsList.size()) {
        Hawk.put(HawkConfig.DOH_URL, 0);
    }
}

// 放宽验证：允许"url"字段为空（用于"默认"项）
private static boolean isValidDnsConfigJson(String json) {
    try {
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject obj = jsonArray.get(i).getAsJsonObject();
            if (!obj.has("name")) {
                return false; // 必须包含"name"
            }
            // "url"字段可选，允许为空
        }
        return true;
    } catch (Exception e) {
        return false;
    }
}


// 定义默认的DNS配置JSON
private static final String DEFAULT_DNS_CONFIG_JSON = "["
        + "{\"name\": \"默认\", \"url\": \"\"},"
        + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
        + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
        + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
        + "]";

    private static List<InetAddress> DohIps(JsonArray ips) {
        List<InetAddress> inetAddresses = new ArrayList<>();
        if (ips != null) {
            for (int j = 0; j < ips.size(); j++) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ips.get(j).getAsString());
                    inetAddresses.add(inetAddress);  // 添加到 List 中
                } catch (Exception e) {
                    e.printStackTrace();  // 处理无效的 IP 字符串
                }
            }
        }
        return inetAddresses;
    }  //xuameng新增完

static void initDnsOverHttps() {
    synchronized (dnsHttpsList) {
        dnsHttpsList.clear();
        setDnsList(); // 统一初始化列表

        Integer dohSelector = Hawk.get(HawkConfig.DOH_URL, 0);
        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) json = DEFAULT_DNS_CONFIG_JSON;

        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (dohSelector >= jsonArray.size()) {
            Hawk.put(HawkConfig.DOH_URL, 0);
            dohSelector = 0;
        }

        JsonArray ipsArray = null; // 用于存储 "ips" 字段，作用域保持在 synchronized 块内

        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            if (dohSelector == i) {
                ipsArray = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;
                break;
            }
        }

        // 现在 ipsArray 在这个 synchronized 块内是可以访问的

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 100 * 1024 * 1024));
        OkHttpClient dohClient = builder.build();
        String dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0));
        if (!dohUrl.isEmpty()) is_doh = true;

        // 现在 ipsArray 在作用域内，可以安全使用
        List<InetAddress> IPS = null;
        if (is_doh && ipsArray != null) {
            IPS = DohIps(ipsArray);  // 传入的是 JsonArray
        }

        DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder();
        dnsBuilder.client(dohClient);
        dnsBuilder.url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl));

        if (is_doh && IPS != null) {
            dnsOverHttps = dnsBuilder.bootstrapDnsHosts(IPS).build();
        } else {
            dnsOverHttps = dnsBuilder.build();
        }
    }
}

    // 自定义 DNS 解析器
    static class CustomDns implements Dns {
        private  ConcurrentHashMap<String, List<InetAddress>> map;
        private final String excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245";
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            if (myHosts == null) {
                myHosts = ApiConfig.get().getMyHost(); // 确保只获取一次减少消耗
            }
    
            // 如果myHosts不为null且非空，则进行主机名替换
            if (myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                hostname = myHosts.get(hostname);
            }
    
            assert hostname != null;
    
            if (isValidIpAddress(hostname)) {
                return Collections.singletonList(InetAddress.getByName(hostname));
            } else {
                // 如果dnsOverHttps为null，回退到系统默认DNS
                if (dnsOverHttps != null) {     //xuameng 获取失败为空用系统默认DNS
                    // 添加超时控制的DoH查询
                    return lookupWithTimeout(hostname);
                } else {
                    // 使用系统默认DNS
                    return Dns.SYSTEM.lookup(hostname);
                }
            }
        }

        private List<InetAddress> lookupWithTimeout(String hostname) throws UnknownHostException {   //XUAMENG超时方法放在卡死
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<List<InetAddress>> future = executor.submit(() -> dnsOverHttps.lookup(hostname));
    
            try {
                // 设置5秒超时，可根据需要调整
                return future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // 取消任务
                LOG.e("DNS查询超时，使用系统DNS");
                return Dns.SYSTEM.lookup(hostname);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UnknownHostException) {
                    throw (UnknownHostException) cause;
                }
                LOG.e("DNS查询异常: " + e.getMessage());
                return Dns.SYSTEM.lookup(hostname);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.e("DNS查询被中断");
                return Dns.SYSTEM.lookup(hostname);
            } finally {
                executor.shutdown();
            }
        }

        public synchronized void mapHosts(Map<String,String> hosts) throws UnknownHostException {   //xuameng新增
            map=new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : hosts.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if(isValidIpAddress(value)){
                    map.put(key,Collections.singletonList(InetAddress.getByName(value)));
                }else {
                    map.put(key,getAllByName(value));
                }
            }
        }

        private List<InetAddress> getAllByName(String host) {
            try {
                // 获取所有与主机名关联的 IP 地址
                InetAddress[] allAddresses = InetAddress.getAllByName(host);
                if(excludeIps.isEmpty())return Arrays.asList(allAddresses);
                // 创建一个列表用于存储有效的 IP 地址
                List<InetAddress> validAddresses = new ArrayList<>();
                Set<String> excludeIpsSet = new HashSet<>();
                for (String ip : excludeIps.split(",")) {
                    excludeIpsSet.add(ip.trim());  // 添加到集合，去除多余的空格
                }
                for (InetAddress address : allAddresses) {
                    if (!excludeIpsSet.contains(address.getHostAddress())) {
                        validAddresses.add(address);
                    }
                }
                return validAddresses;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        //简单判断减少开销
        private boolean isValidIpAddress(String str) {
            if (str.indexOf('.') > 0) return isValidIPv4(str);
            return str.indexOf(':') > 0;
        }

        private boolean isValidIPv4(String str) {
            String[] parts = str.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                try {
                    Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
    }  //xuameng新增完

    static OkHttpClient defaultClient = null;
    static OkHttpClient noRedirectClient = null;

    public static OkHttpClient getDefaultClient() {
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        return noRedirectClient;
    }

    public static void init() {
        initDnsOverHttps();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkGo");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }

        //builder.retryOnConnectionFailure(false);

        builder.addInterceptor(loggingInterceptor);

        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);

        builder.dns(dnsOverHttps);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        HttpHeaders.setUserAgent(Version.userAgent());

        OkHttpClient okHttpClient = builder.build();
        OkGo.getInstance().setOkHttpClient(okHttpClient);

        defaultClient = okHttpClient;

        builder.followRedirects(false);
        builder.followSslRedirects(false);
        noRedirectClient = builder.build();

        initExoOkHttpClient();
        initPicasso(okHttpClient);
    }

    static void initPicasso(OkHttpClient client) {
        client.dispatcher().setMaxRequestsPerHost(10);
        MyOkhttpDownLoader downloader = new MyOkhttpDownLoader(client);
        Picasso picasso = new Picasso.Builder(App.getInstance())
                .downloader(downloader)
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .build();
        Picasso.setSingletonInstance(picasso);
    }

    private static synchronized void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            // 自定义一个信任所有证书的TrustManager，添加SSLSocketFactory的时候要用到
            final X509TrustManager trustAllCert =
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    };
            final SSLSocketFactory sslSocketFactory = new SSLSocketFactoryCompat(trustAllCert);
            builder.sslSocketFactory(sslSocketFactory, trustAllCert);
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
