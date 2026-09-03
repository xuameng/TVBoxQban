package xyz.doikki.videoplayer.exo;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.ArrayList;

/**
 * xuameng TV 专用 DvPrioritizedRenderersFactory:
 * - 强制把 视频确解能播DV直接播放，不能播放 把DV 当成 HEVC 处理，绕过 DV 专用解码器
 */

public class DvPrioritizedRenderersFactory extends DefaultRenderersFactory {

    public DvPrioritizedRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildVideoRenderers(
            Context context,
            int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector, // 这个参数我们不用，自己控制
            boolean enableDecoderFallback,
            android.os.Handler eventHandler,
            VideoRendererEventListener eventListener,
            long allowedVideoJoiningTimeMs,
            @NonNull ArrayList<Renderer> out) {

        // =========================================================
        // 1. 第一优先级：原生 DV 硬解（使用系统默认 Selector，不修改 mimeType）
        // =========================================================
        out.add(new MediaCodecVideoRenderer(
                context,
                MediaCodecSelector.DEFAULT,      // 保持原始 DV mimeType
                allowedVideoJoiningTimeMs,
                true,                            // 开启 Fallback
                eventHandler,
                eventListener,
                50                               // maxDroppedFramesToNotify
        ));

        // =========================================================
        // 2. 第二优先级：HEVC 硬解兜底（把 DV 当 HEVC 处理）
        // =========================================================
        out.add(new MediaCodecVideoRenderer(
                context,
                new HevcFallbackMediaCodecSelector(), // HevcFallbackMediaCodecSelector.java DV → HEVC
                allowedVideoJoiningTimeMs,
                true,
                eventHandler,
                eventListener,
                50
        ));

    }
}
