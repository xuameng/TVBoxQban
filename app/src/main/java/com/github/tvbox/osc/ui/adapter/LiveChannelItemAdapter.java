package com.github.tvbox.osc.ui.adapter;

import android.view.View;   //xuameng 新增我的收藏
import android.graphics.Color;
import android.widget.TextView;

import com.google.gson.JsonArray;  //xuameng 新增我的收藏
import com.google.gson.JsonObject;  //xuameng 新增我的收藏


import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import com.orhanobut.hawk.Hawk;  //xuameng 新增我的收藏

import java.util.ArrayList;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.HawkConfig;  //xuameng 新增我的收藏


/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar); // xuameng新增：我的收藏获取星星TextView的引用
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // xuameng========== 修改：根据收藏状态显示 我的收藏TextView 星星 ==========
        boolean isFavorited = isChannelFavorited(item);
        if (isFavorited) {
            tvFavoriteStar.setVisibility(View.VISIBLE);
            tvFavoriteStar.setText("★");
            tvFavoriteStar.setTextColor(Color.parseColor("#FFD700")); //xuameng我的收藏 设置为金黄色
        } else {
            tvFavoriteStar.setVisibility(View.GONE);
        }
        // xuameng========== 我的收藏 修改结束 ==========

        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        }
        else{
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1)
            notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public int getSelectedChannelIndex() {        //xuameng  选中频道
        return selectedChannelIndex;
    }

    public int getSelectedfocusedChannelIndex() {        //xuameng 焦点颜色
        return focusedChannelIndex;
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1)
            notifyItemChanged(preFocusedChannelIndex);
        if (this.focusedChannelIndex != -1)
            notifyItemChanged(this.focusedChannelIndex);
        else if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    /** xuameng 我的收藏
     * 判断当前频道是否已被收藏
     */
    private boolean isChannelFavorited(LiveChannelItem channel) {
        if (channel == null) return false;
        JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
        JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);

        for (int i = 0; i < favoriteArray.size(); i++) {
            JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
            if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                return true;
            }
        }
        return false;
    }

}
