package com.github.tvbox.osc.util.thunder;

import android.net.Uri;
import android.text.TextUtils;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.LocalIPAddress;
import com.p2p.P2PClass;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;


public class Jianpian {

public static String JPUrlDec(String url) {
    if (App.getp2p() != null) {
        try {
            // 1. 先对输入 URL 做 UTF-8 解码
            String decode = URLDecoder.decode(url, "UTF-8");
            String[] split = decode.split("\\|");
            String replace = split[0]; // 拿到核心部分，如 xg://xxx|...

            // 2. 替换伪协议为 ftp://
            replace = replace.replace("xg://", "ftp://")
                            .replace("xgplay://", "ftp://")
                            .replace("tvbox-xg://", "")
                            .replace("tvbox-xg:", "");

            // 3. P2P 控制逻辑（和原来一样）
            if (TextUtils.isEmpty(App.burl)) {
                App.getp2p().P2Pdoxstart(replace.getBytes("GBK"));
                App.getp2p().P2Pdoxadd(replace.getBytes("GBK"));
            } else if (replace.equals(App.burl)) {
                App.getp2p().P2Pdoxstart(replace.getBytes("GBK"));
            } else {
                App.getp2p().P2Pdoxpause(App.burl.getBytes("GBK"));
                App.getp2p().P2Pdoxdel(App.burl.getBytes("GBK"));
                App.getp2p().P2Pdoxstart(replace.getBytes("GBK"));
                App.getp2p().P2Pdoxadd(replace.getBytes("GBK"));
            }
            App.burl = replace;

            // 4. ✅ 重点修复：手动从 replace（ftp://xxx/文件名.mp4）中提取文件名
            // 假设 replace = "ftp://127.0.0.1/some/path/某某生活02.mp4"
            int ftpIndex = replace.indexOf("ftp://");
            if (ftpIndex == -1) {
                return ""; // 不是有效的 ftp 地址，返回空
            }

            // 取 ftp:// 之后的部分
            String pathPart = replace.substring(ftpIndex + 6); // 去掉 "ftp://"

            // 找最后一个 '/'，取文件名
            int lastSlash = pathPart.lastIndexOf('/');
            String fileName = (lastSlash >= 0) ? pathPart.substring(lastSlash + 1) : pathPart;

            // 5. 对文件名进行 GBK 编码，生成可被 P2P 服务正确寻址的路径
            String encodedFileName = URLEncoder.encode(fileName, "GBK");

            // 6. 拼接最终的 HTTP 代理地址，例如：http://192.168.1.100:8888/编码后的文件名.mp4
            String localIp = LocalIPAddress.getIP(App.getInstance());
            int port = P2PClass.port;

            return "http://" + localIp + ":" + port + "/" + encodedFileName;

        } catch (Exception e) {
            e.printStackTrace();
            return ""; // 出错时返回空字符串，避免播放器拿到错误地址
        }
    }
    return "";
}
    
    public static void finish() {
        if (TextUtils.isEmpty(App.burl) || App.getp2p() == null) {
            return;
        }
        try {
            App.getp2p()
                .P2Pdoxpause(App.burl.getBytes("GBK"));
            App.getp2p()
                .P2Pdoxdel(App.burl.getBytes("GBK"));
            App.burl = "";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static Boolean isJpUrl(String url) {
        return url.startsWith("tvbox-xg:") || (Thunder.isFtp(url) && url.contains("gbl.114s"));
    }
}
