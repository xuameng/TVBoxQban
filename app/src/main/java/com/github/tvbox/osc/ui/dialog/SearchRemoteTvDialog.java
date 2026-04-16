package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.base.App;

import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * xuameng
 * 远端聚汇影视全面修改
 * 直接显示上次列表，可清空列表，重新搜索等
 * 最终稳定版
 * @version 2.0.1
 */
public class SearchRemoteTvDialog extends BaseDialog {

    // ✅ 关键点：List 类型必须与 RemoteTVBox 的 Callback 返回类型一致
    private static final List<RemoteTVBox.RemoteDevice> remoteTvHostList = new ArrayList<>();
    private SelectDialogAdapter<RemoteTVBox.RemoteDevice> mSelectAdapter;
    
    private LoadService mLoadService;
    private boolean isSearching = false;
    private volatile boolean isCancelled = false;

    public SearchRemoteTvDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_remotetv);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTip("搜索附近聚汇影视");

        // 重新搜索
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (isSearching) {
                App.showToastShort(getContext(), "搜索中，请稍候");
                return;
            }
            isCancelled = false; // ✅ 重置取消状态
            showLoading();
            startSearch();
        });

        // 清空列表
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            isCancelled = true;
            isSearching = false;
            remoteTvHostList.clear();
            Hawk.delete(HawkConfig.REMOTE_TV_LIST);
            Hawk.delete(HawkConfig.REMOTE_TVBOX);
            showEmpty();
            App.showToastShort(getContext(), "列表已清空");
        });
    }

    @Override
    public void show() {
        super.show();
        List<RemoteTVBox.RemoteDevice> cache =
                Hawk.get(HawkConfig.REMOTE_TV_LIST, null);
        if (cache != null && !cache.isEmpty()) {
            remoteTvHostList.clear();
            remoteTvHostList.addAll(cache);
            showRemoteTvList();
        } else {
            showEmpty();
        }
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
        setLoadSir(findViewById(R.id.list));
        showLoading();
    }

    private void startSearch() {
        if (isSearching) return;
        isSearching = true;
        remoteTvHostList.clear();

        RemoteTVBox tv = new RemoteTVBox();

        new Thread(() -> {
            tv.searchAvalible(new RemoteTVBox.Callback() {

                @Override
                public void found(RemoteTVBox.RemoteDevice device, boolean end) {
                    if (isCancelled) return;
                    remoteTvHostList.add(device);
                    if (end) {
                    finishSearch(true);
                    }
                }

                @Override
                public void fail(boolean all, boolean end) {
                    if (end) {
                        finishSearch(!all);
                    }
                }
            });
        }).start();
    }

    private void finishSearch(boolean found) {
        isSearching = false;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (found && !remoteTvHostList.isEmpty()) {
                Hawk.put(HawkConfig.REMOTE_TV_LIST, new ArrayList<>(remoteTvHostList));
                showRemoteTvList();
                // ✅ 关键：默认选中第一个
                if (mSelectAdapter != null) {
                    RemoteTVBox.setAvalible(remoteTvHostList.get(0).getHost());
                }
            } else {
                showEmpty();
                App.showToastShort(getContext(), "未找到附近聚汇影视！");
            }
        });
    }

    private void showRemoteTvList() {
        showSuccess();
        RecyclerView list = findViewById(R.id.list);

        if (mSelectAdapter == null) {
            mSelectAdapter = new SelectDialogAdapter<>(
                    new SelectDialogAdapter.SelectDialogInterface<RemoteTVBox.RemoteDevice>() {
                        @Override
                        public void click(RemoteTVBox.RemoteDevice value, int pos) {
                            RemoteTVBox.setAvalible(value.getHost());
                            App.showToastShort(getContext(), "已选择：" + value.hostName);
                        }

                        @Override
                        public String getDisplay(RemoteTVBox.RemoteDevice val) {
                            return val.getDisplay(); // ✅ 显示 IP + 主机名
                        }
                    },
                    new DiffUtil.ItemCallback<RemoteTVBox.RemoteDevice>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull RemoteTVBox.RemoteDevice oldItem, @NonNull RemoteTVBox.RemoteDevice newItem) {
                            return oldItem.getHost().equals(newItem.getHost());
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull RemoteTVBox.RemoteDevice oldItem, @NonNull RemoteTVBox.RemoteDevice newItem) {
                            return oldItem.hostName.equals(newItem.hostName);
                        }
                    }
            );
            list.setAdapter(mSelectAdapter);
        }
        // ✅ 修复了语法错误的位置，并正确获取索引
        mSelectAdapter.setData(remoteTvHostList, getLastSelectedIndex());
    }

    private int getLastSelectedIndex() {
        String lastHost = Hawk.get(HawkConfig.REMOTE_TVBOX, null);
        if (lastHost == null) return 0;
        for (int i = 0; i < remoteTvHostList.size(); i++) {
            if (lastHost.equals(remoteTvHostList.get(i).getHost())) {
                return i;
            }
        }
        return 0;
    }

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, v -> {});
        }
    }

    public void showLoading() {
        if (mLoadService != null) mLoadService.showCallback(LoadingCallback.class);
    }

    public void showEmpty() {
        if (mLoadService != null) mLoadService.showCallback(EmptyCallback.class);
    }

    public void showSuccess() {
        if (mLoadService != null) mLoadService.showSuccess();
    }
}
