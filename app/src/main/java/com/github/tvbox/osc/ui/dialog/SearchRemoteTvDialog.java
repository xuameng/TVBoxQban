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
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment;
import com.github.tvbox.osc.base.App;  //xuameng toast

import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * xuameng
 * 远端聚汇影视全面修改
 * 直接显示上次列表，可清空列表，重新搜索等
 * @version 2.0.0
 */

public class SearchRemoteTvDialog extends BaseDialog {

    private SelectDialogAdapter<String> mSelectAdapter;
    private static final List<String> remoteTvHostList = new ArrayList<>();
    private boolean foundRemoteTv = false;
    private LoadService mLoadService;
    private boolean isSearching = false;

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
            showLoading();
            startSearch();
        });

        // 清空列表
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            remoteTvHostList.clear();
            foundRemoteTv = false;
            showEmpty();
            App.showToastShort(getContext(), "列表已清空");
        });
    }

@Override
public void show() {
    super.show();
    if (remoteTvHostList != null && !remoteTvHostList.isEmpty()) {
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

        showLoading();

        remoteTvHostList.clear();
        foundRemoteTv = false;

        RemoteTVBox tv = new RemoteTVBox();

        new Thread(() -> {
            RemoteTVBox.searchAvalible(tv.new Callback() {
                @Override
                public void found(String viewHost, boolean end) {
                    remoteTvHostList.add(viewHost);
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
        foundRemoteTv = found;

        new Handler(Looper.getMainLooper()).post(() -> {
            if (found && !remoteTvHostList.isEmpty()) {
                showRemoteTvList();
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
                new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        RemoteTVBox.setAvalible(value);
                        App.showToastShort(getContext(), "已选择：" + value);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                },
                new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }
        );

        list.setAdapter(mSelectAdapter);
    }

    // ✅ 正确：两个参数
    mSelectAdapter.setData(remoteTvHostList, 0);
}

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                }
            });
        }
    }

    public void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    public void showEmpty() {
        if (mLoadService != null) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    public void showSuccess() {
        if (mLoadService != null) {
            mLoadService.showSuccess();
        }
    }
}
