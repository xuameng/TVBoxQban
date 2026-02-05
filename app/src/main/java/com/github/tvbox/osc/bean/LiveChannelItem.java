package com.github.tvbox.osc.bean;

import java.util.ArrayList;


import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.HashSet;
import java.util.Set;
/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItem {
    /**
     * channelIndex : 频道索引号
     * channelNum : 频道名称
     * channelSourceNames : 频道源名称
     * channelUrls : 频道源地址
     * sourceIndex : 频道源索引
     * sourceNum : 频道源总数
     */
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private ArrayList<String> channelSourceNames;
    private ArrayList<String> channelUrls;
    public int sourceIndex = 0;
    public int sourceNum = 0;
    public boolean include_back = false;

    public void setinclude_back(boolean include_back) {
        this.include_back = include_back;
    }

    public boolean getinclude_back() {
        return include_back;
    }

    public void setChannelIndex(int channelIndex) {
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public ArrayList<String> getChannelUrls() {
        return channelUrls;
    }

    public void setChannelUrls(ArrayList<String> channelUrls) {
        this.channelUrls = channelUrls;
        sourceNum = channelUrls.size();
    }
    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }
    public void nextSource() {
        sourceIndex++;
        if (sourceIndex == sourceNum) sourceIndex = 0;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public String getUrl() {
        return channelUrls.get(sourceIndex);
    }

    public int getSourceNum() {
        return sourceNum;
    }

    public ArrayList<String> getChannelSourceNames() {
        return channelSourceNames;
    }

    public void setChannelSourceNames(ArrayList<String> channelSourceNames) {
        this.channelSourceNames = channelSourceNames;
    }

    public String getSourceName() {
        return channelSourceNames.get(sourceIndex);
    }

    /**
     * 将 LiveChannelItem 转换为 JsonObject 以便存储到 Hawk
     */
    public static JsonObject convertChannelToJson(LiveChannelItem channel) {
        JsonObject json = new JsonObject();
        json.addProperty("channelIndex", channel.getChannelIndex());
        json.addProperty("channelNum", channel.getChannelNum());
        json.addProperty("channelName", channel.getChannelName());
        json.addProperty("sourceIndex", channel.getSourceIndex());
        json.addProperty("sourceNum", channel.getSourceNum());
        json.addProperty("include_back", channel.getinclude_back());

        JsonArray sourceNameArray = new JsonArray();
        ArrayList<String> sourceNames = channel.getChannelSourceNames();
        if (sourceNames != null) {
            for (String name : sourceNames) {
                sourceNameArray.add(name);
            }
        }
        json.add("channelSourceNames", sourceNameArray);

        JsonArray urlArray = new JsonArray();
        ArrayList<String> urls = channel.getChannelUrls();
        if (urls != null) {
            for (String url : urls) {
                urlArray.add(url);
            }
        }
        json.add("channelUrls", urlArray);

        return json;
    }

    /**
     * 从 Hawk 中读取收藏的频道，并构建一个 LiveChannelGroup 对象
     */
    public static LiveChannelGroup createFavoriteChannelGroup() {
        LiveChannelGroup group = new LiveChannelGroup();
        group.setGroupIndex(-1);
        group.setGroupName("我的收藏");
        group.setGroupPassword("");

        ArrayList<LiveChannelItem> favoriteChannels = new ArrayList<>();
        JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());

        for (int i = 0; i < favoriteArray.size(); i++) {
            try {
                JsonObject channelJson = favoriteArray.get(i).getAsJsonObject();
                LiveChannelItem item = new LiveChannelItem();

                item.setChannelIndex(channelJson.get("channelIndex").getAsInt());
                item.setChannelNum(channelJson.get("channelNum").getAsInt());
                item.setChannelName(channelJson.get("channelName").getAsString());
                item.setSourceIndex(channelJson.get("sourceIndex").getAsInt());
                item.setSourceNum(channelJson.get("sourceNum").getAsInt());
                item.setinclude_back(channelJson.get("include_back").getAsBoolean());

                JsonArray sourceNameArray = channelJson.getAsJsonArray("channelSourceNames");
                ArrayList<String> sourceNames = new ArrayList<>();
                for (int j = 0; j < sourceNameArray.size(); j++) {
                    sourceNames.add(sourceNameArray.get(j).getAsString());
                }
                item.setChannelSourceNames(sourceNames);

                JsonArray urlArray = channelJson.getAsJsonArray("channelUrls");
                ArrayList<String> urls = new ArrayList<>();
                for (int j = 0; j < urlArray.size(); j++) {
                    urls.add(urlArray.get(j).getAsString());
                }
                item.setChannelUrls(urls);

                favoriteChannels.add(item);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        group.setLiveChannels(favoriteChannels);
        return group;
    }

    /**
     * 判断两个 JsonObject 是否代表同一个频道
     */
    public static boolean isSameChannel(JsonObject fav1, JsonObject fav2) {
        if (!fav1.get("channelName").getAsString().equals(fav2.get("channelName").getAsString())) {
            return false;
        }

        JsonArray urls1 = fav1.getAsJsonArray("channelUrls");
        JsonArray urls2 = fav2.getAsJsonArray("channelUrls");

        if (urls1.size() != urls2.size()) {
            return false;
        }

        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>();
        for (int i = 0; i < urls1.size(); i++) {
            set1.add(urls1.get(i).getAsString());
            set2.add(urls2.get(i).getAsString());
        }

        return set1.equals(set2);
    }
}
