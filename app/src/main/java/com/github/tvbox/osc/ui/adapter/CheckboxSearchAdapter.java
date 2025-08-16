package com.github.tvbox.osc.ui.adapter;


import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.SearchHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CheckboxSearchAdapter extends ListAdapter<SourceBean, CheckboxSearchAdapter.ViewHolder> {

    public CheckboxSearchAdapter(DiffUtil.ItemCallback<SourceBean> diffCallback) {
        super(diffCallback);
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_checkbox_search, parent, false));
    }

    private void setCheckedSource(HashMap<String, String> checkedSources) {
        mCheckedSources = checkedSources;
    }

    private ArrayList<SourceBean> data = new ArrayList<>();
    public HashMap<String, String> mCheckedSources = new HashMap<>();

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<SourceBean> newData, HashMap<String, String> checkedSources) {
        data.clear();
        data.addAll(newData);
        setCheckedSource(checkedSources);
        notifyDataSetChanged();
    }

    public void setMCheckedSources() {
//        LOG.i(data.size()+"size----size"+mCheckedSources.size());
        SearchHelper.putCheckedSources(mCheckedSources,data.size()==mCheckedSources.size());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
@Override
public void onBindViewHolder(ViewHolder holder, int position) {
    // 数据绑定
    int pos = holder.getAdapterPosition();
    SourceBean sourceBean = data.get(pos);
    holder.oneSearchSource.setText(sourceBean.getName());
    holder.oneSearchSource.setTag(sourceBean);
    
    // 初始化视图状态
    initViewState(holder, sourceBean);
    
    // 设置触摸事件处理
    setupTouchListener(holder);
    
    // 设置点击事件处理
    setupClickListener(holder);
    
    // 设置选中状态监听
    setupCheckedChangeListener(holder, pos, sourceBean);
}

private void initViewState(ViewHolder holder, SourceBean sourceBean) {
    // 重置所有交互状态
    holder.oneSearchSource.setFocusable(false);
    holder.oneSearchSource.setFocusableInTouchMode(false);
    holder.oneSearchSource.clearFocus();
    holder.oneSearchSource.setSelected(false);
    holder.oneSearchSource.setOnCheckedChangeListener(null);
    
    // 初始化选中状态
    if (mCheckedSources != null) {
        boolean isChecked = mCheckedSources.containsKey(sourceBean.getKey());
        holder.oneSearchSource.setChecked(isChecked);
    }
}

private void setupTouchListener(ViewHolder holder) {
    holder.itemView.setOnTouchListener((v, event) -> {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            v.post(() -> {
                holder.oneSearchSource.setFocusable(true);
                holder.oneSearchSource.setFocusableInTouchMode(true);
                if (!holder.oneSearchSource.requestFocus()) {
                    holder.itemView.requestFocusFromTouch();
                }
                holder.oneSearchSource.setSelected(true);
            });
            return true; // 消费事件阻止立即触发点击
        }
        return false;
    });
}

private void setupClickListener(ViewHolder holder) {
    holder.itemView.setOnClickListener(v -> {
        if (holder.oneSearchSource.isFocused()) {
            holder.oneSearchSource.toggle();
        }
    });
}

private void setupCheckedChangeListener(ViewHolder holder, int pos, SourceBean sourceBean) {
    holder.oneSearchSource.setOnCheckedChangeListener((buttonView, isChecked) -> {
        if (mCheckedSources != null) {
            if (isChecked) {
                mCheckedSources.put(sourceBean.getKey(), "1");
            } else {
                mCheckedSources.remove(sourceBean.getKey());
            }
        }
        notifyItemChanged(pos);
    });
}

@Override
public void onViewRecycled(ViewHolder holder) {
    // 清理资源
    holder.itemView.setOnTouchListener(null);
    holder.itemView.setOnClickListener(null);
    holder.oneSearchSource.setOnCheckedChangeListener(null);
}


    public class ViewHolder extends RecyclerView.ViewHolder {
        public CheckBox oneSearchSource;

        public ViewHolder(View view) {
            super(view);
            oneSearchSource = (CheckBox) view.findViewById(R.id.oneSearchSource);
        }
    }

}
