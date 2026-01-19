package com.github.tvbox.osc.subtitle;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.subtitle.exception.FatalParsingException;
import com.github.tvbox.osc.subtitle.format.FormatASS;
import com.github.tvbox.osc.subtitle.format.FormatSRT;
import com.github.tvbox.osc.subtitle.format.FormatSTL;
import com.github.tvbox.osc.subtitle.format.TimedTextFileFormat;
import com.github.tvbox.osc.subtitle.model.TimedTextObject;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.UnicodeReader;
import com.lzy.okgo.OkGo;

import org.apache.commons.io.input.ReaderInputStream;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import okhttp3.Response;

/**
 * @author AveryZhong.
 */

public class SubtitleLoader {
    private static final String TAG = SubtitleLoader.class.getSimpleName();

    private SubtitleLoader() {
        throw new AssertionError("No instance for you.");
    }

    public static void loadSubtitle(final String path, final Callback callback) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        if (path.startsWith("http://")
                || path.startsWith("https://")) {
            loadFromRemoteAsync(path, callback);
        } else {
            loadFromLocalAsync(path, callback);
        }
    }

    private static void loadFromRemoteAsync(final String remoteSubtitlePath,
                                            final Callback callback) {
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final SubtitleLoadSuccessResult subtitleLoadSuccessResult = loadFromRemote(remoteSubtitlePath);
                    if (callback != null) {
                        AppTaskExecutor.mainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(subtitleLoadSuccessResult);
                            }
                        });
                    }

                } catch (final Exception e) {
                    e.printStackTrace();
                    if (callback != null) {
                        AppTaskExecutor.mainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                    }

                }
            }
        });
    }

    private static void loadFromLocalAsync(final String localSubtitlePath,
                                           final Callback callback) {
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final SubtitleLoadSuccessResult subtitleLoadSuccessResult = loadFromLocal(localSubtitlePath);
                    if (callback != null) {
                        AppTaskExecutor.mainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(subtitleLoadSuccessResult);
                            }
                        });
                    }

                } catch (final Exception e) {
                    e.printStackTrace();
                    if (callback != null) {
                        AppTaskExecutor.mainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                    }

                }
            }
        });
    }

    public SubtitleLoadSuccessResult loadSubtitle(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        try {
            if (path.startsWith("http://")
                    || path.startsWith("https://")) {
                return loadFromRemote(path);
            } else {
                return loadFromLocal(path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

private static SubtitleLoadSuccessResult loadFromRemote(final String remoteSubtitlePath)
        throws IOException, FatalParsingException, Exception {
    Log.d(TAG, "parseRemote: remoteSubtitlePath = " + remoteSubtitlePath);
    
    String referer = "";
    if (remoteSubtitlePath.contains("alicloud") || remoteSubtitlePath.contains("aliyundrive")) {
        referer = "https://www.aliyundrive.com/";
    } else if (remoteSubtitlePath.contains("assrt.net")) {
        referer = "https://secure.assrt.net/";
    }
    
    String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    
    // Android 4.x兼容性处理
    com.lzy.okgo.request.GetRequest<String> request = OkGo.<String>get(remoteSubtitlePath.split("#")[0])
            .headers("Referer", referer)
            .headers("User-Agent", ua);
    
    // 为Android 4.x增加超时设置
    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.KITKAT) {
        request.readTimeOut(45000)           // 读取超时45秒
                .writeTimeOut(45000)         // 写入超时45秒
                .connectTimeout(45000);       // 连接超时45秒
    }
    
    Response response = request.execute();
    
    // 处理可能的网络错误
    if (response == null || response.body() == null) {
        throw new IOException("Network response is null");
    }
    
    byte[] bytes = response.body().bytes();
    if (bytes == null || bytes.length == 0) {
        throw new IOException("Response body is empty");
    }
    
    // Android 4.x编码检测备选方案
    String encoding = "UTF-8";
    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.KITKAT) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        encoding = detector.getDetectedCharset();
        if (TextUtils.isEmpty(encoding)) encoding = "UTF-8";
    } else {
        // Android 4.x备用编码检测
        encoding = detectEncodingForAndroid4(bytes);
    }
    
    String content;
    try {
        content = new String(bytes, encoding);
    } catch (UnsupportedEncodingException e) {
        // 如果编码失败，尝试UTF-8
        content = new String(bytes, "UTF-8");
    }
    
InputStream is = new ByteArrayInputStream(content.getBytes());
String filename = "";
String contentDispostion = response.header("content-disposition", "");
String[] cd = contentDispostion.split(";");
if (cd.length > 1) {
    String filenameInfo = cd[1];
    filenameInfo = filenameInfo.trim();
    if (filenameInfo.startsWith("filename=")) {
        filename = filenameInfo.replace("filename=", "");
        filename = filename.replace("\"", "");
    } else if (filenameInfo.startsWith("filename*=")) {
        filename = filenameInfo.substring(filenameInfo.lastIndexOf("''")+2);
    }
    filename = filename.trim();
    filename = URLDecoder.decode(filename);
}
String filePath = filename;
if (filename == null || filename.length() < 1) {
    Uri uri = Uri.parse(remoteSubtitlePath);
    filePath = uri.getPath();
}
if (!filePath.contains(".") && remoteSubtitlePath.contains("#")) {
    filePath = remoteSubtitlePath.split("#")[1];
    filePath = URLDecoder.decode(filePath);
}
SubtitleLoadSuccessResult subtitleLoadSuccessResult = new SubtitleLoadSuccessResult();
subtitleLoadSuccessResult.timedTextObject = loadAndParse(is, filePath);
subtitleLoadSuccessResult.fileName = filePath;
subtitleLoadSuccessResult.content = content;
subtitleLoadSuccessResult.subtitlePath = remoteSubtitlePath;
return subtitleLoadSuccessResult;
}

// Android 4.x专用编码检测方法
private static String detectEncodingForAndroid4(byte[] bytes) {
    // 简单的编码检测逻辑，适用于Android 4.x
    try {
        // 尝试UTF-8
        String test = new String(bytes, "UTF-8");
        if (isValidText(test)) {
            return "UTF-8";
        }
        
        // 尝试GBK/GB2312（中文网站常用）
        try {
            test = new String(bytes, "GBK");
            if (isValidText(test)) {
                return "GBK";
            }
        } catch (Exception e) {
            // ignore
        }
        
        // 尝试ISO-8859-1
        try {
            test = new String(bytes, "ISO-8859-1");
            if (isValidText(test)) {
                return "ISO-8859-1";
            }
        } catch (Exception e) {
            // ignore
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return "UTF-8";
}

private static boolean isValidText(String text) {
    if (TextUtils.isEmpty(text)) return false;
    // 检查是否包含有效字符
    return text.length() > 10 && (text.contains(" ") || text.contains("\n") || text.contains("\t"));
}


    private static TimedTextObject loadAndParse(final InputStream is, final String filePath)
            throws IOException, FatalParsingException {
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
        String ext = "";
        if (fileName.lastIndexOf(".") > 0) {
            ext = fileName.substring(fileName.lastIndexOf("."));
        }
        Log.d(TAG, "parse: name = " + fileName + ", ext = " + ext);
        Reader reader = new UnicodeReader(is); //处理有BOM头的utf8
        InputStream newInputStream = new ReaderInputStream(reader, Charset.defaultCharset());
        if (".srt".equalsIgnoreCase(ext)) {
            return new FormatSRT().parseFile(fileName, newInputStream);
        } else if (".ass".equalsIgnoreCase(ext)) {
            return new FormatASS().parseFile(fileName, newInputStream);
        } else if (".stl".equalsIgnoreCase(ext)) {
            return new FormatSTL().parseFile(fileName, newInputStream);
        } else if (".ttml".equalsIgnoreCase(ext)) {
            return new FormatSTL().parseFile(fileName, newInputStream);
        }
        TimedTextFileFormat[] arr = {new FormatSRT(), new FormatASS(), new FormatSTL(), new FormatSTL()};
        for(TimedTextFileFormat oneFormat : arr) {
            try {
                TimedTextObject obj = oneFormat.parseFile(fileName, newInputStream);
                return obj;
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

    public interface Callback {
        void onSuccess(SubtitleLoadSuccessResult SubtitleLoadSuccessResult);

        void onError(Exception exception);
    }
}
