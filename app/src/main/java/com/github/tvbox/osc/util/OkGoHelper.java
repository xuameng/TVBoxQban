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
    // 确保 dnsOverHttps 已经初始化
    if (dnsOverHttps == null) {
        dnsOverHttps = Dns.SYSTEM;
    }
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

    public static String getDohUrl(int type) {  //xuameng新增
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        if(json.isEmpty())json=dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (type >= 1 && type < dnsHttpsList.size()) {
            JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
            if (dnsConfig.has("url")) {     //XUAMENG修复DNS URL为空问题
                return dnsConfig.get("url").getAsString();    // 获取对应的 URL
            } else {
                return ""; // 或返回默认DNS地址如 "https://1.1.1.1/dns-query"
            }
        }
        return ""; //xuameng新增完
    }

    public static void setDnsList() {  //xuameng新增
        dnsHttpsList.clear();
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        if(json.isEmpty())json=dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        dnsHttpsList.add("默认");
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            dnsHttpsList.add(name);
        }
        if(Hawk.get(HawkConfig.DOH_URL, 0)+1>dnsHttpsList.size())Hawk.put(HawkConfig.DOH_URL, 0);

    }

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
    Integer dohSelector = Hawk.get(HawkConfig.DOH_URL, 0);
    JsonArray ips = null;
    
    try {
        dnsHttpsList.clear();
        dnsHttpsList.add("默认");
        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) {
            json = dnsConfigJson;
        }
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        
        if (dohSelector > 0 && dohSelector >= jsonArray.size()) {
            Hawk.put(HawkConfig.DOH_URL, 0);
            dohSelector = 0;
        }
        
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            dnsHttpsList.add(name);
            if (dohSelector == i + 1) { // 注意：dohSelector 从1开始，i从0开始
                ips = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
        // 确保 dnsHttpsList 至少包含默认值
        if (dnsHttpsList.isEmpty()) {
            dnsHttpsList.add("默认");
        }
    }

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
    
    // 确保缓存目录存在
    File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache");
    if (!cacheDir.exists()) {
        cacheDir.mkdirs();
    }
    builder.cache(new Cache(cacheDir, 100 * 1024 * 1024));
    
    OkHttpClient dohClient = builder.build();
    String dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0));
    
    // 修复6: 确保 dnsOverHttps 不为 null
    dnsOverHttps = Dns.SYSTEM; // 默认使用系统DNS
    
    if (!dohUrl.isEmpty()) {
        is_doh = true;
        try {
            DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder();
            dnsBuilder.client(dohClient);
            dnsBuilder.url(HttpUrl.get(dohUrl));
            
            if (ips != null) {
                List<InetAddress> IPS = DohIps(ips);
                if (!IPS.isEmpty()) {
                    dnsBuilder.bootstrapDnsHosts(IPS);
                }
            }
            dnsOverHttps = dnsBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果创建 DnsOverHttps 失败，保持使用系统DNS
            dnsOverHttps = Dns.SYSTEM;
        }
    }
}
    // 自定义 DNS 解析器
static class CustomDns implements Dns {
    private ConcurrentHashMap<String, List<InetAddress>> map;
    private final String excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245";
    
    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        // 修复1: 检查 myHosts 是否为 null
        if (myHosts == null) {
            myHosts = ApiConfig.get().getMyHost(); // 确保只获取一次减少消耗
        }
        
        // 修复2: 检查 myHosts 是否为 null 或空
        if (myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
            String mappedHost = myHosts.get(hostname);
            if (mappedHost != null) {
                hostname = mappedHost;
            }
        }
        
        // 修复3: 检查 hostname 是否为 null
        assert hostname != null;
        if (hostname.isEmpty()) {
            throw new UnknownHostException("Hostname is empty");
        }
        
        if (isValidIpAddress(hostname)) {
            return Collections.singletonList(InetAddress.getByName(hostname));
        } else {
            // 修复4: 检查 dnsOverHttps 是否为 null
            if (dnsOverHttps != null) {
                try {
                    return dnsOverHttps.lookup(hostname);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 如果 dnsOverHttps 查询失败，回退到系统 DNS
                    return Dns.SYSTEM.lookup(hostname);
                }
            } else {
                // 如果 dnsOverHttps 为 null，使用系统 DNS
                return Dns.SYSTEM.lookup(hostname);
            }
        }
    }

    // 修复5: 添加 mapHosts 方法的空值检查
    public synchronized void mapHosts(Map<String, String> hosts) throws UnknownHostException {
        if (hosts == null) {
            map = new ConcurrentHashMap<>();
            return;
        }
        
        map = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue; // 跳过 null 键值对
            }
            
            if (isValidIpAddress(value)) {
                try {
                    map.put(key, Collections.singletonList(InetAddress.getByName(value)));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            } else {
                List<InetAddress> addresses = getAllByName(value);
                if (!addresses.isEmpty()) {
                    map.put(key, addresses);
                }
            }
        }
    }

    // 其他方法保持不变，但建议也添加空值检查
    private List<InetAddress> getAllByName(String host) {
        if (host == null || host.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            InetAddress[] allAddresses = InetAddress.getAllByName(host);
            if (excludeIps == null || excludeIps.isEmpty()) {
                return Arrays.asList(allAddresses);
            }
            
            List<InetAddress> validAddresses = new ArrayList<>();
            Set<String> excludeIpsSet = new HashSet<>();
            for (String ip : excludeIps.split(",")) {
                if (ip != null && !ip.trim().isEmpty()) {
                    excludeIpsSet.add(ip.trim());
                }
            }
            
            for (InetAddress address : allAddresses) {
                if (address != null && !excludeIpsSet.contains(address.getHostAddress())) {
                    validAddresses.add(address);
                }
            }
            return validAddresses;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 简单判断减少开销
    private boolean isValidIpAddress(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (str.indexOf('.') > 0) return isValidIPv4(str);
        return str.indexOf(':') > 0;
    }

    private boolean isValidIPv4(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String[] parts = str.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                return false;
            }
            try {
                Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}







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
