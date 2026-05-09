package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.*;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapterXu;
import com.github.tvbox.osc.ui.dialog.xuamengAboutDialog;
import com.github.tvbox.osc.util.*;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.io.File;
import java.util.ArrayList;

public class UserFragment extends BaseLazyFragment implements View.OnClickListener {

    private LinearLayout tvLive, tvSearch, tvSetting, tvHistory, tvCollect, tvPush;
    public static HomeHotVodAdapter homeHotVodAdapter;
    public static HomeHotVodAdapterXu homeHotVodAdapterxu;
    public static TvRecyclerView tvHotList1, tvHotList2;

    private List<Movie.Video> homeSourceRec;
    private ImgUtilHot.Style style;
    private boolean mHasLoaded = false;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    /* ================= 懒加载核心 ================= */

    @Override
    protected void onFragmentResume() {
        if (mHasLoaded) {
            super.onFragmentResume();
            return;
        }
        mHasLoaded = true;

        style = ImgUtilHot.initStyle();

        if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
            tvHotList1.setVisibility(View.VISIBLE);
            tvHotList2.setVisibility(View.GONE);
            initHotList1();
        } else {
            tvHotList1.setVisibility(View.GONE);
            tvHotList2.setVisibility(View.VISIBLE);
            initHotList2();
        }

        initHomeHotVod(homeHotVodAdapter);
        initHomeHotVodXu(homeHotVodAdapterxu);

        super.onFragmentResume();
    }

    /* ================= UI 初始化 ================= */

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);

        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.tvPush);
        tvCollect = findViewById(R.id.tvFavorite);

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

        tvHotList1 = findViewById(R.id.tvHotList1);
        tvHotList2 = findViewById(R.id.tvHotList2);

        homeHotVodAdapter = new HomeHotVodAdapter(isFolederMode(), style);
        homeHotVodAdapterxu = new HomeHotVodAdapterXu(isFolederMode(), style);

        tvHotList1.setAdapter(homeHotVodAdapter);
        tvHotList2.setAdapter(homeHotVodAdapterxu);

        initListeners();
        initItemClick();
    }

    /* ================= 业务方法 ================= */

    private void initHotList1() {
        tvHotList1.setHasFixedSize(true);
        int spanCount = 5;
        if (isFolederMode()) {
            tvHotList1.setLayoutManager(new V7LinearLayoutManager(mContext, 1, false));
        } else {
            if (style != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                spanCount = ImgUtilHot.spanCountByStyle(style, spanCount);
            }
            tvHotList1.setLayoutManager(new V7GridLayoutManager(mContext, spanCount));
        }
    }

    private void initHotList2() {
        if (isFolederMode()) {
            tvHotList2.setLayoutManager(new V7LinearLayoutManager(mContext, 1, false));
        }
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            loadHistory(adapter);
            return;
        }
        setDouBanData(adapter);
    }

    private void initHomeHotVodXu(HomeHotVodAdapterXu adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            loadHistory(adapter);
            return;
        }
        setDouBanDataXu(adapter);
    }

    private void loadHistory(BaseQuickAdapter adapter) {
        List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
        List<Movie.Video> list = new ArrayList<>();
        for (VodInfo info : records) {
            Movie.Video vod = new Movie.Video();
            vod.id = info.id;
            vod.sourceKey = info.sourceKey;
            vod.name = info.name;
            vod.pic = info.pic;
            vod.note = "上次看到 " + info.playNote;
            list.add(vod);
        }
        adapter.setNewData(list);
    }
