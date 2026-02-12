package com.github.tvbox.osc.bean;

/**
 * @author xuameng
 * @date :2026/2/12
 * @description:
 */
public class LiveSettingItem {
    private int itemIndex;
    private String itemName;
    private boolean itemSelected = false;
    private String itemUrl; // xuaemng新增：存储线路URL

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public boolean isItemSelected() {
        return itemSelected;
    }

    public void setItemSelected(boolean itemSelected) {
        this.itemSelected = itemSelected;
    }

    // xuameng新增：itemUrl的Getter/Setter
    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl;
    }

    // xuameng新增：itemUrl的Getter/Setter
    public String getItemUrl() {
        return itemUrl;
    }
}
