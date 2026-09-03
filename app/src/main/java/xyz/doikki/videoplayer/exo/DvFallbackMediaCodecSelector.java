package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;

import java.util.List;

/**
 * xuameng TV 专用 MediaCodecSelector:
 * - 强制把 DV 当成 HEVC 处理，绕过 DV 专用解码器
 */
public class DvFallbackMediaCodecSelector implements MediaCodecSelector {

    private final boolean forceSoftwareDecode;

    public DvFallbackMediaCodecSelector(boolean forceSoftwareDecode) {
        this.forceSoftwareDecode = forceSoftwareDecode;
    }

    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder
    ) throws MediaCodecUtil.DecoderQueryException {

        // 只在软解模式下，DV → HEVC
        if (forceSoftwareDecode && "video/dolby-vision".equals(mimeType)) {
            mimeType = "video/hevc";
        }

        return MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
        );
    }
}