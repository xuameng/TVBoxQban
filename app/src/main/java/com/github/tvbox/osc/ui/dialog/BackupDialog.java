package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.ui.adapter.BackupAdapter;
import com.github.tvbox.osc.util.FileUtils;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import com.github.tvbox.osc.util.HawkConfig;     //xuameng恢复判断

public class BackupDialog extends BaseDialog {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BackupDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_backup);
        TvRecyclerView tvRecyclerView = ((TvRecyclerView) findViewById(R.id.list));
        BackupAdapter adapter = new BackupAdapter();
        tvRecyclerView.setAdapter(adapter);
        adapter.setNewData(allBackup());
        adapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (view.getId() == R.id.tvName) {
                    restore((String) adapter.getItem(position));
                } else if (view.getId() == R.id.tvDel) {
                    delete((String) adapter.getItem(position));
                    adapter.setNewData(allBackup());
                }
            }
        });
        findViewById(R.id.backupNow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backup();
                adapter.setNewData(allBackup());
            }
        });
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                    App.showToastShort(getContext(), "已获得存储权限！");
                } else {
                    XXPermissions.with(getContext())
                            .permission(Permission.Group.STORAGE)
                            .request(new OnPermissionCallback() {
                                @Override
                                public void onGranted(List<String> permissions, boolean all) {
                                    if (all) {
                                        adapter.setNewData(allBackup());
                                        App.showToastShort(getContext(), "已获得存储权限！");
                                    }
                                }

                                @Override
                                public void onDenied(List<String> permissions, boolean never) {
                                    if (never) {
                                        App.showToastShort(getContext(), "获取存储权限失败,请在系统设置中开启！");
                                        startPermissionActivitySafely(permissions);
                                    } else {
                                        App.showToastShort(getContext(), "获取存储权限失败！");
                                    }
                                }
                            });
                }
            }
        });
    }

    // 安全地启动权限设置页面
    private void startPermissionActivitySafely(List<String> permissions) {
        Context context = getContext();
        if (context instanceof Activity) {
            // 如果context是Activity，安全调用
            XXPermissions.startPermissionActivity((Activity) context, permissions);
        } else if (context != null) {
            // 如果不是Activity，尝试从Dialog的window获取Activity
            Activity activity = getActivityFromContext(context);
            if (activity != null) {
                XXPermissions.startPermissionActivity(activity, permissions);
            } else {
                // 如果无法获取Activity，显示提示
                App.showToastShort(context, "无法启动权限设置页面，请在系统设置中手动开启权限");
            }
        }
    }
    
    // 尝试从Context获取Activity
    private Activity getActivityFromContext(Context context) {
        // 方法1：如果是Activity，直接返回
        if (context instanceof Activity) {
            return (Activity) context;
        }
        
        // 方法2：尝试从ContextThemeWrapper获取底层Activity
        if (context instanceof android.view.ContextThemeWrapper) {
            Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
            if (baseContext instanceof Activity) {
                return (Activity) baseContext;
            }
        }
        
        // 方法3：如果是Wrapper，尝试获取原始Context
        if (context instanceof android.content.ContextWrapper) {
            Context baseContext = ((android.content.ContextWrapper) context).getBaseContext();
            if (baseContext instanceof Activity) {
                return (Activity) baseContext;
            }
        }
        
        return null;
    }

    List<String> allBackup() {
        ArrayList<String> result = new ArrayList<>();
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File file = new File(root + "/聚汇影视备份黑标/");
            File[] list = file.listFiles();
            if (list != null) {
                Arrays.sort(list, new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        if (o1.isDirectory() && o2.isFile()) return -1;
                        return o1.isFile() && o2.isDirectory() ? 1 : o2.getName().compareTo(o1.getName());
                    }
                });
                if (file.exists()) {
                    for (File f : list) {
                        if (result.size() > 10) {
                            FileUtils.recursiveDelete(f);
                            continue;
                        }
                        if (f.isDirectory()) {
                            result.add(f.getName());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    void restore(String dir) {
        new Thread(() -> {
            boolean success = false;
            try {
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                File backup = new File(root + "/聚汇影视备份黑标/" + dir);
                if (backup.exists()) {
                    File db = new File(backup, "sqlite");
                    if (AppDataManager.restore(db)) {
                        byte[] data = FileUtils.readSimple(new File(backup, "hawk"));
                        if (data != null) {
                            String hawkJson = new String(data, "UTF-8");
                            JSONObject jsonObject = new JSONObject(hawkJson);
                            Iterator<String> it = jsonObject.keys();
                            SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
                            while (it.hasNext()) {
                                String key = it.next();
                                String value = jsonObject.getString(key);
                                if (key.equals("cipher_key")) {
                                    App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE).edit().putString(key, value).commit();
                                } else {
                                    sharedPreferences.edit().putString(key, value).commit();
                                }
                            }
                            success = true;
                        } else {
                            showToastOnMainThread("Hawk恢复失败!");
                        }
                    } else {
                        showToastOnMainThread("DB文件恢复失败!");
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            
            final boolean finalSuccess = success;
            runOnMainThread(() -> {
                if (finalSuccess) {
                    App.showToastShort(getContext(), "数据恢复成功！请重启应用！");
                    HawkConfig.ISrestore = true;  //xuameng恢复成功,请重启应用
                }
            });
        }).start();
    }

    void backup() {
        new Thread(() -> {
            boolean success = false;
            try {
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                File file = new File(root + "/聚汇影视备份黑标/");
                if (!file.exists())
                    file.mkdirs();
                Date now = new Date();
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
                File backup = new File(file, f.format(now));
                backup.mkdirs();
                File db = new File(backup, "sqlite");
                if (AppDataManager.backup(db)) {
                    SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
                    JSONObject jsonObject = new JSONObject();
                    for (String key : sharedPreferences.getAll().keySet()) {
                        jsonObject.put(key, sharedPreferences.getString(key, ""));
                    }
                    sharedPreferences = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
                    for (String key : sharedPreferences.getAll().keySet()) {
                        jsonObject.put(key, sharedPreferences.getString(key, ""));
                    }
                    if (!FileUtils.writeSimple(jsonObject.toString().getBytes("UTF-8"), new File(backup, "hawk"))) {
                        backup.delete();
                        showToastOnMainThread("备份Hawk失败!");
                    } else {
                        success = true;
                        showToastOnMainThread("备份成功!");
                    }
                } else {
                    backup.delete();
                    showToastOnMainThread("DB文件不存在!");
                }
            } catch (Throwable e) {
                e.printStackTrace();
                showToastOnMainThread("备份失败!");
            }
        }).start();
    }

    void delete(String dir) {
        new Thread(() -> {
            try {
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                File backup = new File(root + "/聚汇影视备份黑标/" + dir);
                FileUtils.recursiveDelete(backup);
                showToastOnMainThread("此备份已删除！");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    // 在主线中运行代码
    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
    
    // 在主线中显示Toast
    private void showToastOnMainThread(String message) {
        runOnMainThread(() -> {
            App.showToastShort(getContext(), message);
        });
    }
}
