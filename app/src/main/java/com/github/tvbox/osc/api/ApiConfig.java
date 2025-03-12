package com.github.tvbox.osc.api;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.JsLoader;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AES;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.M3u8;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    private LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private List<LiveChannelGroup> liveChannelGroupList;
    private List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private Map<String,String> myHosts;
    private List<IJKCode> ijkCodes;
    private String spider = null;
    public String wallpaper = "";
	public String musicwallpaper = "";   //xuameng音乐背景图
	public String warningText = "";   //xuameng版权提示

    private SourceBean emptyHome = new SourceBean();

    private JarLoader jarLoader = new JarLoader();
    private JsLoader jsLoader = new JsLoader();
    private Gson gson;

    private String userAgent = "okhttp/3.15";

    private String requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";

    private String defaultLiveObjString="{\"lives\":[{\"name\":\"txt_m3u\",\"type\":0,\"url\":\"txt_m3u_url\"}]}";
    private ApiConfig() {
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
        gson = new Gson();
        loadDefaultConfig();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) return content;
            Pattern pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if(matcher.find()){
                content=content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            if (content.startsWith("2423")) {
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            }else if (configKey !=null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            }
            else{
                json = content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private static byte[] getImgJar(String body){
        Pattern pattern = Pattern.compile("[A-Za-z0]{8}\\*\\*");
        Matcher matcher = pattern.matcher(body);
        if(matcher.find()){
            body = body.substring(body.indexOf(matcher.group()) + 10);
            return Base64.decode(body, Base64.DEFAULT);
        }
        return "".getBytes();
    }

    private String TempKey = null;
    private String configUrl(String apiUrl){
        String configUrl = "", pk = ";pk;";
        apiUrl=apiUrl.replace("file://", "clan://localhost/");
        if (apiUrl.contains(pk)) {
            String[] a = apiUrl.split(pk);
            TempKey = a[1];
            if (apiUrl.startsWith("clan")){
                configUrl = clanToAddress(a[0]);
            }else if (apiUrl.startsWith("http")){
                configUrl = a[0];
            }else {
                configUrl = "http://" + a[0];
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl);
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + apiUrl;
        } else {
            configUrl = apiUrl;
        }
        return configUrl;
    }
    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "http://xuameng.vicp.net:8082/tvbox/1/xu.json");
        //独立加载直播配置
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        String liveApiConfigUrl=configUrl(liveApiUrl);
        if(!liveApiUrl.isEmpty() && !liveApiUrl.equals(apiUrl)){
            if(liveApiUrl.contains(".txt") || liveApiUrl.contains(".m3u") || liveApiUrl.contains("=txt") || liveApiUrl.contains("=m3u")){
                initLiveSettings();
                defaultLiveObjString = defaultLiveObjString.replace("txt_m3u_url",liveApiConfigUrl);
                parseLiveJson(liveApiUrl,defaultLiveObjString);
            }else {
                File live_cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(liveApiUrl));
                if (useCache && live_cache.exists()) {
                    try {
                        parseLiveJson(liveApiUrl, live_cache);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }else {
                    OkGo.<String>get(liveApiConfigUrl)
                            .headers("User-Agent", userAgent)
                            .headers("Accept", requestAccept)
                            .execute(new AbsCallback<String>() {
                                @Override
                                public void onSuccess(Response<String> response) {
                                    try {
                                        String json = response.body();
                                        parseLiveJson(liveApiUrl, json);
                                        FileUtils.saveCache(live_cache,json);
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                        callback.notice("聚汇影视提示您：解析直播配置失败！");
                                    }
                                }

                                @Override
                                public void onError(Response<String> response) {
                                    super.onError(response);
                                    if (live_cache.exists()) {
                                        try {
                                            parseLiveJson(liveApiUrl, live_cache);
                                            callback.success();
                                            return;
                                        } catch (Throwable th) {
                                            th.printStackTrace();
                                        }
                                    }
                                    callback.notice("聚汇影视提示您：直播配置拉取失败！");
									//Hawk.put(HawkConfig.LIVE_API_URL, "");
									//Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
                                }

                                public String convertResponse(okhttp3.Response response) throws Throwable {
                                    String result = "";
                                    if (response.body() == null) {
                                        result = "";
                                    }else {
                                        result = FindResult(response.body().string(), TempKey);
                                        if (liveApiUrl.startsWith("clan")) {
                                            result = clanContentFix(clanToAddress(liveApiUrl), result);
                                        }
                                        //假相對路徑
                                        result = fixContentPath(liveApiUrl,result);
                                    }
                                    return result;
                                }
                            });
                }
            }
        }

        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                parseJson(apiUrl, cache);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String configUrl=configUrl(apiUrl);
        OkGo.<String>get(configUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String json = response.body();
                            parseJson(apiUrl, json);
                            FileUtils.saveCache(cache,json);
                            callback.success();
                        } catch (Throwable th) {
                            th.printStackTrace();
                            callback.error("聚汇影视提示您：解析配置失败！");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (cache.exists()) {
                            try {
                                parseJson(apiUrl, cache);
                                callback.success();
                                return;
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("聚汇影视提示您：拉取配置失败！\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = FindResult(response.body().string(), TempKey);
                        }

                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result);
                        }
                        //假相對路徑
                        result = fixContentPath(apiUrl,result);
                        return result;
                    }
                });
    }

    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("");
                }
                return;
            }
        }

        boolean isJarInImg = jarUrl.startsWith("img+");
        jarUrl = jarUrl.replace("img+", "");
        OkGo.<File>get(jarUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                if (cache.exists())
                    cache.delete();
                FileOutputStream fos = new FileOutputStream(cache);
                if(isJarInImg) {
                    String respData = response.body().string();
                    byte[] imgJar = getImgJar(respData);
                    fos.write(imgJar);
                } else {
                    fos.write(response.body().bytes());
                }
                fos.flush();
                fos.close();
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                callback.error("");
            }
        });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        System.out.println("从本地缓存加载" + f.getAbsolutePath());
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseJson(apiUrl, sb.toString());
    }

    private void parseJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
		musicwallpaper = DefaultConfig.safeJsonString(infoJson, "musicwallpaper", "");    //xuameng音乐背景图
		warningText = DefaultConfig.safeJsonString(infoJson, "warningText", "");  //xuameng警告
        // 远端站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.get("name").getAsString().trim());
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            if(obj.has("ext") && (obj.get("ext").isJsonObject() || obj.get("ext").isJsonArray())){
                sb.setExt(obj.get("ext").toString());
            }else {
                sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            }
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
            if (firstSite == null)
                firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null)
                setSourceBean(firstSite);
            else
                setSourceBean(sh);
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        parseBeanList.clear();
        if(infoJson.has("parses")){
            JsonArray parses = infoJson.get("parses").getAsJsonArray();
            for (JsonElement opt : parses) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                parseBeanList.add(pb);
            }
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse == null)
                setDefaultParse(parseBeanList.get(0));
        }

        // 直播源
        String live_api_url=Hawk.get(HawkConfig.LIVE_API_URL,"");
        if(live_api_url.isEmpty() || apiUrl.equals(live_api_url)){
			initLiveSettings();
			liveSettingGroupList.clear();
			Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
            LOG.i("echo-load-config_live");
            if(infoJson.has("lives")){
                JsonArray lives_groups=infoJson.get("lives").getAsJsonArray();
				if (lives_groups.size() > 0) {  
                    initLiveSettings();
					int live_group_index=Hawk.get(HawkConfig.LIVE_GROUP_INDEX,0);
					if(live_group_index>lives_groups.size()-1){           //xuameng 重要BUG
						Hawk.put(HawkConfig.LIVE_GROUP_INDEX,0);
						Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
						//加载多源配置
						try {
						ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
						for (int i=0; i< lives_groups.size();i++) {
							JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
							String name = jsonObject.has("name")?jsonObject.get("name").getAsString():"线路"+(i+1);
							LiveSettingItem liveSettingItem = new LiveSettingItem();
							liveSettingItem.setItemIndex(i);
							liveSettingItem.setItemName(name);
							liveSettingItemList.add(liveSettingItem);
						}
						liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
					} catch (Exception e) {
						// 捕获任何可能发生的异常
						e.printStackTrace();
					}
					int live_group_index_xu=Hawk.get(HawkConfig.LIVE_GROUP_INDEX,0);
					JsonObject livesOBJ_xu = lives_groups.get(live_group_index_xu).getAsJsonObject();
					loadLiveApi(livesOBJ_xu);
					}else{
						Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
						//加载多源配置
						try {
							ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
							for (int i=0; i< lives_groups.size();i++) {
							JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
							String name = jsonObject.has("name")?jsonObject.get("name").getAsString():"线路"+(i+1);
							LiveSettingItem liveSettingItem = new LiveSettingItem();
							liveSettingItem.setItemIndex(i);
							liveSettingItem.setItemName(name);
							liveSettingItemList.add(liveSettingItem);
							}
							liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
						} catch (Exception e) {
						// 捕获任何可能发生的异常
							e.printStackTrace();
					}
					JsonObject livesOBJ = lives_groups.get(live_group_index).getAsJsonObject();
					loadLiveApi(livesOBJ);
					}
				}else{
					initLiveSettings();
					liveSettingGroupList.clear();
					Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
				}
			}else{
				initLiveSettings();
				liveSettingGroupList.clear();
				Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
			}

            myHosts = new HashMap<>();
            if (infoJson.has("hosts")) {
                JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
                for (int i = 0; i < hostsArray.size(); i++) {
                    String entry = hostsArray.get(i).getAsString();
                    String[] parts = entry.split("=", 2); // 只分割一次，防止 value 里有 =
                    if (parts.length == 2) {
                        myHosts.put(parts[0], parts[1]);
                    }
                }
            }
        }

        //video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule();
            for(JsonElement oneHostRule : infoJson.getAsJsonArray("rules")) {
                JsonObject obj = (JsonObject) oneHostRule;
                if (obj.has("host")) {
                    String host = obj.get("host").getAsString();
                    if (obj.has("rule")) {
                        JsonArray ruleJsonArr = obj.getAsJsonArray("rule");
                        ArrayList<String> rule = new ArrayList<>();
                        for (JsonElement one : ruleJsonArr) {
                            String oneRule = one.getAsString();
                            rule.add(oneRule);
                        }
                        if (rule.size() > 0) {
                            VideoParseRuler.addHostRule(host, rule);
                        }
                    }
                    if (obj.has("filter")) {
                        JsonArray filterJsonArr = obj.getAsJsonArray("filter");
                        ArrayList<String> filter = new ArrayList<>();
                        for (JsonElement one : filterJsonArr) {
                            String oneFilter = one.getAsString();
                            filter.add(oneFilter);
                        }
                        if (filter.size() > 0) {
                            VideoParseRuler.addHostFilter(host, filter);
                        }
                    }
                }
                if (obj.has("hosts") && obj.has("regex")) {
                    ArrayList<String> rule = new ArrayList<>();
                    ArrayList<String> ads = new ArrayList<>();
                    JsonArray regexArray = obj.getAsJsonArray("regex");
                    for (JsonElement one : regexArray) {
                        String regex = one.getAsString();
                        if (M3u8.isAd(regex)) ads.add(regex);
                        else rule.add(regex);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostRule(host, rule);
                        VideoParseRuler.addHostRegex(host, ads);
                    }
                }
            }
        }

        if (infoJson.has("doh")) {
            String doh_json = infoJson.getAsJsonArray("doh").toString();
            Hawk.put(HawkConfig.DOH_JSON,doh_json);
        }else {
            Hawk.put(HawkConfig.DOH_JSON,"");
        }
        OkGoHelper.setDnsList();
        LOG.i("echo-api-config-----------load");
        //追加的广告拦截
        if(infoJson.has("ads")){
            for (JsonElement host : infoJson.getAsJsonArray("ads")) {
                if(!AdBlocker.hasHost(host.getAsString())){
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
        }
    }

    private void loadDefaultConfig() {
        String defaultIJKADS="{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}";
        JsonObject defaultJson=gson.fromJson(defaultIJKADS, JsonObject.class);
        // 广告地址
        if(AdBlocker.isEmpty()){
            //默认广告拦截
            for (JsonElement host : defaultJson.getAsJsonArray("ads")) {
                AdBlocker.addAdHost(host.getAsString());
            }
        }
        // IJK解码配置
        if(ijkCodes==null){
            ijkCodes = new ArrayList<>();
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
            JsonArray ijkJsonArray = defaultJson.get("ijk").getAsJsonArray();
            for (JsonElement opt : ijkJsonArray) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
            if (!foundOldSelect && ijkCodes.size() > 0) {
                ijkCodes.get(0).selected(true);
            }
        }
        LOG.i("echo-default-config-----------load");
    }
    private void parseLiveJson(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseLiveJson(apiUrl, sb.toString());
    }
    private void parseLiveJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
		initLiveSettings();
		liveSettingGroupList.clear();
		Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
        // 直播源
        if(infoJson.has("lives")){
            JsonArray lives_groups=infoJson.get("lives").getAsJsonArray();
				if (lives_groups.size() > 0) { 
				initLiveSettings();
				int live_group_index=Hawk.get(HawkConfig.LIVE_GROUP_INDEX,0);
					if(live_group_index>lives_groups.size()-1){           //xuameng 重要BUG
						Hawk.put(HawkConfig.LIVE_GROUP_INDEX,0);
						Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
				   		//加载多源配置
						try {
						ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
						for (int i=0; i< lives_groups.size();i++) {
							JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
							String name = jsonObject.has("name")?jsonObject.get("name").getAsString():"线路"+(i+1);
							LiveSettingItem liveSettingItem = new LiveSettingItem();
							liveSettingItem.setItemIndex(i);
							liveSettingItem.setItemName(name);
							liveSettingItemList.add(liveSettingItem);
						}
						liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
					} catch (Exception e) {
						// 捕获任何可能发生的异常
						e.printStackTrace();
					}
					int live_group_index_xu=Hawk.get(HawkConfig.LIVE_GROUP_INDEX,0);
					JsonObject livesOBJ_xu = lives_groups.get(live_group_index_xu).getAsJsonObject();
					loadLiveApi(livesOBJ_xu);
					}else{
						Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
						//加载多源配置
					try {
						ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
						for (int i=0; i< lives_groups.size();i++) {
							JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
							String name = jsonObject.has("name")?jsonObject.get("name").getAsString():"线路"+(i+1);
							LiveSettingItem liveSettingItem = new LiveSettingItem();
							liveSettingItem.setItemIndex(i);
							liveSettingItem.setItemName(name);
							liveSettingItemList.add(liveSettingItem);
						}
						liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
					} catch (Exception e) {
					// 捕获任何可能发生的异常
						e.printStackTrace();
					}
					JsonObject livesOBJ = lives_groups.get(live_group_index).getAsJsonObject();
					loadLiveApi(livesOBJ);
					}
				}else{
					initLiveSettings();
					liveSettingGroupList.clear();
					Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
				}
			}else{
				initLiveSettings();
				liveSettingGroupList.clear();
				Hawk.put(HawkConfig.LIVE_GROUP_LIST,"");
		}

        myHosts = new HashMap<>();
        if (infoJson.has("hosts")) {
            JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
            for (int i = 0; i < hostsArray.size(); i++) {
                String entry = hostsArray.get(i).getAsString();
                String[] parts = entry.split("=", 2); // 只分割一次，防止 value 里有 =
                if (parts.length == 2) {
                    myHosts.put(parts[0], parts[1]);
                }
            }
        }
        LOG.i("echo-api-live-config-----------load");
    }

    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    private void initLiveSettings() {
		ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "多源切换"));  //xuameng 换源
        ArrayList < ArrayList < String >> itemsArrayList = new ArrayList < > ();
        ArrayList < String > sourceItems = new ArrayList < > ();
        ArrayList < String > scaleItems = new ArrayList < > (Arrays.asList("默认比例", "16:9比例", "4:3 比例", "填充比例", "原始比例", "裁剪比例"));
        ArrayList < String > playerDecoderItems = new ArrayList < > (Arrays.asList("系统解码", "IJK  硬解", "IJK  软解", "EXO 解码"));
        ArrayList < String > timeoutItems = new ArrayList < > (Arrays.asList("超时05秒", "超时10秒", "超时15秒", "超时20秒", "超时25秒", "超时30秒"));
        ArrayList < String > personalSettingItems = new ArrayList < > (Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        ArrayList<String> yumItems = new ArrayList<>();   //xuameng新增 换源

        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);
        itemsArrayList.add(yumItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
    }

    public List<LiveSettingGroup> getLiveSettingGroupList() {
        return liveSettingGroupList;
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    public void loadLiveApi(JsonObject livesOBJ) {
        try {
            String lives = livesOBJ.toString();
            int index = lives.indexOf("proxy://");
            String url;
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix;
                    if(extUrl.startsWith("http") || extUrl.startsWith("clan://")){
                        extUrlFix = extUrl;
                    }else {
                        extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    }
                    extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                    url = url.replace(extUrl, extUrlFix);
                }
            } else {
                String type= livesOBJ.get("type").getAsString();
                if(type.equals("0")){
                    url = livesOBJ.get("url").getAsString();
                    if(!url.startsWith("http://127.0.0.1")){
                        if(url.startsWith("http")){
                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        }
                        url ="http://127.0.0.1:9978/proxy?do=live&type=txt&ext="+url;
                    }
                    LOG.i("echo-url:"+url);
                }else {
                    return;
                }
            }
            //设置epg
            if(livesOBJ.has("epg")){
                String epg =livesOBJ.get("epg").getAsString();
                Hawk.put(HawkConfig.EPG_URL,epg);
            }
            //直播播放器类型
            if(livesOBJ.has("playerType")){
                String livePlayType =livesOBJ.get("playerType").getAsString();
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE,livePlayType);
				HawkConfig.intLIVEPLAYTYPE = true;   //xuameng是否有直播默认播放器
            }else{
				HawkConfig.intLIVEPLAYTYPE = false;   //xuameng是否有直播默认播放器
			}
            //xuameng设置UA信息
            if(livesOBJ.has("ua")){
                String ua =livesOBJ.get("ua").getAsString();
                HashMap<String,String> liveHeader=new HashMap<>();
                liveHeader.put("User-Agent",ua);
                Hawk.put(HawkConfig.LIVE_WEB_HEADER,liveHeader);
            }else {
                Hawk.put(HawkConfig.LIVE_WEB_HEADER,null);
            }
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setGroupName(url);
            liveChannelGroupList.clear();
            liveChannelGroupList.add(liveChannelGroup);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        boolean js = sourceBean.getApi().endsWith(".js") || sourceBean.getApi().contains(".js?");
        if (js) return jsLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
    }

    public Object[] proxyLocal(Map param) {
        return jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void error(String msg);
        void notice(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://localhost/", fix).replace("file://", fix);
    }

    String fixContentPath(String url, String content) {
        if (content.contains("\"./")) {
            if(!url.startsWith("http") && !url.startsWith("clan://")){
                url = "http://" + url;
            }
            if(url.startsWith("clan://"))url=clanToAddress(url);
            content = content.replace("./", url.substring(0,url.lastIndexOf("/") + 1));
        }
        return content;
    }

    public Map<String,String> getMyHost() {
        return myHosts;
    }
}
