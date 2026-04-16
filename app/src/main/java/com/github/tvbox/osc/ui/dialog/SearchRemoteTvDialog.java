package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xuameng
 * 远端聚汇影视全面修改
 * 直接显示上次列表，可清空列表，重新搜索等
 *
 * @version 2.0.0
 */
public class SearchRemoteTvDialog extends BaseDialog {

    private SelectDialogAdapter<DeviceInfo> mSelectAdapter;
    
    // ✅ 使用自定义对象列表代替简单的字符串列表
    private static final List<DeviceInfo> remoteTvHostList = new ArrayList<>();
    
    private boolean foundRemoteTv = false;
    private LoadService mLoadService;
    private boolean isSearching = false;
    private volatile boolean isCancelled = false;

    // ✅ 内部类：用于存储设备信息 (主机名 + 地址)
    public static class DeviceInfo {
        public String hostName;
        public String address; // IP:Port

        public DeviceInfo(String hostName, String address) {
            this.hostName = hostName;
            this.address = address;
        }

        @Override
        public String toString() {
            return hostName + " (" + address + ")";
        }
    }

    public SearchRemoteTvDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_remotetv);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTip("搜索附近聚汇影视");

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (isSearching) {
                App.showToastShort(getContext(), "搜索中，请稍候");
                return;
            }
            setTip("搜索附近聚汇影视");
            startSearch();
        });

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            isCancelled = true;
            isSearching = false;
            remoteTvHostList.clear();
            Hawk.delete(HawkConfig.REMOTE_TV_LIST);
            Hawk.delete(HawkConfig.REMOTE_TVBOX);
            showSuccess();
            foundRemoteTv = false;
            setTip("搜索附近聚汇影视");
            if (mSelectAdapter != null) {
                mSelectAdapter.setData(new ArrayList<>(), 0);
            }
            App.showToastShort(getContext(), "列表已清空");
        });
    }

    @Override
    public void show() {
        super.show();
        List<String> cache = Hawk.get(HawkConfig.REMOTE_TV_LIST, null);
        if (cache != null && !cache.isEmpty()) {
            remoteTvHostList.clear();
            // ✅ 这里假设缓存里只有地址，主机名暂时显示为 "Unknown"
            // 如果需要记住主机名，需要修改缓存结构存储 Map
            for (String addr : cache) {
                remoteTvHostList.add(new DeviceInfo("Unknown", addr));
            }
            setTip("选择附近聚汇影视");
            showRemoteTvList();
        } else {
            setTip("搜索附近聚汇影视");
        }
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
        setLoadSir(findViewById(R.id.list));
    }

    private void startSearch() {
        if (isSearching) {
            return;
        }
        isSearching = true;
        isCancelled = false;
        showLoading();
        remoteTvHostList.clear();
        foundRemoteTv = false;
        RemoteTVBox tv = new RemoteTVBox();
        new Thread(() -> {
            RemoteTVBox.searchAvalible(tv.new Callback() {
                @Override
                public void found(String viewHost, String hostName, boolean end) { // ✅ 接收 hostName
                    if (isCancelled) {
                        return;
                    }
                    // ✅ 构建 DeviceInfo 对象
                    remoteTvHostList.add(new DeviceInfo(hostName, viewHost));
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
                // ✅ 保存地址列表 (目前 Hawk 只存地址)
                List<String> addrList = new ArrayList<>();
                for (DeviceInfo device : remoteTvHostList) {
                    addrList.add(device.address);
                }
                Hawk.put(HawkConfig.REMOTE_TV_LIST, addrList);
                
                setTip("选择附近聚汇影视");
                showRemoteTvList();
                if (mSelectAdapter != null) {
                    RemoteTVBox.setAvalible(remoteTvHostList.get(0).address);
                }
            } else {
                setTip("搜索附近聚汇影视");
                showEmpty();
                App.showToastShort(getContext(), "未找到附近聚汇影视！");
            }
        });
    }

    private void showRemoteTvList() {
        showSuccess();
        RecyclerView list = findViewById(R.id.list);
        if (mSelectAdapter == null) {
            mSelectAdapter = new SelectDialogAdapter<>(new SelectDialogAdapter.SelectDialogInterface<DeviceInfo>() {
                @Override
                public void click(DeviceInfo value, int pos) {
                    RemoteTVBox.setAvalible(value.address);
                    App.showToastShort(getContext(), "已选择：" + value.hostName);
                }

                @Override
                public String getDisplay(DeviceInfo val) {
                    // ✅ 核心修改：显示 "主机名 (IP:端口)"
                    return val.hostName + " (" + val.address + ")";
                }
            }, new DiffUtil.ItemCallback<DeviceInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull DeviceInfo oldItem, @NonNull DeviceInfo newItem) {
                    return oldItem.address.equals(newItem.address);
                }

                @Override
                public boolean areContentsTheSame(@NonNull DeviceInfo oldItem, @NonNull DeviceInfo newItem) {
                    return oldItem.address.equals(newItem.address);
                }
            });
            list.setAdapter(mSelectAdapter);
        }
        mSelectAdapter.setData(remoteTvHostList, getLastSelectedIndex());
    }

    private int getLastSelectedIndex() {
        String last = Hawk.get(HawkConfig.REMOTE_TVBOX, null);
        if (last == null) {
            return 0;
        }
        for (int i = 0; i < remoteTvHostList.size(); i++) {
            if (remoteTvHostList.get(i).address.equals(last)) {
                return i;
            }
        }
        return 0;
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
