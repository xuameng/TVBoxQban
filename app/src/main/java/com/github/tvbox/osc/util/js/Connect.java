package com.github.tvbox.osc.util.js;

import android.util.Base64;

import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.common.net.HttpHeaders;
import com.lzy.okgo.OkGo;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSUtils;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.List;
import java.util.Map;
import java.util.Random;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class Connect {
    static OkHttpClient client;
    
    public static Call to(String url, Req req) {
        client = OkGoHelper.getDefaultClient();
        return client.newCall(getRequest(url, req, Headers.of(req.getHeader())));
    }    

public static JSObject success(QuickJSContext ctx, Req req, Response res) {
    try {
        JSObject jsObject = ctx.createNewJSObject();
        JSObject jsHeader = ctx.createNewJSObject();
        setHeader(ctx, res, jsHeader);
        ctx.setProperty(jsObject, "headers", jsHeader);

        // ✅ 只读一次 bytes
        byte[] bytes = res.body().bytes();

        // ✅ 不信任头，只看 gzip 魔数
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0x1F
                && (bytes[1] & 0xFF) == 0x8B
                && (bytes[2] & 0xFF) == 0x08) {
            bytes = gunzip(bytes);
        }

        String ct = res.header("Content-Type", "");
        String charset = parseCharset(ct);
        boolean isText = ct.contains("text") || ct.contains("html")
                || ct.contains("json") || ct.contains("javascript");
        if (charset == null && isText) charset = "UTF-8";

        if (req.getBuffer() == 0) {
            ctx.setProperty(jsObject, "content",
                    isText ? new String(bytes, charset) : new String(bytes));
        } else if (req.getBuffer() == 1) {
            byte[] out = isText ? new String(bytes, charset).getBytes(charset) : bytes;
            JSArray array = ctx.createNewJSArray();
            for (int i = 0; i < out.length; i++) array.set(out[i], i);
            ctx.setProperty(jsObject, "content", array);
        } else if (req.getBuffer() == 2) {
            byte[] out = isText ? new String(bytes, charset).getBytes(charset) : bytes;
            ctx.setProperty(jsObject, "content",
                    Base64.encodeToString(out, Base64.DEFAULT | Base64.NO_WRAP));
        }

String preview;
try {
    preview = new String(bytes, charset);
} catch (Exception e) {
    preview = "DECODE_FAIL";
}

LOG.i("xuameng_pomo-fix buffer=" + req.getBuffer()
        + " gzipMagic=" + (bytes.length >= 3
            && (bytes[0] & 0xFF) == 0x1F
            && (bytes[1] & 0xFF) == 0x8B)
        + " preview=" + preview.substring(0, Math.min(120, preview.length())));

        return jsObject;
    } catch (Exception e) {
        LOG.i("xuameng_Connect.success error: " + e.getMessage());
        return error(ctx);
    }
}

    public static JSObject error(QuickJSContext ctx) {
        JSObject jsObject = ctx.createNewJSObject();
        JSObject jsHeader = ctx.createNewJSObject();
        ctx.setProperty(jsObject, "headers", jsHeader);
        ctx.setProperty(jsObject, "content", "");
        return jsObject;
    }

    private static Request getRequest(String url, Req req, Headers headers) {
        if (req.getMethod().equalsIgnoreCase("post")) {
            return new Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).post(getPostBody(req, headers.get(HttpHeaders.CONTENT_TYPE))).build();
        } else if (req.getMethod().equalsIgnoreCase("header")) {
            return new Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).head().build();
        } else {
            return new Request.Builder().url(url).tag("js_okhttp_tag").headers(headers).get().build();
        }
    }

    private static RequestBody getPostBody(Req req, String contentType) {
        if (req.getData() != null && req.getPostType().equals("json")) return getJsonBody(req);
        if (req.getData() != null && req.getPostType().equals("form")) return getFormBody(req);
        if (req.getData() != null && req.getPostType().equals("form-data")) return getFormDataBody(req);
        if (req.getBody() != null && contentType != null) return RequestBody.create(MediaType.get(contentType), req.getBody());
        return RequestBody.create(null, "");
    }

    private static RequestBody getJsonBody(Req req) {
        return RequestBody.create(MediaType.get("application/json"), req.getData().toString());
    }

    private static RequestBody getFormBody(Req req) {
        FormBody.Builder formBody = new FormBody.Builder();
        Map<String, String> params = Json.toMap(req.getData());
        for (String key : params.keySet()) formBody.add(key, params.get(key));
        return formBody.build();
    }

    private static RequestBody getFormDataBody(Req req) {
        String boundary = "--dio-boundary-" + new Random().nextInt(42949) + "" + new Random().nextInt(67296);
        MultipartBody.Builder builder = new MultipartBody.Builder(boundary).setType(MultipartBody.FORM);
        Map<String, String> params = Json.toMap(req.getData());
        for (String key : params.keySet()) builder.addFormDataPart(key, params.get(key));
        return builder.build();
    }

    private static void setHeader(QuickJSContext ctx, Response res, JSObject object) {
        for (Map.Entry<String, List<String>> entry : res.headers().toMultimap().entrySet()) {
            if (entry.getValue().size() == 1) ctx.setProperty(object, entry.getKey(), entry.getValue().get(0));
            if (entry.getValue().size() >= 2) ctx.setProperty(object, entry.getKey(), new JSUtils<String>().toArray(ctx, entry.getValue()));
        }
    }
    public static void cancelByTag(Object tag) {
        try {
            if (client != null) {
                for (Call call : client.dispatcher().queuedCalls()) {
                    if (tag.equals(call.request().tag())) {
                        call.cancel();
                    }
                }
                for (Call call : client.dispatcher().runningCalls()) {
                    if (tag.equals(call.request().tag())) {
                        call.cancel();
                    }
                }
            }
            OkGo.getInstance().cancelTag(tag);
            cancelDefaultClient(tag);
        } catch (Exception e) {
        }
    }

    private static void cancelDefaultClient(Object tag) {
        OkHttpClient defaultClient = OkGoHelper.getDefaultClient();
        if (defaultClient == null || tag == null) return;
        for (Call call : defaultClient.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : defaultClient.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }

private static byte[] gunzip(byte[] compressed) {
    try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressed);
         java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bais);
         java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
        byte[] buf = new byte[8192];
        int len;
        while ((len = gis.read(buf)) != -1) baos.write(buf, 0, len);
        return baos.toByteArray();
    } catch (Exception e) {
        return compressed; // 解压失败就原样返回
    }
}



private static String parseCharset(String contentType) {
    if (contentType == null) return null;
    for (String part : contentType.split(";")) {
        part = part.trim();
        if (part.toLowerCase().startsWith("charset=")) {
            return part.substring(8).trim();
        }
    }
    return null;
}

}
