package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

import okhttp3.OkHttpClient;

public final class ExoMediaSourceHelper {

private static ExoMediaSourceHelper sInstance;

private final String mUserAgent;
private final Context mAppContext;
private OkHttpDataSource.Factory mHttpDataSourceFactory;
private OkHttpClient mOkClient = null;
private Cache mCache;

private ExoMediaSourceHelper(Context context) {
    mAppContext = context.getApplicationContext();
    mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
}

public static ExoMediaSourceHelper getInstance(Context context) {
    if (sInstance == null) {
        synchronized (ExoMediaSourceHelper.class) {
            if (sInstance == null) {
                sInstance = new ExoMediaSourceHelper(context);
            }
        }
    }
    return sInstance;
}

public void setOkClient(OkHttpClient client) {
    mOkClient = client;
}

public MediaSource getMediaSource(String uri) {
    return getMediaSource(uri, null, false, null);
}

public MediaSource getMediaSource(String uri, Map<String, String> headers) {
    return getMediaSource(uri, headers, false, null);
}

public MediaSource getMediaSource(String uri, boolean isCache) {
    return getMediaSource(uri, null, isCache, null);
}

public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache) {
    return getMediaSource(uri, headers, isCache, null);
}

public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache, String mimeType) {
    Uri contentUri = Uri.parse(uri);
    
    // RTMP和RTSP协议特殊处理
    if ("rtmp".equals(contentUri.getScheme())) {
        return new ProgressiveMediaSource.Factory(new RtmpDataSourceFactory(null))
                .createMediaSource(MediaItem.fromUri(contentUri));
    } else if ("rtsp".equals(contentUri.getScheme())) {
        return new RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri));
    }
    
    // 获取数据源工厂
    DataSource.Factory factory;
    if (isCache) {
        factory = getCacheDataSourceFactory();
    } else {
        factory = getDataSourceFactory();
    }
    
    // 设置HTTP头部
    if (mHttpDataSourceFactory != null) {
        setHeaders(headers);
    }
    
    // 构建MediaItem
    MediaItem.Builder builder = new MediaItem.Builder()
            .setUri(contentUri);
    
    // 优先使用传入的MIME类型，否则根据URL推断
    String actualMimeType = mimeType;
    if (actualMimeType == null || actualMimeType.isEmpty()) {
        actualMimeType = inferMimeType(uri);
    }
    
    if (actualMimeType != null && !actualMimeType.isEmpty()) {
        builder.setMimeType(actualMimeType);
    }
    
    MediaItem mediaItem = builder.build();
    
    // 关键修复：根据MIME类型而不是URL推断来选择MediaSource工厂
    if (actualMimeType != null) {
        if (actualMimeType.equals(MimeTypes.APPLICATION_M3U8)) {
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
        } else if (actualMimeType.equals(MimeTypes.APPLICATION_MPD)) {
            return new DashMediaSource.Factory(factory).createMediaSource(mediaItem);
        }
    }
    
    // 如果MIME类型为空，则根据URL推断
    int contentType = inferContentType(uri);
    switch (contentType) {
        case C.TYPE_DASH:
            return new DashMediaSource.Factory(factory).createMediaSource(mediaItem);
        case C.TYPE_HLS:
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
        default:
        case C.TYPE_OTHER:
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
    }
}

/**
 * 修复后的 inferContentType 方法
 * 正确处理带查询参数的URL
 */
private int inferContentType(String uri) {
    // 先去除查询参数，只检查路径部分
    String path = uri.toLowerCase();
    int questionMarkIndex = path.indexOf('?');
    if (questionMarkIndex != -1) {
        path = path.substring(0, questionMarkIndex);
    }
    
    // 检查路径中是否包含格式标识
    if (path.contains(".mpd") || path.contains("type=mpd")) {
        return C.TYPE_DASH;
    } else if (path.contains(".m3u8")) {
        return C.TYPE_HLS;
    } else {
        return C.TYPE_OTHER;
    }
}

/**
 * 根据内容类型推断MIME类型
 */
private String inferMimeType(String uri) {
    int contentType = inferContentType(uri);
    switch (contentType) {
        case C.TYPE_DASH:
            return MimeTypes.APPLICATION_MPD;
        case C.TYPE_HLS:
            return MimeTypes.APPLICATION_M3U8;
        default:
            return null;
    }
}

private DataSource.Factory getCacheDataSourceFactory() {
    if (mCache == null) {
        mCache = newCache();
    }
    return new CacheDataSource.Factory()
            .setCache(mCache)
            .setUpstreamDataSourceFactory(getDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
}

private Cache newCache() {
    return new SimpleCache(
            new File(FileUtils.getCachePath() + "exo-video-cache"),
            new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
            new ExoDatabaseProvider(mAppContext));
}

/**
 * Returns a new DataSource factory.
 *
 * @return A new DataSource factory.
 */
private DataSource.Factory getDataSourceFactory() {
    return new DefaultDataSourceFactory(mAppContext, getHttpDataSourceFactory());
}

/**
 * Returns a new HttpDataSource factory.
 *
 * @return A new HttpDataSource factory.
 */
private DataSource.Factory getHttpDataSourceFactory() {
    if (mHttpDataSourceFactory == null) {
        mHttpDataSourceFactory = new OkHttpDataSource.Factory(mOkClient)
                .setUserAgent(mUserAgent);
    }
    return mHttpDataSourceFactory;
}

private void setHeaders(Map<String, String> headers) {
    if (headers != null && headers.size() > 0) {
        // 如果发现用户通过header传递了UA，则强行将HttpDataSourceFactory里面的userAgent字段替换成用户的
        if (headers.containsKey("User-Agent")) {
            String value = headers.remove("User-Agent");
            if (!TextUtils.isEmpty(value)) {
                try {
                    Field userAgentField = mHttpDataSourceFactory.getClass().getDeclaredField("userAgent");
                    userAgentField.setAccessible(true);
                    userAgentField.set(mHttpDataSourceFactory, value.trim());
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        Iterator<String> iter = headers.keySet().iterator();
        while (iter.hasNext()) {
            String k = iter.next();
            String v = headers.get(k);
            if (v != null)
                headers.put(k, v.trim());
        }
        mHttpDataSourceFactory.setDefaultRequestProperties(headers);
    }
}

public void setCache(Cache cache) {
    this.mCache = cache;
}

}
