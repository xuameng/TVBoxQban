package com.github.tvbox.osc.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.base.App;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter;
import com.github.tvbox.osc.ui.dialog.GridFilterDialog;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import me.jessyan.autosize.utils.AutoSizeUtils;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import android.text.TextUtils;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.Stack;
import java.util.List;

/**
 * @author xuameng (modified for draw-crash fix)
 * @description: 修复 ViewGroup.dispatchDraw → ArrayIndexOutOfBoundsException
 *              核心改动：不再 removeView，只做 visibility 切换 + 生命周期守卫
 */
public class GridFragment extends BaseLazyFragment {
    private MovieSort.SortData sortData = null;
    private TvRecyclerView mGridView;
    private SourceViewModel sourceViewModel;
    private GridFilterDialog gridFilterDialog;
    private GridAdapter gridAdapter;
    private int page = 1;
    private int maxPage = 1;
    private boolean isLoad = false;
    private boolean isTop = true;
    private View focusedView = null;
    private boolean isRequesting = false;
    private boolean hasActionItems = false;
    private float pullRefreshStartX;
    private float pullRefreshStartY;
    private boolean pullRefreshStartAtTop = false;
    private boolean pullRefreshReady = false;
    private int pullRefreshThreshold = 0;
    private View loadSirView = null;

