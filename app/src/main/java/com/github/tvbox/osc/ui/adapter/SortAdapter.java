package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;
import android.graphics.Typeface;

/**
 * @author xuameng
 * @date 2026/04/08
 * @description: 首页分类菜单 Adapter（终极版：Payload局部刷新，无跳动，焦点完美）
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {

    // ✅ 唯一真实选中状态
    private int selectedPosition = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    /**
     * ✅ 外部设置选中项
     */
    public void setSelectedPosition(int pos) {
        if (pos < 0 || pos >= getData().size()) return;

        int old = selectedPosition;
        selectedPosition = pos;

        if (old != selectedPosition) {
            // ✅ 核心修复：使用 Payload 进行局部刷新
            // 1. 刷新旧的选中项，告诉它“你不再是焦点了，请变回 1.0”
            if (old >= 0 && old < getData().size()) {
                notifyItemChanged(old, false); // 第二个参数 false 代表“未选中”
            }
            // 2. 刷新新的选中项，告诉它“你是焦点了，请变成 1.1”
            notifyItemChanged(selectedPosition, true); // 第二个参数 true 代表“选中”
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        int pos = helper.getAdapterPosition();
        if (pos == -1) return;
        
        boolean isSelected = pos == selectedPosition;
        boolean isHomePage = pos == 0;

        // ✅ 全量绑定逻辑（只在初始化或全量刷新时执行）
        helper.setText(R.id.tvTitle, item.name);

        helper.setTextColor(
                R.id.tvTitle,
                isSelected
                        ? mContext.getResources().getColor(R.color.color_FFFFFF)
                        : mContext.getResources().getColor(R.color.color_BBFFFFFF)
        );

        helper.setTypeface(
                R.id.tvTitle,
                isSelected
                        ? Typeface.DEFAULT_BOLD
                        : Typeface.DEFAULT
        );

        // 筛选图标逻辑
        boolean hasFilterSelected = isSelected && !isHomePage && item.filterSelectCount() > 0;
        boolean hasFiltersAvailable = isSelected && !isHomePage && !item.filters.isEmpty() && item.filterSelectCount() <= 0;
        
        helper.setGone(R.id.tvFilterColor, hasFilterSelected);
        helper.setGone(R.id.tvFilter, hasFiltersAvailable);
    }

    /**
     * ✅ 核心修复：Payload 局部更新逻辑
     * 这个方法专门处理“只改变大小”的需求，不触碰文字、颜色、焦点
     */
    @Override
    protected void convert(@NonNull BaseViewHolder helper, @NonNull MovieSort.SortData item, @NonNull List<Object> payloads) {
        // payloads 里存的就是我们在 notifyItemChanged 里传的 true/false
        for (Object payload : payloads) {
            if (payload instanceof Boolean) {
                boolean isSelected = (Boolean) payload;
                // ✅ 这里只做缩放，不做其他任何操作
                // 这样就不会干扰焦点，也不会导致左右跳动
                if (isSelected) {
                    helper.itemView.setScaleX(1.1f);
                    helper.itemView.setScaleY(1.1f);
                } else {
                    helper.itemView.setScaleX(1.0f);
                    helper.itemView.setScaleY(1.0f);
                }
            }
        }
    }
}
