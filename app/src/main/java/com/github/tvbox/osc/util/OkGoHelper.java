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
    private static final String dnsConfigJson = "["
            + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
            + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
            + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
            + "]";

    // 内置默认DNS配置列表
    private static final JsonArray defaultDnsConfigs;
    static {
        // 静态初始化，避免多次解析JSON
        try {
            defaultDnsConfigs = JsonParser.parseString(dnsConfigJson).getAsJsonArray();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果解析失败，创建一个空的JsonArray
            defaultDnsConfigs = new JsonArray();
        }
    }
    
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

    public static String getDohUrl(int type) {  //xuameng新增
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        if(json.isEmpty())json=dnsConfigJson;
        JsonArray jsonArray;
        try {
            jsonArray = JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            e.printStackTrace();
            jsonArray = JsonParser.parseString(dnsConfigJson).getAsJsonArray();
        }
        if (type >= 1 && type <= jsonArray.size()) {
            JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
            if (dnsConfig.has("url")) {     //XUAMENG修复DNS URL为空问题
                return dnsConfig.get("url").getAsString();    // xuameng获取对应的 URL
            } else {
                return ""; // 或返回默认DNS地址如 "https://1.1.1.1/dns-query"
            }
        }
        return ""; //xuameng新增完
    }

    public static void setDnsList() {  //xuameng新增
        // 清空列表
        dnsHttpsList.clear();
        // 添加默认选项
        dnsHttpsList.add("默认");
        
        String json=Hawk.get(HawkConfig.DOH_JSON,"");
        JsonArray jsonArray;
        try {
            jsonArray = JsonParser.parseString(json).getAsJsonArray();
            // 如果获取到的配置为空数组，使用默认配置
            if(jsonArray.size() == 0) {
                jsonArray = defaultDnsConfigs;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 如果解析失败，使用默认配置
            jsonArray = defaultDnsConfigs;
        }
        
        // 将默认的DNS配置添加到前面
        for (int i = 0; i < defaultDnsConfigs.size(); i++) {
            JsonObject dnsConfig = defaultDnsConfigs.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            if (!dnsHttpsList.contains(name)) { // 避免重复添加
                dnsHttpsList.add(name);
            }
        }
        
        // 添加用户自定义的DNS配置
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            if (!dnsHttpsList.contains(name)) { // 避免重复添加
                dnsHttpsList.add(name);
            }
        }
        
        if(Hawk.get(HawkConfig.DOH_URL, 0)+1>dnsHttpsList.size())Hawk.put(HawkConfig.DOH_URL, 0);
    }

    private static List<InetAddress> DohIps(JsonArray ips) {
        List<InetAddress> inetAddresses = new ArrayList<>();
        if (ips != null) {
            for (int j = 0; j < ips.size(); j++) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ips.get(j).getAsString());
                    inetAddresses.add(inetAddress);  // xuameng添加到 List 中
                } catch (Exception e) {
                    e.printStackTrace();  // xuameng处理无效的 IP 字符串
                }
            }
        }
        return inetAddresses;
    }  //xuameng新增完

    static void initDnsOverHttps() {   //xuameng新增
        Integer dohSelector = Hawk.get(HawkConfig.DOH_URL, 0);
        JsonArray ips = null;
        try {
            // 清空列表
            dnsHttpsList.clear();
            // 添加默认选项
            dnsHttpsList.add("默认");
            
            String json = Hawk.get(HawkConfig.DOH_JSON,"");
            JsonArray jsonArray;
            try {
                jsonArray = JsonParser.parseString(json).getAsJsonArray();
                // 如果获取到的配置为空数组，使用默认配置
                if(jsonArray.size() == 0) {
                    jsonArray = defaultDnsConfigs;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 如果解析失败，使用默认配置
                jsonArray = defaultDnsConfigs;
            }
            
            // 将默认的DNS配置添加到前面
            for (int i = 0; i < defaultDnsConfigs.size(); i++) {
                JsonObject dnsConfig = defaultDnsConfigs.get(i).getAsJsonObject();
                String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
                if (!dnsHttpsList.contains(name)) { // 避免重复添加
                    dnsHttpsList.add(name);
                }
            }
            
            // 添加用户自定义的DNS配置
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
                String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
                if (!dnsHttpsList.contains(name)) { // 避免重复添加
                    dnsHttpsList.add(name);
                }
            }
            
            // 计算实际的索引位置，考虑默认选项和默认DNS配置
            int actualIndex = dohSelector;
            if(actualIndex >= dnsHttpsList.size()) {
                Hawk.put(HawkConfig.DOH_URL, 0);
                actualIndex = 0;
            }
            
            // 根据实际的索引获取对应的ips
            if(actualIndex > 0) { // 0是"默认"选项，不需要获取ips
                int customIndex = actualIndex - 1; // 减去"默认"选项
                if(customIndex < jsonArray.size()) {
                    JsonObject dnsConfig = jsonArray.get(customIndex).getAsJsonObject();
                    ips = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 100 * 1024 * 1024));   //xuameng新增完
        OkHttpClient dohClient = builder.build();
        String dohUrl = getDohUrl(dohSelector);
        if (!dohUrl.isEmpty()) is_doh = true;   //xuameng新增
//        dnsOverHttps = new DnsOverHttps.Builder()
//                .client(dohClient)
//                .url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl))
//                .build();
        DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder();
        dnsBuilder.client(dohClient);
        dnsBuilder.url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl));
        if (is_doh && ips!=null){
            List<InetAddress> IPS=DohIps(ips);
            dnsOverHttps = dnsBuilder.bootstrapDnsHosts(IPS).build();
        }else {
            dnsOverHttps = dnsBuilder.build();
        }
    }

    // 自定义 DNS 解析器
    static class CustomDns implements Dns {
        private final String excludeIps = "2409:8087:6c02:14:100::14,2409:8087:6c02:14:100::18,39.134.108.253,39.134.108.245";
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            // 修复：先初始化myHosts，再进行空值和空map检查
            if (myHosts == null) {
                try {
                    myHosts = ApiConfig.get().getMyHost();
                } catch (Exception e) {
                    e.printStackTrace();
                    myHosts = new ConcurrentHashMap<>(); // 初始化为空map防止后续出错
                }
            }
            
            // xuameng修复空指针异常：添加 null 检查
            if(myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                hostname = myHosts.get(hostname);
            }
            
            if (hostname == null) {
                throw new UnknownHostException("Hostname is null");
            }
            
            if (isValidIpAddress(hostname)) {
                return Collections.singletonList(InetAddress.getByName(hostname));
            } else {
                // xuameng修复：如果dnsOverHttps未初始化，则使用系统默认DNS
                if (dnsOverHttps != null) {
                    try {
                        return dnsOverHttps.lookup(hostname);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 如果DOH查询失败，回退到系统默认DNS解析
                        return Arrays.asList(InetAddress.getAllByName(hostname));
                    }
                } else {
                    // xuameng如果DOH未初始化，回退到系统默认DNS解析
                    return Arrays.asList(InetAddress.getAllByName(hostname));
                }
            }
        }

        private List<InetAddress> getAllByName(String host) {
            try {
                // xuameng获取所有与主机名关联的 IP 地址
                InetAddress[] allAddresses = InetAddress.getAllByName(host);
                if(excludeIps.isEmpty())return Arrays.asList(allAddresses);
                // xuameng创建一个列表用于存储有效的 IP 地址
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
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        //xuameng简单判断减少开销
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

        builder.dns(new CustomDns()); // 使用自定义DNS解析器而不是dnsOverHttps
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
