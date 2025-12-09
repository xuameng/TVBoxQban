package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.TextView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedPosition = -1;
    private int focusedPosition = -1;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        
        TextView textView = helper.getView(R.id.tvTitle);
        int currentPosition = helper.getAdapterPosition();
        
        // 设置焦点变化监听
        helper.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    focusedPosition = currentPosition;
                    // 当获得焦点时，自动设置为选中状态
                    setSelectedPosition(currentPosition);
                }
            }
        });

        // 根据状态更新样式
        if (currentPosition == selectedPosition) {
            // 选中项样式
            textView.getPaint().setFakeBoldText(true);
            textView.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
            helper.itemView.setScaleX(1.1f);
            helper.itemView.setScaleY(1.1f);
        } else {
            // 非选中项样式
            textView.getPaint().setFakeBoldText(false);
            textView.setTextColor(mContext.getResources().getColor(R.color.color_BBFFFFFF));
            helper.itemView.setScaleX(1.0f);
            helper.itemView.setScaleY(1.0f);
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
    
    public int getFocusedPosition() {
        return focusedPosition;
    }
}