    private static class GridInfo {
        public String sortID = "";
        public TvRecyclerView mGridView;
        public GridAdapter gridAdapter;
        public int page = 1;
        public int maxPage = 1;
        public boolean isLoad = false;
        public boolean hasActionItems = false;
        public View focusedView = null;
    }
    Stack<GridInfo> mGrids = new Stack<GridInfo>();

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return new GridFragment().setArguments(sortData);
    }

    public GridFragment setArguments(MovieSort.SortData sortData) {
        this.sortData = sortData;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 生命周期守卫：只在 attached 时操作
        if (!isAdded() || isDetached()) return;

        TvRecyclerView gridView = view.findViewById(R.id.mGridView);
        if (gridView != null && gridView.getLayoutManager() == null) {
            if (isFolederMode()) {
                gridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            } else {
                int spanCount = isBaseOnWidth() ? 5 : 6;
                if (style != null) {
                    spanCount = ImgUtil.spanCountByStyle(style, spanCount);
                }
                if (spanCount == 1) {
                    gridView.setLayoutManager(new V7LinearLayoutManager(mContext, spanCount, false));
                } else {
                    gridView.setLayoutManager(new V7GridLayoutManager(mContext, spanCount));
                }
            }
        }
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    private void changeView(String id) {
        this.sortData.flag = "1";
        initView();
        this.sortData.id = id;
        initViewModel();
        initData();
    }

    public boolean isFolederMode() { return (getUITag() == '1'); }

    public char getUITag() {
        if (style != null && "list".equals(style.type)) {
            return '1';
        }
        if (sortData == null || sortData.flag == null || sortData.flag.length() == 0) {
            return '0';
        }
        char flagChar = sortData.flag.charAt(0);
        return flagChar != '0' ? '1' : flagChar;
    }

    public boolean enableFastSearch() {
        return sortData.flag == null || sortData.flag.length() < 2 || (sortData.flag.charAt(1) == '1');
    }

    // 保存当前页面
    private void saveCurrentView() {
        if (this.mGridView == null) return;
        GridInfo info = new GridInfo();
        info.sortID = this.sortData.id;
        info.mGridView = this.mGridView;
        info.gridAdapter = this.gridAdapter;
        info.page = this.page;
        info.maxPage = this.maxPage;
        info.isLoad = this.isLoad;
        info.hasActionItems = this.hasActionItems;
        info.focusedView = this.focusedView;
        this.mGrids.push(info);
    }

    // ★★★ 核心修复：不再 removeView，只 hide + 恢复旧引用 ★★★
    public boolean restoreView() {
        if (mGrids.empty()) return false;
        if (!isAdded() || isDetached() || isRemoving()) return false;

        this.showSuccess();

        // 把当前 mGridView 标记为废弃（GONE），但不 remove
        if (mGridView != null && mGridView.getParent() != null) {
            mGridView.setVisibility(View.GONE);
        }

        GridInfo info = mGrids.pop();
        this.sortData.id = info.sortID;
        this.mGridView = info.mGridView;
        this.gridAdapter = info.gridAdapter;
        this.page = info.page;
        this.maxPage = info.maxPage;
        this.isLoad = info.isLoad;
        this.hasActionItems = info.hasActionItems;
        this.focusedView = info.focusedView;

        // 恢复旧 View：确保 VISIBLE + bringToFront
        if (this.mGridView != null) {
            this.mGridView.setVisibility(View.VISIBLE);
            ViewGroup parent = (ViewGroup) this.mGridView.getParent();
            if (parent != null) {
                parent.bringChildToFront(this.mGridView);
            }
            this.mGridView.requestFocus();
        }
        return true;
    }

    private ImgUtil.Style style;

    // ★★★ 核心修复：createView 只 add + hide，不 remove ★★★
    private void createView() {
        if (!isAdded() || isDetached()) return;

        this.saveCurrentView();

        if (mGridView == null) {
            mGridView = findViewById(R.id.mGridView);
        } else {
            // 隐藏旧的，不 remove
            mGridView.setVisibility(View.GONE);

            int horizontal = AutoSizeUtils.dp2px(mContext, 20);
            int vertical = AutoSizeUtils.mm2px(mContext, 10);

            TvRecyclerView v3 = new TvRecyclerView(this.mContext);
            v3.setSpacingWithMargins(vertical, horizontal);
            v3.setLayoutParams(mGridView.getLayoutParams());
            v3.setPadding(mGridView.getPaddingLeft(), mGridView.getPaddingTop(),
                    mGridView.getPaddingRight(), mGridView.getPaddingBottom());
            v3.setClipToPadding(mGridView.getClipToPadding());

            // add 到父布局，但不 remove 旧的
            ViewGroup parent = (ViewGroup) mGridView.getParent();
            if (parent != null) {
                parent.addView(v3);
            }
            mGridView = v3;
            mGridView.setVisibility(View.VISIBLE);
        }

        mGridView.setHasFixedSize(true);
        style = ImgUtil.initStyle();
        gridAdapter = new GridAdapter(isFolederMode(), style);
        this.page = 1;
        this.maxPage = 1;
        this.isLoad = false;
    }

    private void initView() {
        // 生命周期守卫
        if (!isAdded() || isDetached()) return;

        this.createView();
        if (isFolederMode()) {
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        } else {
            int spanCount = isBaseOnWidth() ? 5 : 6;
            if (style != null) {
                spanCount = ImgUtil.spanCountByStyle(style, spanCount);
            }
            if (spanCount == 1) {
                mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, spanCount, false));
            } else {
                mGridView.setLayoutManager(new V7GridLayoutManager(mContext, spanCount));
            }
        }
        mGridView.setAdapter(gridAdapter);

        gridAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                gridAdapter.setEnableLoadMore(true);
                sourceViewModel.getList(sortData, page);
            }
        }, mGridView);

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });

        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                return false;
            }
        });

        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (!isAdded()) return; // ★ 守卫
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    if (!TextUtils.isEmpty(video.action)) {
                        sourceViewModel.action(video.sourceKey, video.action);
                        return;
                    }

                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    if (video.tag != null && (video.tag.equals("folder") || video.tag.equals("cover"))) {
                        focusedView = view;
                        changeView(video.id);
                    } else {
                        if (TextUtils.isEmpty(video.id) || video.id.startsWith("msearch:")) {
                            if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) && enableFastSearch()) {
                                jumpActivity(FastSearchActivity.class, bundle);
                            } else {
                                jumpActivity(SearchActivity.class, bundle);
                            }
                        } else {
                            bundle.putString("picture", video.pic);
                            jumpActivity(DetailActivity.class, bundle);
                        }
                    }
                }
            }
        });

        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (!isAdded()) return true;
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    if (!TextUtils.isEmpty(video.action)) {
                        sourceViewModel.action(video.sourceKey, video.action);
                        return true;
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });

        gridAdapter.setLoadMoreView(new LoadMoreView());
        setLoadSir2(mGridView);
        initPullRefresh();
    }

    private void initPullRefresh() {
        if (!isAdded()) return;
        pullRefreshThreshold = ViewConfiguration.get(mContext).getScaledTouchSlop() * 6;
        mGridView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                return handlePullRefreshTouch(rv, event);
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
        loadSirView = (View) mGridView.getParent();
        if (loadSirView != null) {
            loadSirView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return handlePullRefreshTouch(v, event);
                }
            });
        }
    }

    private void bindPullRefreshTouch(View view) {
        if (view == null || view == mGridView) return;
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handlePullRefreshTouch(v, event);
            }
        });
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                bindPullRefreshTouch(group.getChildAt(i));
            }
        }
    }

    private boolean handlePullRefreshTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pullRefreshStartX = event.getX();
                pullRefreshStartY = event.getY();
                pullRefreshStartAtTop = !view.canScrollVertically(-1);
                pullRefreshReady = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float diffX = Math.abs(event.getX() - pullRefreshStartX);
                float diffY = event.getY() - pullRefreshStartY;
                pullRefreshReady = pullRefreshStartAtTop && diffY > pullRefreshThreshold && diffY > diffX;
                break;
            case MotionEvent.ACTION_UP:
                if (pullRefreshReady) {
                    pullRefreshReady = false;
                    forceRefresh();
                    App.showToastShort(getContext(), "页面刷新！");
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                pullRefreshReady = false;
                break;
        }
        return false;
    }

    @Override
    protected void showEmpty() {
        super.showEmpty();
        bindPullRefreshTouch(loadSirView);
    }

    private void initViewModel() {
        if (sourceViewModel != null) return;
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (!isAdded() || isDetached()) return; // ★ 守卫
                isRequesting = false;
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    if (page == 1) {
                        showSuccess();
                        isLoad = true;
                        hasActionItems = hasActionVideo(absXml.movie.videoList);
                        gridAdapter.setNewData(absXml.movie.videoList);
                    } else {
                        hasActionItems = hasActionItems || hasActionVideo(absXml.movie.videoList);
                        gridAdapter.addData(absXml.movie.videoList);
                    }
                    page++;
                    maxPage = absXml.movie.pagecount;
                    if (maxPage > 0 && page > maxPage) {
                        gridAdapter.loadMoreEnd();
                        gridAdapter.setEnableLoadMore(false);
                        if (page > 2) App.showToastShort(getContext(), "没有更多了！");
                    } else {
                        gridAdapter.loadMoreComplete();
                        gridAdapter.setEnableLoadMore(true);
                    }
                } else {
                    if (page == 1) {
                        hasActionItems = false;
                        showEmpty();
                    } else if (page > 2) {
                        App.showToastShort(getContext(), "没有更多了！");
                    }
                    gridAdapter.loadMoreEnd();
                    gridAdapter.setEnableLoadMore(false);
                }
            }
        });

        sourceViewModel.actionResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject jsonObject) {
                if (!isAdded()) return;
                if (jsonObject == null) return;
                String msg = jsonObject.optString("msg");
                if (!msg.isEmpty()) App.showToastShort(getContext(), msg);
            }
        });
    }

    public boolean shouldReloadOnSelect() {
        return !isRequesting && mGrids.empty() && (hasActionItems || !isLoad);
    }

    public boolean isLoad() {
        return isLoad || !mGrids.empty();
    }

    private void initData() {
        if (!isAdded()) return;
        showLoading();
        isRequesting = true;
        isLoad = false;
        hasActionItems = false;
        scrollTop();
        sourceViewModel.getList(sortData, page);
    }

    private boolean hasActionVideo(List<Movie.Video> videos) {
        if (videos == null) return false;
        for (Movie.Video video : videos) {
            if (video != null && video.action != null) return true;
        }
        return false;
    }

    public boolean isTop() {
        return isTop;
    }

    public void scrollTop() {
        isTop = true;
        if (mGridView == null) return;
        mGridView.scrollToPosition(0);
    }

    public void showFilter() {
        if (mContext == null || !isAdded()) return;
        if (!sortData.filters.isEmpty() && gridFilterDialog == null) {
            gridFilterDialog = new GridFilterDialog(mContext);
        }
        if (gridFilterDialog != null)
            gridFilterDialog.show();
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus)
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            else
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
        }
    };

    public void setFilterDialogData() {
        if (!isAdded()) return;
        Context context = getContext();
        if (context == null) return;
        LayoutInflater inflater = LayoutInflater.from(context);
        final int defaultColor = ContextCompat.getColor(context, R.color.color_FFFFFF);
        final int selectedColor = ContextCompat.getColor(context, R.color.color_02F8E1);

        for (MovieSort.SortFilter filter : sortData.filters) {
            View line = inflater.inflate(R.layout.item_grid_filter, gridFilterDialog.filterRoot, false);
            TextView filterNameTv = line.findViewById(R.id.filterName);
            filterNameTv.setText(filter.name);
            TvRecyclerView gridView = line.findViewById(R.id.mFilterKv);
            gridView.setId(View.generateViewId());
            gridView.setHasFixedSize(true);
            gridView.setLayoutManager(new V7LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));

            final String key = filter.key;
            final ArrayList<String> values = new ArrayList<>(filter.values.keySet());
            final ArrayList<String> keys = new ArrayList<>(filter.values.values());

            GridFilterKVAdapter adapter = new GridFilterKVAdapter(defaultColor, selectedColor);
            String currentSelected = sortData.filterSelect.get(key);
            int selectedPosition = -1;
            if (currentSelected != null) {
                selectedPosition = keys.indexOf(currentSelected);
            }
            adapter.setSelectedPosition(selectedPosition);

            gridView.setAdapter(adapter);
            adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                    if (!isAdded()) return;
                    GridFilterKVAdapter kvAdapter = (GridFilterKVAdapter) adapter;
                    String currentSelection = sortData.filterSelect.get(key);
                    String newSelection = keys.get(position);

                    if (currentSelection == null || !currentSelection.equals(newSelection)) {
                        sortData.filterSelect.put(key, newSelection);
                        kvAdapter.setSelectedPosition(position);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_FILTER_CHANGE));
                    } else {
                        sortData.filterSelect.remove(key);
                        kvAdapter.setSelectedPosition(-1);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_FILTER_CHANGE));
                    }
                    forceRefresh();
                }
            });
            adapter.setNewData(values);
            gridFilterDialog.filterRoot.addView(line);
        }
    }

    public void forceRefresh() {
        if (mGridView == null || gridAdapter == null || sourceViewModel == null) return;
        if (!isAdded() || isDetached()) return; // ★ 守卫
        if (isRequesting) {
            App.showToastShort(getContext(), "数据加载中，请稍候！");
            return;
        }
        if (!isLoad) {
            showLoading();
        }
        page = 1;
        isRequesting = true;
        isLoad = false;
        hasActionItems = false;
        scrollTop();
        sourceViewModel.getList(sortData, page);
    }

    // ★★★ 关键：Fragment 销毁时统一清理，防止悬空引用 ★★★
    @Override
    public void onDestroyView() {
        // 把所有栈里的 View 也标记为 GONE，防止父布局持有
        for (GridInfo info : mGrids) {
            if (info.mGridView != null && info.mGridView.getParent() != null) {
                info.mGridView.setVisibility(View.GONE);
            }
        }
        mGrids.clear();
        if (mGridView != null && mGridView.getParent() != null) {
            mGridView.setVisibility(View.GONE);
            // 注意：这里不 removeView，让父布局自然回收
            mGridView = null;
        }
        gridAdapter = null;
        super.onDestroyView();
    }
}
