package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 *  xuameng选集列表
 */
public class SeriesAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
    private V7GridLayoutManager mGridLayoutManager;
    public SeriesAdapter(V7GridLayoutManager gridLayoutManager) {
        super(R.layout.item_series, new ArrayList<>());
        this.mGridLayoutManager = gridLayoutManager;
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
        TextView tvSeries = helper.getView(R.id.tvSeries);
        if (item.selected) {
            tvSeries.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        } else {
            tvSeries.setTextColor(Color.WHITE);
        }
        helper.setText(R.id.tvSeries, item.name);

        if (getData().size() == 1 && helper.getLayoutPosition() == 0) {
            helper.itemView.setNextFocusUpId(R.id.mGridViewFlag);
        }

        View mSeriesGroupView = ((Activity) helper.itemView.getContext()).findViewById(R.id.mSeriesGroupView);
        if (getData().size()>1 && mSeriesGroupView != null && mSeriesGroupView.getVisibility() == View.VISIBLE) {
                helper.itemView.setNextFocusUpId(R.id.mSeriesGroupView);
        }

        int spanCount = mGridLayoutManager.getSpanCount();
        int position = helper.getLayoutPosition();
        int totalCount = getData().size();
        int remainder = totalCount % spanCount;
        int lastRowStart = remainder == 0 ? totalCount - spanCount : totalCount - remainder;
        if (position >= lastRowStart) {
            helper.itemView.setNextFocusDownId(R.id.tvPlay);
        }
    }
}
