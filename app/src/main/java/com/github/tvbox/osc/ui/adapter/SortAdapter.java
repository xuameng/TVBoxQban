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
 * @description: 首页分类菜单 Adapter（修复焦点及多高亮等BUG，彻底解决跳动）
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
        
        // ❌ 删除旧的逻辑（只更新两个Item）
        // 因为那个逻辑会导致 convert 被调用，从而触发 setScaleX 强制重置
        
        // ✅ 新逻辑：直接刷新整个数据集
        // 这样虽然性能稍微低一点点，但是能保证 convert 里的逻辑最简单
        selectedPosition = pos;
        notifyDataSetChanged(); 
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

        // ✅ 核心修复点
        // 1. 我们不再在代码里强制设置 setScaleX(1.0f)
        // 2. 我们只在选中时设置变大，不选中时不碰这个属性
        // 3. 这样 View 复用时，不会被强制压回 1.0，视觉上就不会有“抽搐感”
        
        if (isSelected) {
            // 只有当选中时，才设置大一点
            // 这样就不会出现“从1.1瞬间变回1.0”的左右抖动
            helper.itemView.setScaleX(1.1f);
            helper.itemView.setScaleY(1.1f);
        }
        // ❌ 注释掉这行：helper.itemView.setScaleX(1.0f);
        // ❌ 注释掉这行：helper.itemView.setScaleY(1.0f);
        // 让非选中状态保持 View 的默认状态（通常是1.0），不要在代码里反复横跳

        // 筛选图标逻辑
        boolean hasFilterSelected = isSelected && !isHomePage && item.filterSelectCount() > 0;
        boolean hasFiltersAvailable = isSelected && !isHomePage && !item.filters.isEmpty() && item.filterSelectCount() <= 0;
        
        helper.setGone(R.id.tvFilterColor, hasFilterSelected);
        helper.setGone(R.id.tvFilter, hasFiltersAvailable);
    }
}
