package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567 / xuameng
 * @date 2020/12/21
 * @description: 首页分类菜单 Adapter（支持焦点 & 手机滑动）
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

        notifyItemChanged(old);
        notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

@Override
protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
    int pos = helper.getAdapterPosition();
    boolean isSelected = pos == selectedPosition;

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
    boolean showFilter = isSelected && item.filterSelectCount() > 0;
    helper.setGone(R.id.tvFilterColor, showFilter);
    helper.setGone(R.id.tvFilter, !showFilter);
}
}
