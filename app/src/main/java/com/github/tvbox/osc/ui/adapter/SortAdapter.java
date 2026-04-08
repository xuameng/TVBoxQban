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
        // ✅ 关键修复 1：告诉RecyclerView这个列表的大小是固定的，不要因为内容改变而重新请求布局
        // 这能防止左右跳动
        setHasFixedSize(true);
    }

    public void setSelectedPosition(int pos) {
        if (pos < 0 || pos >= getData().size()) return;
        int old = selectedPosition;
        selectedPosition = pos;
        if (old != selectedPosition) {
            if (old >= 0 && old < getData().size()) {
                notifyItemChanged(old);
            }
            notifyItemChanged(selectedPosition);
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        int pos = helper.getAdapterPosition();
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

        // ✅ 核心逻辑：直接设置缩放比例
        // RecyclerView 会自动处理这个属性，不需要 start()，也不会触发 Layout 跳动
        float scale = isSelected ? 1.1f : 1.0f;
        helper.itemView.setScaleX(scale);
        helper.itemView.setScaleY(scale);
        
        // ✅ 关键修复 2：必须设置 Pivot 点为中心
        // 否则 View 会以左上角为轴心缩放，导致视觉位移
        helper.itemView.setPivotX(helper.itemView.getWidth() / 2f);
        helper.itemView.setPivotY(helper.itemView.getHeight() / 2f);

        // 图标逻辑...
        boolean hasFilterSelected = isSelected && !isHomePage && item.filterSelectCount() > 0;
        boolean hasFiltersAvailable = isSelected && !isHomePage && !item.filters.isEmpty() && item.filterSelectCount() <= 0;
        
        helper.setGone(R.id.tvFilterColor, hasFilterSelected);
        helper.setGone(R.id.tvFilter, hasFiltersAvailable);
    }

}
