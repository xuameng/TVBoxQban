package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class GridFilterKVAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private int selectedPosition = -1;
    
    public GridFilterKVAdapter() {
        super(R.layout.item_grid_filter_value, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.filterValue, item);
    }
    
    public void setSelectedPosition(int position) {       //xuameng新增方法
        this.selectedPosition = position;
        notifyDataSetChanged();
    }
    
    public int getSelectedPosition() {   //xuameng新增方法
        return selectedPosition;
    }
}
