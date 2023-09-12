package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import org.xwalk.core.XWalkInitializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class XWalkUtils {

    private static XWalkInitializer xWalkInitializer = null;

    public interface XWalkState {
        void success();

        void fail();

        void ignore();
    }



    static String apkPath(Context context) {
        return context.getCacheDir().getAbsolutePath() + "/XWalkRuntimeLib.apk";
    }

    static String libExtractPath(Context context) {
        // XWalkEnvironment.getExtractedCoreDir
        return context.getDir(/*XWALK_CORE_EXTRACTED_DIR*/"extracted_xwalkcore", Context.MODE_PRIVATE).getAbsolutePath();
    }

}
