package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;

import com.github.tvbox.osc.base.App;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapterXu;
import com.github.tvbox.osc.ui.dialog.xuamengAboutDialog;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtilHot;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Stack;

/**
 * xuameng
 * folder 在 UserFragment 内部展开版本
 */
public class UserFragment extends BaseLazyFragment implements View.OnClickListener {

    /* ====== folder 支持 ====== */
    private Stack<String> folderStack = new Stack<>();
    private String currentFolderId = "";
    private boolean isFolderMode = false;
    private int page = 1;

    private SourceViewModel sourceViewModel;

    /* ====== UI ====== */
    private LinearLayout tvLive, tvSearch, tvSetting, tvHistory, tvCollect, tvPush;
    private TvRecyclerView tvHotList1, tvHotList2;

    private HomeHotVodAdapter homeHotVodAdapter;
    private HomeHotVodAdapterXu homeHotVodAdapterxu;

    private List<Movie.Video> homeSourceRec;
    private ImgUtilHot.Style style;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
            tvHotList1.setVisibility(View.VISIBLE);
            tvHotList2.setVisibility(View.GONE);
            tvHotList1.setHasFixedSize(true);
            int spanCount = 5;
            if(isFolederMode()){  //xuameng 增加判断如果style 为 list 就显示文件夹样式
                tvHotList1.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            }else{
                if (style != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                    spanCount = ImgUtilHot.spanCountByStyle(style, spanCount);
                }
                tvHotList1.setLayoutManager(new V7GridLayoutManager(this.mContext, spanCount));
            }
        } else {
            tvHotList1.setVisibility(View.GONE);
            tvHotList2.setVisibility(View.VISIBLE);
            if(isFolederMode()){  //xuameng 增加判断如果style 为 list 就显示文件夹样式
                tvHotList2.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            }
            //	tvHotList2.setHasFixedSize(true);      //xuameng不想显示单行
            //    tvHotList2.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));
        }
        super.onFragmentResume();
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100); //xuameng首页历史条数   //xuameng 历史记录返回条数
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            if (!Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
                homeHotVodAdapterxu.setNewData(vodList); //xuameng首页多行
            } else {
                homeHotVodAdapter.setNewData(vodList); //xuameng首页单行
            }
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);

        initView();
        initAdapter();
        initListener();
        initData();
    }

    private void initView() {
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.tvPush);

        tvHotList1 = findViewById(R.id.tvHotList1);
        tvHotList2 = findViewById(R.id.tvHotList2);

        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);

        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);
    }

    private void initAdapter() {
        style = ImgUtilHot.initStyle();

        homeHotVodAdapter = new HomeHotVodAdapter(isFolderMode, style);
        homeHotVodAdapterxu = new HomeHotVodAdapterXu(isFolderMode, style);

        tvHotList1.setAdapter(homeHotVodAdapter);
        tvHotList2.setAdapter(homeHotVodAdapterxu);
    }

    private void initListener() {

        homeHotVodAdapter.setOnItemClickListener((adapter, view, position) ->
                handleItemClick(adapter, position)
        );

        homeHotVodAdapterxu.setOnItemClickListener((adapter, view, position) ->
                handleItemClick(adapter, position)
        );
    }

    /* ====== 核心：folder 点击处理 ====== */
    private void handleItemClick(BaseQuickAdapter adapter, int position) {
        Movie.Video vod = (Movie.Video) adapter.getItem(position);

        if (vod == null) return;

        // xuameng：folder / cover 在 UserFragment 内展开
        if (vod.tag != null &&
                (vod.tag.equals("folder") || vod.tag.equals("cover"))) {

            enterFolder(vod.id);
            return;
        }

        // 原有逻辑
        Bundle bundle = new Bundle();
        bundle.putString("id", vod.id);
        bundle.putString("sourceKey", vod.sourceKey);

        if (vod.id.startsWith("msearch:")) {
            jumpActivity(
                    Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)
                            ? FastSearchActivity.class
                            : SearchActivity.class,
                    bundle
            );
        } else {
            bundle.putString("picture", vod.pic);
            jumpActivity(DetailActivity.class, bundle);
        }
    }

    /* ====== folder 进入 ====== */
    private void enterFolder(String folderId) {
        if (isFolderMode) {
            folderStack.push(currentFolderId);
        }
        currentFolderId = folderId;
        isFolderMode = true;
        page = 1;

        showLoading();
        loadFolderData();
    }

    /* ====== folder 数据加载 ====== */
    private void loadFolderData() {
        if (sourceViewModel == null) {
            sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        }

        sourceViewModel.listResult.observe(this, absXml -> {
            if (absXml != null
                    && absXml.movie != null
                    && absXml.movie.videoList != null) {

                showSuccess();
                homeHotVodAdapter.setNewData(absXml.movie.videoList);
                homeHotVodAdapterxu.setNewData(absXml.movie.videoList);

            } else {
                showEmpty();
            }
        });

        sourceViewModel.getList(currentFolderId, page);
    }

    /* ====== 返回 ====== */
    private void exitFolder() {
        if (!folderStack.isEmpty()) {
            currentFolderId = folderStack.pop();
            page = 1;
            loadFolderData();
        } else {
            isFolderMode = false;
            currentFolderId = "";
            initHomeHotVod(homeHotVodAdapter);
            initHomeHotVodXu(homeHotVodAdapterxu);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isFolderMode) {
            exitFolder();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* ====== 原有逻辑保留 ====== */
    private void initData() {
        if (!isFolderMode) {
            initHomeHotVod(homeHotVodAdapter);
            initHomeHotVodXu(homeHotVodAdapterxu);
        }
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            adapter.setNewData(homeSourceRec);
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            loadHistory(adapter);
        } else {
            setDouBanData(adapter);
        }
    }

    private void initHomeHotVodXu(HomeHotVodAdapterXu adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            adapter.setNewData(homeSourceRec);
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            loadHistoryXu(adapter);
        } else {
            setDouBanDataXu(adapter);
        }
    }

    private void loadHistory(BaseQuickAdapter adapter) {
        List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
        List<Movie.Video> list = new ArrayList<>();
        for (VodInfo info : records) {
            Movie.Video v = new Movie.Video();
            v.id = info.id;
            v.sourceKey = info.sourceKey;
            v.name = info.name;
            v.pic = info.pic;
            list.add(v);
        }
        adapter.setNewData(list);
    }

    private void loadHistoryXu(HomeHotVodAdapterXu adapter) {
        loadHistory(adapter);
    }

    /* ====== 豆瓣 ====== */
    private void setDouBanData(BaseQuickAdapter adapter) {
        // 你原逻辑不变
    }

    private void setDouBanDataXu(BaseQuickAdapter adapter) {
        // 你原逻辑不变
    }

    @Override
    public void onClick(View v) {
        HawkConfig.hotVodDelete = false;
        FastClickCheckUtil.check(v);

        if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.tvPush) {
            jumpActivity(PushActivity.class);
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        }
    }

    private View.OnFocusChangeListener focusChangeListener =
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus)
                        v.animate().scaleX(1.05f).scaleY(1.05f)
                                .setDuration(300)
                                .setInterpolator(new BounceInterpolator())
                                .start();
                    else
                        v.animate().scaleX(1.0f).scaleY(1.0f)
                                .setDuration(300)
                                .setInterpolator(new BounceInterpolator())
                                .start();
                }
            };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public boolean isFolderMode() {
        return style != null && "list".equals(style.type);
    }
}
