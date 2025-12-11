package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import android.widget.TextView;

import java.util.ArrayList;

public class GridFilterKVAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private int selectedPosition = -1;
    private int defaultColor;
    private int selectedColor;
    
    public GridFilterKVAdapter(int defaultColor, int selectedColor) {
        super(R.layout.item_grid_filter_value, new ArrayList<>());
        this.defaultColor = defaultColor;
        this.selectedColor = selectedColor;
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.filterValue, item);
        
        TextView valueTv = helper.getView(R.id.filterValue);
        if (helper.getAdapterPosition() == selectedPosition) {         //xuameng新增方法修正多个高亮BUG
            valueTv.getPaint().setFakeBoldText(true);
            valueTv.setTextColor(selectedColor);
        } else {
            valueTv.getPaint().setFakeBoldText(false);           //xuameng新增方法修正多个高亮BUG
            valueTv.setTextColor(defaultColor);
        }
    }
    
    public void setSelectedPosition(int position) {    //xuameng新增方法修正多个高亮BUG
        this.selectedPosition = position;
    }
    
    public int getSelectedPosition() {    //xuameng新增方法修正多个高亮BUG
        return selectedPosition;
    }
}


