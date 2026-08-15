package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EpisodeDialog extends BaseDialog {
    public interface EpisodeSelectListener {
        void selectEpisode(int position);
    }

    private final List<VodInfo.VodSeries> episodes;
    private final int selectedPosition;
    private final EpisodeSelectListener listener;

    private V7GridLayoutManager mEpisodeLayoutManager;
    private androidx.recyclerview.widget.LinearSmoothScroller smoothScroller;

    public EpisodeDialog(@NonNull @NotNull Context context, String title, List<VodInfo.VodSeries> episodes, int selectedPosition, EpisodeSelectListener listener) {
        super(context);
        if (context instanceof Activity) setOwnerActivity((Activity) context);
        this.episodes = episodes == null ? new ArrayList<>() : new ArrayList<>(episodes);
        this.selectedPosition = Math.max(0, Math.min(selectedPosition, Math.max(0, this.episodes.size() - 1)));
        this.listener = listener;
        setContentView(R.layout.dialog_episode);
        init(title);
    }

    private void init(String title) {
        TextView episodeTitle = findViewById(R.id.episode_title);
        episodeTitle.setText(title);
        findViewById(R.id.episode_dismiss).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        TvRecyclerView episodeList = findViewById(R.id.episode_list);
        EpisodeAdapter adapter = new EpisodeAdapter(selectedPosition);
        episodeList.setHasFixedSize(true);
        V7GridLayoutManager layoutManager = new V7GridLayoutManager(getContext(), 1);
        episodeList.setLayoutManager(layoutManager);
        episodeList.setAdapter(adapter);
        adapter.setNewData(episodes);
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                dismiss();
                if (listener != null) listener.selectEpisode(position);
            }
        });

mEpisodeLayoutManager = layoutManager;

smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(getContext()) {
    @Override
    protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
        // 数值越大滚得越快，原 DetailActivity 是 100f
        return 100f / displayMetrics.densityDpi;
    }

    @Override
    public android.graphics.PointF computeScrollVectorForPosition(int targetPosition) {
        return mEpisodeLayoutManager.computeScrollVectorForPosition(targetPosition);
    }
};

episodeList.post(new Runnable() {
    @Override
    public void run() {
        layoutManager.setSpanCount(getSpanCount(episodeList.getWidth()));

        customEpisodeScrollPos(selectedPosition);

        episodeList.setSelectedPosition(selectedPosition);
        episodeList.requestFocus();
    }
});
    }

private void customEpisodeScrollPos(int targetPos) {
    // LayoutManager 还没准备好就延迟重试
    if (mEpisodeLayoutManager == null || episodeList == null) {
        episodeList.postDelayed(() -> customEpisodeScrollPos(targetPos), 100);
        return;
    }

    // 1️⃣ 先快速跳过去（无动画，瞬间到位）
    mEpisodeLayoutManager.scrollToPositionWithOffset(
            targetPos > 10 ? targetPos - 10 : 0,
            0
    );

    // 2️⃣ 再用 SmoothScroller 做短距离微调（看起来很顺）
    episodeList.postDelayed(() -> {
        if (mEpisodeLayoutManager != null && smoothScroller != null) {
            smoothScroller.setTargetPosition(targetPos);
            mEpisodeLayoutManager.startSmoothScroll(smoothScroller);
        }
    }, 50);
}

    private int getSpanCount(int gridWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(getContext().getResources().getDimension(R.dimen.ts_22));
        Rect bounds = new Rect();
        int maxTextWidth = 1;
        for (VodInfo.VodSeries episode : episodes) {
            String name = episode == null || episode.name == null ? "" : episode.name;
            paint.getTextBounds(name, 0, name.length(), bounds);
            maxTextWidth = Math.max(maxTextWidth, bounds.width());
        }
        int itemPadding = getContext().getResources().getDimensionPixelSize(R.dimen.vs_10) * 2;
        int itemMargin = getContext().getResources().getDimensionPixelSize(R.dimen.vs_5) * 2;
        int itemWidth = maxTextWidth + itemPadding + itemMargin;
        return Math.max(1, Math.min(4, gridWidth / Math.max(1, itemWidth)));
    }

    @Override
    public void show() {
        super.show();
        if (!isShowing()) return;
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private static class EpisodeAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
        private final int selectedPosition;

        EpisodeAdapter(int selectedPosition) {
            super(R.layout.item_series, new ArrayList<VodInfo.VodSeries>());
            this.selectedPosition = selectedPosition;
        }

        @Override
        protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
            TextView series = helper.getView(R.id.tvSeries);
            series.setText(item == null ? "" : item.name);
            series.setTextColor(helper.getLayoutPosition() == selectedPosition
                    ? mContext.getResources().getColor(R.color.color_02F8E1)
                    : Color.WHITE);
        }
    }
}