private void setDouBanData(HomeHotVodAdapter adapter) {
    try {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DATE);
        String today = String.format("%d%d%d", year, month, day);
        String requestDay = Hawk.get("home_hot_day", "");

        if (requestDay.equals(today)) {
            String json = Hawk.get("home_hot", "");
            if (!json.isEmpty()) {
                adapter.setNewData(loadHots(json));
                return;
            }
        }

        String doubanUrl =
            "https://movie.douban.com/j/new_search_subjects?" +
            "sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;

        OkGo.<String>get(doubanUrl)
            .headers("User-Agent", UA.randomOne())
            .execute(new AbsCallback<String>() {

                @Override
                public void onSuccess(Response<String> response) {
                    String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    mActivity.runOnUiThread(() ->
                        adapter.setNewData(loadHots(netJson))
                    );
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
    } catch (Throwable th) {
        th.printStackTrace();
    }
}

private void setDouBanDataXu(HomeHotVodAdapterXu adapter) {
    try {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DATE);
        String today = String.format("%d%d%d", year, month, day);
        String requestDay = Hawk.get("home_hot_day", "");

        if (requestDay.equals(today)) {
            String json = Hawk.get("home_hot", "");
            if (!json.isEmpty()) {
                adapter.setNewData(loadHots(json));
                return;
            }
        }

        String doubanUrl =
            "https://movie.douban.com/j/new_search_subjects?" +
            "sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;

        OkGo.<String>get(doubanUrl)
            .headers("User-Agent", UA.randomOne())
            .execute(new AbsCallback<String>() {

                @Override
                public void onSuccess(Response<String> response) {
                    String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    mActivity.runOnUiThread(() ->
                        adapter.setNewData(loadHots(netJson))
                    );
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
    } catch (Throwable th) {
        th.printStackTrace();
    }
}

private ArrayList<Movie.Video> loadHots(String json) {
    ArrayList<Movie.Video> result = new ArrayList<>();
    try {
        JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
        JsonArray array = infoJson.getAsJsonArray("data");
        for (JsonElement ele : array) {
            JsonObject obj = (JsonObject) ele;
            Movie.Video vod = new Movie.Video();
            vod.name = obj.get("title").getAsString();
            vod.note = obj.get("rate").getAsString() + " 分";
            vod.pic = obj.get("cover").getAsString()
                + "@User-Agent=" + UA.randomOne()
                + "@Referer=https://www.douban.com/";
            result.add(vod);
        }
    } catch (Throwable ignored) {}
    return result;
}
    /* ================= 事件 ================= */

    private void initListeners() {
        findViewById(R.id.tvHistory).setOnLongClickListener(v -> {
            FastClickCheckUtil.check(v);
            restartHomeActivity();
            return true;
        });

        findViewById(R.id.tvxuameng).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new xuamengAboutDialog(mActivity).show();
        });

        findViewById(R.id.tvxuameng).setOnLongClickListener(v -> {
            FastClickCheckUtil.check(v);
            DefaultConfig.restartApp();
            return true;
        });

        findViewById(R.id.tvSetting).setOnLongClickListener(v -> {
            FastClickCheckUtil.check(v);
            clearCache();
            return true;
        });
    }

    private void initItemClick() {
        homeHotVodAdapter.setOnItemClickListener(this::handleItemClick);
        homeHotVodAdapterxu.setOnItemClickListener(this::handleItemClick);

        homeHotVodAdapter.setOnItemLongClickListener(this::handleItemLongClick);
        homeHotVodAdapterxu.setOnItemLongClickListener(this::handleItemLongClick);
    }

    private void handleItemClick(BaseQuickAdapter adapter, View view, int position) {
        Movie.Video vod = (Movie.Video) adapter.getItem(position);
        if (vod == null) return;

        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2 && HawkConfig.hotVodDelete) {
            deleteRecord(vod, adapter, position);
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString("id", vod.id);
        bundle.putString("sourceKey", vod.sourceKey);

        if (vod.id.startsWith("msearch:") || "folder".equals(vod.tag) || "cover".equals(vod.tag)) {
            bundle.putString("title", vod.name);
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

    private boolean handleItemLongClick(BaseQuickAdapter adapter, View view, int position) {
        Movie.Video vod = (Movie.Video) adapter.getItem(position);
        if (vod == null) return false;

        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
            adapter.notifyDataSetChanged();
        } else {
            Bundle bundle = new Bundle();
            bundle.putString("title", vod.name);
            jumpActivity(FastSearchActivity.class, bundle);
        }
        return true;
    }

    /* ================= 工具方法 ================= */

    private void deleteRecord(Movie.Video vod, BaseQuickAdapter adapter, int position) {
        VodInfo info = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
        RoomDataManger.deleteVodRecord(vod.sourceKey, info);
        adapter.remove(position);
        App.showToastShort(mContext, "已删除当前记录！");
    }

    private void restartHomeActivity() {
        Intent intent = new Intent(mContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("useCache", true);
        startActivity(intent);
        App.showToastShort(mContext, "重新加载主页数据！");
    }

    private void clearCache() {
        new Thread(() -> {
            try {
                FileUtils.cleanDirectory(new File(FileUtils.getCachePath()));
                FileUtils.cleanDirectory(new File(FileUtils.getFilePath() + "/csp/"));
            } catch (Exception ignored) {}
        }).start();
        App.showToastShort(mContext, "缓存已清空！");
    }

    @Override
    public void onClick(View v) {
        HawkConfig.hotVodDelete = false;
        FastClickCheckUtil.check(v);

        if (v.getId() == R.id.tvLive) jumpActivity(LivePlayActivity.class);
        else if (v.getId() == R.id.tvSearch) jumpActivity(SearchActivity.class);
        else if (v.getId() == R.id.tvSetting) jumpActivity(SettingActivity.class);
        else if (v.getId() == R.id.tvHistory) jumpActivity(HistoryActivity.class);
        else if (v.getId() == R.id.tvPush) jumpActivity(PushActivity.class);
        else if (v.getId() == R.id.tvFavorite) jumpActivity(CollectActivity.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public boolean isFolederMode() {
        return style != null && "list".equals(style.type);
    }

    private View.OnFocusChangeListener focusChangeListener =
        (v, hasFocus) -> v.animate()
            .scaleX(hasFocus ? 1.05f : 1.0f)
            .scaleY(hasFocus ? 1.05f : 1.0f)
            .setDuration(300)
            .setInterpolator(new BounceInterpolator())
            .start();
}
