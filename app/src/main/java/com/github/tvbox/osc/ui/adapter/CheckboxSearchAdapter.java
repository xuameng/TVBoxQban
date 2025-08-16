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
import android.view.MotionEvent;

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
    public void onBindViewHolder(ViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        SourceBean sourceBean = data.get(pos);
    // 初始状态禁止焦点
    holder.oneSearchSource.setFocusable(false);
    holder.oneSearchSource.setFocusableInTouchMode(false);

        holder.oneSearchSource.setText(sourceBean.getName());
        holder.oneSearchSource.setOnCheckedChangeListener(null);
        if (mCheckedSources != null) {
            holder.oneSearchSource.setChecked(mCheckedSources.containsKey(sourceBean.getKey()));
        }
        holder.oneSearchSource.setTag(sourceBean);

    // 父容器触摸监听
    holder.itemView.setOnTouchListener((v, event) -> {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 触摸时启用焦点
            holder.oneSearchSource.setFocusableInTouchMode(true);
            holder.oneSearchSource.requestFocus();
        }
        return false;
    });

        holder.oneSearchSource.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mCheckedSources.put(sourceBean.getKey(), "1");
                } else {
                    mCheckedSources.remove(sourceBean.getKey());
                }
                notifyItemChanged(pos);
            // 操作后恢复无焦点状态
            buttonView.setFocusable(false);
            buttonView.setFocusableInTouchMode(false);
            }
        });

    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public CheckBox oneSearchSource;

        public ViewHolder(View view) {
            super(view);
            oneSearchSource = (CheckBox) view.findViewById(R.id.oneSearchSource);
        }
    }

}
