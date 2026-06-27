package com.github.tvbox.osc.util.live;

import com.github.tvbox.osc.util.DefaultConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {
    public static final String DEFAULT_GROUP_NAME = "聚汇直播";
    private static final String LEGACY_DEFAULT_GROUP_NAME = "Ungrouped";

    private static final Pattern NAME_PATTERN = Pattern.compile(".*,(.+?)$");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"(.*?)\"");
    private static final Pattern TVG_CHNO_PATTERN = Pattern.compile("tvg-chno=\"(.*?)\"");
    private static final Pattern TVG_LOGO_PATTERN = Pattern.compile("tvg-logo=\"(.*?)\"");
    private static final Pattern TVG_NAME_PATTERN = Pattern.compile("tvg-name=\"(.*?)\"");
    private static final Pattern TVG_URL_PATTERN = Pattern.compile("tvg-url=\"(.*?)\"");
    private static final Pattern TVG_ID_PATTERN = Pattern.compile("tvg-id=\"(.*?)\"");
    private static final Pattern HTTP_USER_AGENT_PATTERN = Pattern.compile("http-user-agent=\"(.*?)\"");
    private static final Pattern CATCHUP_PATTERN = Pattern.compile("catchup=\"(.*?)\"");
    private static final Pattern CATCHUP_SOURCE_PATTERN = Pattern.compile("catchup-source=\"(.*?)\"");
    private static final Pattern CATCHUP_REPLACE_PATTERN = Pattern.compile("catchup-replace=\"(.*?)\"");

    /* ===================== parse ===================== */

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        linkedHashMap.clear();
        JsonArray array = parseToJsonArray(str);
        if (array == null || array.size() == 0) return;

        for (JsonElement groupElement : array) {
            if (!groupElement.isJsonObject()) continue;
            JsonObject groupObj = groupElement.getAsJsonObject();

            String groupName = safeGetString(groupObj, "group");
            if (groupName.isEmpty()) groupName = safeGetString(groupObj, "name");
            groupName = normalizeGroupName(groupName);

            LinkedHashMap<String, ArrayList<String>> channelMap = new LinkedHashMap<>();

            JsonArray channels = safeGetArray(groupObj, "channels");
            if (channels.size() == 0) channels = safeGetArray(groupObj, "channel");

            for (JsonElement channelElement : channels) {
                if (!channelElement.isJsonObject()) continue;
                JsonObject channelObj = channelElement.getAsJsonObject();

                String channelName = safeGetString(channelObj, "name");
                if (channelName.isEmpty()) channelName = "未命名";

                ArrayList<String> urls = new ArrayList<>();
                JsonArray urlArray = safeGetArray(channelObj, "urls");
                for (JsonElement urlElement : urlArray) {
                    if (!urlElement.isJsonPrimitive()) continue;
                    String url = urlElement.getAsString().trim();
                    if (isUrl(url) && !urls.contains(url)) {
                        urls.add(url);
                    }
                }

                if (!urls.isEmpty()) {
                    channelMap.put(channelName, urls);
                }
            }

            if (!channelMap.isEmpty()) {
                linkedHashMap.put(groupName, channelMap);
            }
        }
    }

    /* ===================== entry ===================== */

    public static JsonArray parseToJsonArray(String str) {
        if (str == null) return new JsonArray();
        str = str.trim();
        if (str.isEmpty()) return new JsonArray();

        try {
            JsonElement element = JsonParser.parseString(str);
            if (element.isJsonArray()) {
                return normalizeJsonArray(element.getAsJsonArray());
            }
        } catch (Throwable ignored) {
        }

        if (str.startsWith("#EXTM3U")) {
            return parseM3uToJsonArray(str);
        }
        return parseTxtToJsonArray(str);
    }

    /* ===================== JSON ===================== */

    private static JsonArray normalizeJsonArray(JsonArray groups) {
        JsonArray result = new JsonArray();
        for (JsonElement g : groups) {
            if (!g.isJsonObject()) continue;
            JsonObject groupObj = g.getAsJsonObject();

            JsonObject outGroup = new JsonObject();
            String groupName = safeGetString(groupObj, "group");
            if (groupName.isEmpty()) groupName = safeGetString(groupObj, "name");
            outGroup.addProperty("group", normalizeGroupName(groupName));

            JsonArray channels = safeGetArray(groupObj, "channels");
            if (channels.size() == 0) channels = safeGetArray(groupObj, "channel");

            for (JsonElement c : channels) {
                if (!c.isJsonObject()) continue;
                JsonObject channelObj = c.getAsJsonObject();
                JsonObject outChannel = new JsonObject();

                copyIfExists(channelObj, outChannel, "name");
                copyIfExists(channelObj, outChannel, "urls");
                copyIfExists(channelObj, outChannel, "logo");
                copyIfExists(channelObj, outChannel, "epg");
                copyIfExists(channelObj, outChannel, "ua");
                copyIfExists(channelObj, outChannel, "click");
                copyIfExists(channelObj, outChannel, "format");
                copyIfExists(channelObj, outChannel, "origin");
                copyIfExists(channelObj, outChannel, "referer");
                copyIfExists(channelObj, outChannel, "tvg-id");
                copyIfExists(channelObj, outChannel, "tvg-name");
                copyIfExists(channelObj, outChannel, "tvg-chno");
                copyIfExists(channelObj, outChannel, "parse");
                copyIfExists(channelObj, outChannel, "header");
                copyIfExists(channelObj, outChannel, "catchup");
                copyIfExists(channelObj, outChannel, "catchup-source");
                copyIfExists(channelObj, outChannel, "catchup-replace");

                addChannel(outGroup, outChannel);
            }

            if (!outGroup.has("channels")) {
                outGroup.add("channels", new JsonArray());
            }
            result.add(outGroup);
        }
        return result;
    }

    /* ===================== M3U ===================== */

    private static JsonArray parseM3uToJsonArray(String str) {
        JsonArray result = new JsonArray();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(normalizeText(str)));
            String line;
            JsonObject currentGroup = null;
            JsonObject pendingChannel = null;
            JsonObject pendingMeta = new JsonObject();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTM3U")) {
                    mergeMeta(pendingMeta, buildMeta(line));
                    continue;
                }
                if (isSetting(line)) {
                    mergeMeta(pendingMeta, buildSetting(line));
                    continue;
                }

                if (line.startsWith("#EXTINF") || line.contains("#EXTINF")) {
                    String groupName = getSafe(line, GROUP_PATTERN, DEFAULT_GROUP_NAME);
                    currentGroup = findOrCreateGroup(result, groupName);

                    pendingChannel = new JsonObject();
                    pendingChannel.addProperty("name", getSafe(line, NAME_PATTERN, "未命名"));
                    mergeMeta(pendingChannel, buildMeta(line));
                    mergeMeta(pendingChannel, pendingMeta);
                    pendingMeta = new JsonObject();
                    continue;
                }

                if (line.startsWith("#")) continue;

                if (currentGroup == null) {
                    currentGroup = findOrCreateGroup(result, DEFAULT_GROUP_NAME);
                }
                if (pendingChannel == null) {
                    pendingChannel = new JsonObject();
                    pendingChannel.addProperty("name", "未命名");
                }

                String[] parts = line.split("\\|", 2);
                String url = parts[0].trim();
                if (!isUrl(url)) continue;

                if (parts.length > 1) {
                    mergeMeta(pendingMeta, parseHeaderString(parts[1]));
                }
                mergeMeta(pendingChannel, pendingMeta);

                JsonArray urls = safeGetArray(pendingChannel, "urls");
                if (!containsUrl(urls, url)) urls.add(url);
                pendingChannel.add("urls", urls);

                addChannel(currentGroup, pendingChannel);
                pendingChannel = null;
                pendingMeta = new JsonObject();
            }
            reader.close();
        } catch (Throwable ignored) {
        }
        return result;
    }

    /* ===================== TXT ===================== */

    private static JsonArray parseTxtToJsonArray(String str) {
        JsonArray result = new JsonArray();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(normalizeText(str)));
            String line;
            JsonObject currentGroup = null;
            JsonObject pendingMeta = new JsonObject();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#")) {
                    if (isSetting(line)) mergeMeta(pendingMeta, buildSetting(line));
                    continue;
                }

                if (line.contains("#genre#")) {
                    String[] sp = safeSplit(line, ',', 2);
                    String groupName = sp[0].trim();
                    currentGroup = findOrCreateGroup(result, groupName);
                    pendingMeta = new JsonObject();
                    continue;
                }

                String[] sp = safeSplit(line, ',', 2);
                if (sp.length < 2) continue;

                if (currentGroup == null) {
                    currentGroup = findOrCreateGroup(result, DEFAULT_GROUP_NAME);
                }

                JsonObject channel = new JsonObject();
                channel.addProperty("name", sp[0].trim());
                mergeMeta(channel, pendingMeta);

                String[] urlParts = safeSplit(sp[1], '#');
                ArrayList<String> urls = new ArrayList<>();
                for (String part : urlParts) {
                    String url = part.trim();
                    if (isUrl(url) && !urls.contains(url)) {
                        urls.add(url);
                    }
                }

                if (urls.isEmpty()) continue;

                JsonArray urlArray = new JsonArray();
                for (String url : urls) urlArray.add(url);
                channel.add("urls", urlArray);

                addChannel(currentGroup, channel);
                pendingMeta = new JsonObject();
            }
            reader.close();
        } catch (Throwable ignored) {
        }
        return result;
    }

    /* ===================== 工具方法（核心安全点）===================== */

    /** 安全取字符串 */
    private static String safeGetString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return "";
        return e.isJsonPrimitive() ? e.getAsString().trim() : "";
    }

    /** 安全取数组 */
    private static JsonArray safeGetArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return new JsonArray();
        JsonElement e = obj.get(key);
        if (e != null && e.isJsonArray()) return e.getAsJsonArray();
        return new JsonArray();
    }

    /** 正则安全获取（带兜底） */
    private static String getSafe(String line, Pattern pattern, String def) {
        Matcher m = pattern.matcher(line);
        return m.find() ? m.group(1).trim() : def;
    }

    /** 防越界 split */
    private static String[] safeSplit(String s, char delimiter) {
        if (s == null) return new String[0];
        return s.split(String.valueOf(delimiter));
    }

    private static String[] safeSplit(String s, char delimiter, int limit) {
        if (s == null) return new String[0];
        return s.split(String.valueOf(delimiter), limit);
    }

    private static String normalizeText(String str) {
        return str.replace("\r\n", "\n").replace("\r", "\n");
    }

    /* ===================== 其余原逻辑（略作安全封装）===================== */

    private static void copyIfExists(JsonObject src, JsonObject dst, String key) {
        if (src != null && src.has(key) && src.get(key) != null) {
            dst.add(key, src.get(key));
        }
    }

    private static JsonObject findOrCreateGroup(JsonArray result, String name) {
        name = normalizeGroupName(name);
        for (JsonElement e : result) {
            if (!e.isJsonObject()) continue;
            JsonObject g = e.getAsJsonObject();
            if (!g.has("group")) continue;
            if (name.equals(safeGetString(g, "group"))) return g;
        }
        JsonObject g = new JsonObject();
        g.addProperty("group", name);
        g.add("channels", new JsonArray());
        result.add(g);
        return g;
    }

    public static String normalizeGroupName(String name) {
        if (name == null || name.trim().isEmpty()) return DEFAULT_GROUP_NAME;
        name = name.trim();
        return LEGACY_DEFAULT_GROUP_NAME.equalsIgnoreCase(name) ? DEFAULT_GROUP_NAME : name;
    }

    private static void addChannel(JsonObject group, JsonObject channel) {
        JsonArray channels = safeGetArray(group, "channels");
        String name = safeGetString(channel, "name");
        if (name.isEmpty()) {
            channel.addProperty("name", "未命名");
            name = "未命名";
        }

        JsonObject exists = findChannel(channels, name);
        if (exists == null) {
            channels.add(channel);
        } else {
            mergeChannel(exists, channel);
        }
        group.add("channels", channels);
    }

    private static JsonObject findChannel(JsonArray channels, String name) {
        for (JsonElement e : channels) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            if (name.equals(safeGetString(c, "name"))) return c;
        }
        return null;
    }

    private static void mergeChannel(JsonObject dst, JsonObject src) {
        mergeUrls(dst, src);
        for (Map.Entry<String, JsonElement> en : src.entrySet()) {
            String k = en.getKey();
            if ("urls".equals(k)) continue;
            if (!dst.has(k) || isEmptyValue(dst.get(k))) {
                dst.add(k, en.getValue());
            }
        }
    }

    private static void mergeUrls(JsonObject dst, JsonObject src) {
        JsonArray srcUrls = safeGetArray(src, "urls");
        if (srcUrls.size() == 0) return;

        JsonArray dstUrls = safeGetArray(dst, "urls");
        for (JsonElement e : srcUrls) {
            if (!e.isJsonPrimitive()) continue;
            String url = e.getAsString().trim();
            if (isUrl(url) && !containsUrl(dstUrls, url)) {
                dstUrls.add(url);
            }
        }
        dst.add("urls", dstUrls);
    }

    private static boolean containsUrl(JsonArray urls, String url) {
        for (JsonElement e : urls) {
            if (e.isJsonPrimitive() && url.equals(e.getAsString())) return true;
        }
        return false;
    }

    private static boolean isEmptyValue(JsonElement e) {
        if (e == null || e.isJsonNull()) return true;
        if (e.isJsonPrimitive()) return e.getAsString().trim().isEmpty();
        if (e.isJsonArray()) return e.getAsJsonArray().size() == 0;
        if (e.isJsonObject()) return e.getAsJsonObject().entrySet().size() == 0;
        return true;
    }

    private static void mergeMeta(JsonObject dst, JsonObject src) {
        if (dst == null || src == null) return;
        for (Map.Entry<String, JsonElement> e : src.entrySet()) {
            dst.add(e.getKey(), e.getValue());
        }
    }

    /* ====== 以下完全保留你原有逻辑 ====== */

    private static JsonObject buildMeta(String line) {
        JsonObject obj = new JsonObject();
        put(obj, "logo", get(line, TVG_LOGO_PATTERN));
        put(obj, "epg", get(line, TVG_URL_PATTERN));
        put(obj, "tvg-id", get(line, TVG_ID_PATTERN));
        put(obj, "tvg-name", get(line, TVG_NAME_PATTERN));
        put(obj, "tvg-chno", get(line, TVG_CHNO_PATTERN));
        put(obj, "ua", get(line, HTTP_USER_AGENT_PATTERN));
        String catchup = get(line, CATCHUP_PATTERN);
        String source = get(line, CATCHUP_SOURCE_PATTERN);
        String replace = get(line, CATCHUP_REPLACE_PATTERN);
        if (!catchup.isEmpty() || !source.isEmpty() || !replace.isEmpty()) {
            JsonObject c = new JsonObject();
            put(c, "type", catchup);
            put(c, "source", source);
            put(c, "replace", replace);
            obj.add("catchup", c);
        }
        return obj;
    }

    private static JsonObject buildSetting(String line) {
        JsonObject obj = new JsonObject();
        if (line.startsWith("ua")) put(obj, "ua", getValue(line, "ua"));
        if (line.startsWith("parse")) put(obj, "parse", getValue(line, "parse"));
        if (line.startsWith("click")) put(obj, "click", getValue(line, "click"));
        if (line.startsWith("header")) {
            String v = getValue(line, "header");
            if (!v.isEmpty()) {
                try {
                    obj.add("header", JsonParser.parseString(v).getAsJsonObject());
                } catch (Throwable ignored) {}
            }
        }
        if (line.startsWith("format")) put(obj, "format", getValue(line, "format"));
        if (line.startsWith("origin")) put(obj, "origin", getValue(line, "origin"));
        if (line.startsWith("referer")) put(obj, "referer", getValue(line, "referer"));
        if (line.startsWith("#EXTHTTP:")) {
            try {
                obj.add("header", JsonParser.parseString(line.split("#EXTHTTP:", 2)[1].trim()).getAsJsonObject());
            } catch (Throwable ignored) {}
        }
        if (line.startsWith("#EXTVLCOPT:")) {
            if (line.contains("http-user-agent")) put(obj, "ua", getValue(line, "http-user-agent"));
            if (line.contains("http-origin")) put(obj, "origin", getValue(line, "http-origin"));
            if (line.contains("http-referrer")) put(obj, "referer", getValue(line, "http-referrer"));
        }
        if (line.startsWith("#KODIPROP:") && line.contains("manifest_type=")) {
            put(obj, "format", getValue(line, "manifest_type"));
        }
        return obj;
    }

    private static boolean isSetting(String line) {
        return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click")
                || line.startsWith("player") || line.startsWith("header") || line.startsWith("format")
                || line.startsWith("origin") || line.startsWith("referer")
                || line.startsWith("#EXTHTTP:") || line.startsWith("#EXTVLCOPT:")
                || line.startsWith("#KODIPROP:");
    }

    private static boolean isUrl(String url) {
        return url != null && !url.isEmpty()
                && (url.startsWith("http") || url.startsWith("rtp")
                || url.startsWith("rtsp") || url.startsWith("rtmp"));
    }

    private static String get(String line, Pattern p) {
        Matcher m = p.matcher(line);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String getValue(String line, String key) {
        int i = line.indexOf(key + "=");
        if (i < 0) return "";
        return line.substring(i + key.length() + 1).trim().replace("\"", "");
    }

    private static void put(JsonObject o, String k, String v) {
        if (v != null && !v.isEmpty()) o.addProperty(k, v);
    }

    private static JsonObject parseHeaderString(String text) {
        JsonObject w = new JsonObject();
        JsonObject o = new JsonObject();
        for (String p : text.split("&")) {
            if (!p.contains("=")) continue;
            String[] a = p.split("=", 2);
            o.addProperty(a[0].trim().replace("\"", ""), a[1].trim().replace("\"", ""));
        }
        if (o.size() > 0) w.add("header", o);
        return w;
    }
}
