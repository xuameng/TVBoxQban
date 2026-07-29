package com.github.catvod.net;

public class OkResult {
    private final int code;
    private final String body;

    public OkResult(int code, String body) {
        this.code = code;
        this.body = body;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return body;
    }
}