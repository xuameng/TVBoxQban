package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedPosition = -1;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        
        TextView textView = helper.getView(R.id.tvTitle);
        int currentPosition = helper.getAdapterPosition();
        
        // 根据状态更新样式和动画
        if (currentPosition == selectedPosition) {
            // 选中项样式 - 添加动画效果
            helper.itemView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setInterpolator(new BounceInterpolator())
                    .setDuration(250)
                    .start();
            textView.getPaint().setFakeBoldText(true);
            textView.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
        } else {
            // 非选中项样式 - 添加动画效果
            helper.itemView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(250)
                    .start();
            textView.getPaint().setFakeBoldText(false);
            textView.setTextColor(mContext.getResources().getColor(R.color.color_BBFFFFFF));
        }
        textView.invalidate();
    }
    
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }
    
    public int getSelectedPosition() {
        return selectedPosition;
    }
}
