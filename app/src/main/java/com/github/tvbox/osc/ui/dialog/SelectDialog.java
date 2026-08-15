package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.GridLayoutManager;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {
    private TvRecyclerView tvRecyclerView;   // xuameng
    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback,
                           List<T> data, int select) {
        final int selectIdx = select;
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);
        tvRecyclerView = findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        if (select<5){
            tvRecyclerView.setSelection(select);
        }

        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 5) {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                    safeSelecttvRecyclerView(selectIdx);  //xuameng滚动调整
                }
            }
        });
    }

    private void safeSelecttvRecyclerView(int i) {     //xuameng滚动调整
        if (tvRecyclerView == null) return;  
        // 检查 RecyclerView 是否处于安全状态
        if (tvRecyclerView.isComputingLayout() || tvRecyclerView.isScrolling()) {
            // 延迟执行，避免在布局计算或滚动过程中操作
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    safeSelecttvRecyclerView(i); 
                }
            }, 20);
            return;
        }
        tvRecyclerView.setSelection(i);
    }

}
