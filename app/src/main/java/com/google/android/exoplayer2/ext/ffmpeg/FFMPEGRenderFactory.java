import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.audio.AudioSink;

import java.util.ArrayList;

public class FFMPEGRenderFactory extends DefaultRenderersFactory {

    public FFMPEGRenderFactory(Context context) {
        super(context);
    }

@Override
protected void buildAudioRenderers(
    Context context,
    int extensionRendererMode,
    MediaCodecSelector mediaCodecSelector,
    boolean playClearSamplesWithoutKeys,
    AudioSink audioSink,
    Handler eventHandler,
    AudioRendererEventListener eventListener,
    ArrayList<Renderer> out) {

    // 1. 调用父类方法构建基础渲染器
    super.buildAudioRenderers(
        context,
        extensionRendererMode,
        mediaCodecSelector,
        playClearSamplesWithoutKeys,
        audioSink,
        eventHandler,
        eventListener,
        out);

    // 2. 创建FFmpeg音频渲染器实例
    out.add(new FfmpegAudioRenderer(
        eventHandler, 
        eventListener,
        audioSink
    ));
}

}

