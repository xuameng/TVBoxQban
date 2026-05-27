package com.github.tvbox.osc.util;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.github.tvbox.osc.base.App; //xuameng toast

public class DownloadHelper {

    private static long downloadId = -1;

    /**
     * 开始下载（支持 APK / ZIP / RAR / 7Z / TAR 等）
     */
    public static void start(Context context, String url, String name) {
        if (context == null || TextUtils.isEmpty(url)) return;

        DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request req =
                new DownloadManager.Request(Uri.parse(url));

        // 下载通知
        req.setTitle(name);
        req.setDescription("正在下载");
        req.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, name);
        req.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // 网络策略
        req.setAllowedOverMetered(true);
        req.setAllowedOverRoaming(true);

        // MIME 类型（防止系统不知道怎么打开）
        String mime = getMimeType(name);
        if (!TextUtils.isEmpty(mime)) {
            req.setMimeType(mime);
        }

        downloadId = dm.enqueue(req);
        App.showToastShort(context, "开始下载：" + name);
    }

    /**
     * 根据文件名获取 MIME 类型
     */
    private static String getMimeType(String name) {
        int dot = name.lastIndexOf(".");
        if (dot < 0) return null;

        String ext = name.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "apk":
                return "application/vnd.android.package-archive";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/vnd.rar";
            case "7z":
                return "application/x-7z-compressed";
            case "tar":
                return "application/x-tar";
            default:
                return MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext);
        }
    }
}