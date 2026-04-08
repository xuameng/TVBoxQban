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
 * @description: 首页分类菜单 Adapter（修复焦点及多高亮等BUG）
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {

    // ✅ 唯一真实选中状态
    private int selectedPosition = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    /**
     * ✅ 外部设置选中项（焦点 / 滑动 / 点击 统一入口）
     */
    public void setSelectedPosition(int pos) {
        if (pos < 0 || pos >= getData().size()) return;

        int old = selectedPosition;
        selectedPosition = pos;

        notifyItemChanged(old);  //更新上一个选中项状态
        notifyItemChanged(selectedPosition);  //更新当前选中项状态
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        int pos = helper.getAdapterPosition();
        boolean isSelected = pos == selectedPosition;
        
        // 主页（位置0）不显示筛选图标
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

        helper.itemView.setPivotX(helper.itemView.getWidth() / 2f);
        helper.itemView.setPivotY(helper.itemView.getHeight() / 2f);
        helper.itemView.animate()
                .scaleX(isSelected ? 1.1f : 1.0f)
                .scaleY(isSelected ? 1.1f : 1.0f)
                .setDuration(250)
                .start();

        // ✅ filter icon 完全由 Adapter 控制 
        // 修复：主页不显示筛选图标，其他页面按规则显示  hasUserFilter为用户点击  filterSelectCount是用户筛选的项
        boolean showFilterColor = isSelected && !isHomePage && item.hasUserFilter && item.filterSelectCount() > 0;
        boolean showFilterNormal = isSelected && !isHomePage && !item.hasUserFilter && !item.filters.isEmpty();
        helper.setGone(R.id.tvFilterColor, showFilterColor);
        helper.setGone(R.id.tvFilter, showFilterNormal);
    }
}
