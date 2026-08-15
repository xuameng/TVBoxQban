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
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {
    private TvRecyclerView tvRecyclerView;   // xuameng
    private V7GridLayoutManager mEpisodeLayoutManager;   //xuameng滚动调整
    private androidx.recyclerview.widget.LinearSmoothScroller smoothScroller;  //xuameng滚动调整
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

        smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(getContext()) {
            @Override
            protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                // 数值越大滚得越快
                return 100f / displayMetrics.densityDpi;
            }

            @Override
            public android.graphics.PointF computeScrollVectorForPosition(int targetPosition) {
                return mEpisodeLayoutManager.computeScrollVectorForPosition(targetPosition);
            }
        };

        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 5) {
                    customEpisodeScrollPos(selectIdx);
                    tvRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if (newState == tvRecyclerView.SCROLL_STATE_IDLE) {   //xuameng剧集滚动完成后焦点选择为剧集
                                // 滚动已经停止，执行你需要的操作
                                //	mGridView.requestFocus();
                                safeSelecttvRecyclerView(selectIdx);
                                tvRecyclerView.removeOnScrollListener(this);    //xuameng删除滚动监听
                            }
                        }
                    });
                    safeSelecttvRecyclerView(selectIdx);  //xuameng滚动调整
                }
            }
        });
    }

    private void customEpisodeScrollPos(int targetPos) { //xuameng滚动调整
        // LayoutManager 还没准备好就延迟重试
        if (mEpisodeLayoutManager == null || tvRecyclerView == null) {
            tvRecyclerView.postDelayed(() -> customEpisodeScrollPos(targetPos), 100);
            return;
        }

        // 快速跳过去（无动画，瞬间到位）
        mEpisodeLayoutManager.scrollToPositionWithOffset(
                targetPos > 10 ? targetPos - 10 : 0, 0
        );

        // 用 SmoothScroller 做短距离微调（看起来很顺）
        tvRecyclerView.postDelayed(() -> {
            if (mEpisodeLayoutManager != null && smoothScroller != null) {
                smoothScroller.setTargetPosition(targetPos);
                mEpisodeLayoutManager.startSmoothScroll(smoothScroller);
                tvRecyclerView.smoothScrollToPosition(targetPos);
            }
        }, 50);
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
