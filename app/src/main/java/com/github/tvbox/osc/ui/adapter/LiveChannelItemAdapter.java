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
import com.github.tvbox.osc.R;

import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.base.App;
import android.os.Handler;
import android.os.Looper;


/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;

    // ... 现有成员变量 ...
    private OnFavoriteChangeListener favoriteChangeListener;

    // 定义收藏变更监听器接口
    public interface OnFavoriteChangeListener {
        void onFavoriteChanged();
    }

    // 设置监听器的方法
    public void setOnFavoriteChangeListener(OnFavoriteChangeListener listener) {
        this.favoriteChangeListener = listener;
    }

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());

    // 添加长按监听器
    setOnItemLongClickListener(new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
            LiveChannelItem channel = getData().get(position);
            // 直接切换收藏状态，不显示菜单
            toggleFavoriteChannel(channel, position);
            return true; // 消费长按事件
        }
    });

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
        App.showToastShort(mContext, "已取消收藏：" + channel.getChannelName());
    } else {
        favoriteArray.add(channelJson);
        App.showToastShort(mContext, "已收藏：" + channel.getChannelName());
    }

    Hawk.put(HawkConfig.LIVE_FAVORITE_CHANNELS, favoriteArray);
    
    // 只需要更新当前项的UI
 new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
    @Override
    public void run() {
        notifyItemChanged(position);
    }
}, 200); 

	        // === 新增：通知收藏状态变更 ===
        if (favoriteChangeListener != null) {
            favoriteChangeListener.onFavoriteChanged();
        }
    
}





}
