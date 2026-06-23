package com.github.tvbox.osc.util;

import java.util.ArrayList;
import com.orhanobut.hawk.Hawk;

public class HistoryHelper {
    private static Integer[] hisNumArray = {30,50,70,100};
    private static final String API_LINE_SPLIT = "\t";

    public static final String getHistoryNumName(int index){
        Integer value = getHisNum(index);
        return value + "条";
    }

    public static final int getHisNum(int index){
        Integer value = null;
        if(index>=0 && index < hisNumArray.length){
            value = hisNumArray[index];
        }else{
            value = hisNumArray[0];
        }
        return value;
    }

    public static String buildApiLine(String name, String url) {
        String lineName = name == null ? "" : name.trim();
        String lineUrl = url == null ? "" : url.trim();
        if (lineName.isEmpty()) {
            lineName = lineUrl;
        }
        return lineName + API_LINE_SPLIT + lineUrl;
    }

    public static String getApiLineName(String value) {
        if (value == null) return "";
        int splitIndex = value.indexOf(API_LINE_SPLIT);
        String name = splitIndex >= 0 ? value.substring(0, splitIndex) : value;
        return name.trim();
    }

    public static String getApiLineUrl(String value) {
        if (value == null) return "";
        int splitIndex = value.indexOf(API_LINE_SPLIT);
        String url = splitIndex >= 0 ? value.substring(splitIndex + API_LINE_SPLIT.length()) : value;
        return url.trim();
    }

    public static boolean isApiLineUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String trimUrl = url.trim();
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        for (String apiLine : apiLines) {
            if (trimUrl.equals(getApiLineUrl(apiLine))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isApiLineSource(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String source = Hawk.get(HawkConfig.API_LINE_SOURCE, "");
        return url.trim().equals(source);
    }

    public static boolean isApiLineHistory(String url) {
        return isApiLineSource(url) || isApiLineUrl(url);
    }

    public static void clearApiLineList() {
        Hawk.put(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        Hawk.put(HawkConfig.API_LINE_SOURCE, "");
    }

    public static void setApiHistory(String value){
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        if (!history.contains(value)) {
            history.add(0, value);
        }
        if (history.size() > 30) {
            history.remove(30);
        }
        Hawk.put(HawkConfig.API_HISTORY, history);
    }

    public static void setLiveApiHistory(String value){
        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        if (!history.contains(value)) {
            history.add(0, value);
        }
        if (history.size() > 30) {
            history.remove(30);
        }
        Hawk.put(HawkConfig.LIVE_API_HISTORY, history);
    }
}
