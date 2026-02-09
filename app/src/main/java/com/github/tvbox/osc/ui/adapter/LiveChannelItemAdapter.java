package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private ExecutorService favoriteCheckExecutor;
    private Map<String, Boolean> favoriteCache = new ConcurrentHashMap<>();

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvFavoriteStar = holder.getView(R.id.ivFavoriteStar);
        
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());
        tvFavoriteStar.setVisibility(View.GONE);
        
        int position = holder.getLayoutPosition();
        
        // 异步检查收藏状态
        checkChannelFavoriteAsync(item, position, new FavoriteCheckCallback() {
            @Override
            public void onFavoriteChecked(boolean isFavorited, int checkedPosition) {
                // 通过RecyclerView获取当前ViewHolder
                RecyclerView recyclerView = getRecyclerView();
                if (recyclerView != null) {
                    BaseViewHolder currentHolder = (BaseViewHolder) recyclerView.findViewHolderForAdapterPosition(checkedPosition);
                    if (currentHolder != null) {
                        TextView starView = currentHolder.getView(R.id.ivFavoriteStar);
                        if (isFavorited) {
                            starView.setVisibility(View.VISIBLE);
                            starView.setText("★");
                            starView.setTextColor(Color.parseColor("#FFD700"));
                        } else {
                            starView.setVisibility(View.GONE);
                        }
                    }
                }
            }
        });

        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        } else {
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    // 新增接口定义
    public interface FavoriteCheckCallback {
        void onFavoriteChecked(boolean isFavorited, int position);
    }

    // 生成缓存键的方法
    private String getChannelCacheKey(LiveChannelItem channel) {
        return channel.getChannelName() + "|" + channel.getUrl();
    }

    // 修改异步检查方法，先检查缓存
    private void checkChannelFavoriteAsync(LiveChannelItem channel, int position, FavoriteCheckCallback callback) {
        if (channel == null) {
            if (callback != null) callback.onFavoriteChecked(false, position);
            return;
        }
        
        // 先检查缓存
        String cacheKey = getChannelCacheKey(channel);
        Boolean cachedResult = favoriteCache.get(cacheKey);
        if (cachedResult != null) {
            if (callback != null) {
                boolean finalResult = cachedResult;
                if (mContext instanceof Activity) {
                    ((Activity) mContext).runOnUiThread(() -> {
                        callback.onFavoriteChecked(finalResult, position);
                    });
                }
            }
            return;
        }
        
        // 缓存未命中，执行异步查询
        if (favoriteCheckExecutor == null) {
            int coreCount = Runtime.getRuntime().availableProcessors();
            favoriteCheckExecutor = Executors.newFixedThreadPool(Math.max(1, coreCount - 1));
        }
        
        favoriteCheckExecutor.execute(() -> {
            boolean isFavorited = false;
            try {
                JsonArray favoriteArray = Hawk.get(HawkConfig.LIVE_FAVORITE_CHANNELS, new JsonArray());
                JsonObject currentChannelJson = LiveChannelItem.convertChannelToJson(channel);
                
                for (int i = 0; i < favoriteArray.size(); i++) {
                    JsonObject favChannelJson = favoriteArray.get(i).getAsJsonObject();
                    if (LiveChannelItem.isSameChannel(favChannelJson, currentChannelJson)) {
                        isFavorited = true;
                        break;
                    }
                }
                
                // 更新缓存
                favoriteCache.put(cacheKey, isFavorited);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                boolean finalIsFavorited = isFavorited;
                if (mContext instanceof Activity) {
                    ((Activity) mContext).runOnUiThread(() -> {
                        if (callback != null) {
                            callback.onFavoriteChecked(finalIsFavorited, position);
                        }
                    });
                }
            }
        });
    }

    // 清理缓存的方法
    public void clearFavoriteCache() {
        if (favoriteCache != null) {
            favoriteCache.clear();
        }
    }

    // 在数据更新时清理缓存
    @Override
    public void setNewData(@Nullable List<LiveChannelItem> data) {
        clearFavoriteCache();
        super.setNewData(data);
    }

    // 在Adapter销毁时释放资源
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (favoriteCheckExecutor != null && !favoriteCheckExecutor.isShutdown()) {
            favoriteCheckExecutor.shutdownNow();
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

    public int getSelectedChannelIndex() {
        return selectedChannelIndex;
    }

    public int getSelectedfocusedChannelIndex() {
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
     * 判断当前频道是否已被收藏（同步方法，保留供其他用途）
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
