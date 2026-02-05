package com.github.tvbox.osc.ui.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;

import com.github.tvbox.osc.bean.LiveChannelItem;


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
TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar); // 新增：获取星星TextView的引用
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // ========== 修改：根据收藏状态显示 TextView 星星 ==========
        boolean isFavorited = isChannelFavorited(item);
        if (isFavorited) {
            tvFavoriteStar.setVisibility(View.VISIBLE);
            tvFavoriteStar.setText("★");
            tvFavoriteStar.setTextColor(Color.YELLOW); // 设置为黄色
        } else {
            tvFavoriteStar.setVisibility(View.GONE);
        }
        // ========== 修改结束 ==========

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

    public int getSelectedChannelIndex() {        //xuameng
        return selectedChannelIndex;
    }

	public int getSelectedfocusedChannelIndex() {        //xuameng
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

	 /**
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

    /**
     * 切换频道的收藏状态
     */
    public void toggleFavoriteChannel(LiveChannelItem channel, int position) {
        JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
        JsonObject channelJson = LiveChannelItem.convertChannelToJson(channel);

        boolean found = false;
        int foundIndex = -1;
        for (int i = 0; i < favoriteArray.size(); i++) {
            JsonObject fav = favoriteArray.get(i).getAsJsonObject();
            if (LiveChannelItem.isSameChannel(fav, channelJson)) {
                found = true;
                foundIndex = i;
                break;
            }
        }

        if (found) {
            favoriteArray.remove(foundIndex);
            Toast.makeText(mContext, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoriteArray.add(channelJson);
            Toast.makeText(mContext, "已加入收藏", Toast.LENGTH_SHORT).show();
        }

        Hawk.put(HawkConfig.LIVE_FAVORITE_CHANNELS, favoriteArray);
        notifyItemChanged(position);
    }

    /**
     * 显示收藏操作菜单
     */
    public void showFavoriteMenu(final LiveChannelItem channel, final int position) {
        if (channel == null) return;

        boolean isCurrentlyFavorited = isChannelFavorited(channel);
        String menuTitle = isCurrentlyFavorited ? "取消收藏" : "加入收藏";

        new AlertDialog.Builder(mContext)
                .setTitle("频道操作")
                .setMessage(channel.getChannelName())
                .setPositiveButton(menuTitle, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleFavoriteChannel(channel, position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
