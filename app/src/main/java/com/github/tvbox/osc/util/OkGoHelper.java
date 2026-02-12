package com.github.tvbox.osc.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader;
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000;      // 默认的超时时间

    // 内置doh json
    private static final String dnsConfigJson = "["
            + "{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},"
            + "{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},"
            + "{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}"
            + "]";

    static OkHttpClient ItvClient = null;

    // 保持类型为 DnsOverHttps
    public static DnsOverHttps dnsOverHttps = null;

    public static ArrayList<String> dnsHttpsList = new ArrayList<>();
    public static boolean is_doh = false;
    public static Map<String, String> myHosts = null;

    public static String getDohUrl(int type) {
        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) json = dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (type >= 1 && type <= dnsHttpsList.size()) {
            JsonObject dnsConfig = jsonArray.get(type - 1).getAsJsonObject();
            if (dnsConfig.has("url")) {
                return dnsConfig.get("url").getAsString();
            } else {
                return "";
            }
        }
        return "";
    }

    public static void setDnsList() {
        dnsHttpsList.clear();
        String json = Hawk.get(HawkConfig.DOH_JSON, "");
        if (json.isEmpty()) json = dnsConfigJson;
        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
        dnsHttpsList.add("默认");
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
            String name = dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown Name";
            dnsHttpsList.add(name);
        }
        if (Hawk.get(HawkConfig.DOH_URL, 0) + 1 > dnsHttpsList.size()) Hawk.put(HawkConfig.DOH_URL, 0);
    }

    private static List<InetAddress> DohIps(JsonArray ips) {
        List<InetAddress> inetAddresses = new ArrayList<>();
        if (ips != null) {
            for (int j = 0; j < ips.size(); j++) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ips.get(j).getAsString());
                    inetAddresses.add(inetAddress);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return inetAddresses;
    }

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
                if (dohSelector == i + 1) {
                    ips = dnsConfig.has("ips") ? dnsConfig.getAsJsonArray("ips") : null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

        // 初始化 dnsOverHttps 为 null
        dnsOverHttps = null;

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
                dnsOverHttps = dnsBuilder.build(); // 现在 dnsOverHttps 是 DnsOverHttps 类型
            } catch (Exception e) {
                e.printStackTrace();
                // 如果创建 DnsOverHttps 失败，保持 dnsOverHttps 为 null
                dnsOverHttps = null;
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
            if (myHosts == null) {
                myHosts = ApiConfig.get().getMyHost();
            }

            if (myHosts != null && !myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                String mappedHost = myHosts.get(hostname);
                if (mappedHost != null) {
                    hostname = mappedHost;
                }
            }

            assert hostname != null;
            if (hostname.isEmpty()) {
                throw new UnknownHostException("Hostname is empty");
            }

            if (isValidIpAddress(hostname)) {
                return Collections.singletonList(InetAddress.getByName(hostname));
            } else {
                if (dnsOverHttps != null) {
                    try {
                        return dnsOverHttps.lookup(hostname);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Dns.SYSTEM.lookup(hostname);
                    }
                } else {
                    return Dns.SYSTEM.lookup(hostname);
                }
            }
        }

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
                    continue;
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

        builder.addInterceptor(loggingInterceptor);

        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);

        builder.dns(dnsOverHttps); // 现在 dnsOverHttps 是 DnsOverHttps 类型
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
            final X509TrustManager trustAllCert = new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

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

    // 确保 initExoOkHttpClient 使用正确的 Dns 类型
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

        // **关键修改：根据 dnsOverHttps 是否为 null 来决定使用哪个 Dns 实现**
        if (dnsOverHttps == null) {
            // 使用系统默认 DNS
            builder.dns(Dns.SYSTEM);
        } else {
            // 使用 dnsOverHttps 作为 Dns 实现
            builder.dns(dnsOverHttps);
        }

        ItvClient = builder.build();

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient);
    }
}
