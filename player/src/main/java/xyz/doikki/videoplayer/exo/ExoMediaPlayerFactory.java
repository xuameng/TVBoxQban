package xyz.doikki.videoplayer.exo;

import android.content.Context;

import xyz.doikki.videoplayer.player.PlayerFactory;

public class ExoMediaPlayerFactory extends PlayerFactory<ExoMediaPlayer> {

    public static ExoMediaPlayerFactory create() {
        return new ExoMediaPlayerFactory();
    }

    @Override
    public ExoMediaPlayer createPlayer(Context context) {
        return new ExoMediaPlayer(context);
    }
}

    @SuppressLint("UnsafeOptInUsageError")
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            getExoSelectedTrack();
            for (int groupArrayIndex = 0; groupArrayIndex < trackInfo.getRendererCount(); groupArrayIndex++) {
                TrackGroupArray groupArray = trackInfo.getTrackGroups(groupArrayIndex);
                for (int groupIndex = 0; groupIndex < groupArray.length; groupIndex++) {
                    TrackGroup group = groupArray.get(groupIndex);
                    for (int formatIndex = 0; formatIndex < group.length; formatIndex++) {
                        Format format = group.getFormat(formatIndex);
                        if (MimeTypes.isAudio(format.sampleMimeType)) {
                            String trackName = (data.getAudio().size() + 1) + "：" + trackNameProvider.getTrackName(format) + "[" + format.codecs + "]";
                            TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = "";
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(audioId) && audioId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addAudio(t);
                        } else if (MimeTypes.isText(format.sampleMimeType)) {
                            String trackName = (data.getSubtitle().size() + 1) + "：" + trackNameProvider.getTrackName(format);
                            TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = "";
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(subtitleId) && subtitleId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addSubtitle(t);
                        }
                    }
                }
            }
        }
        return data;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void getExoSelectedTrack() {
        audioId = "";
        subtitleId = "";        
        for (Tracks.Group group : mMediaPlayer.getCurrentTracks().getGroups()) {
            if (!group.isSelected()) continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                if (!group.isTrackSelected(trackIndex)) continue;
                Format format = group.getTrackFormat(trackIndex);
                if (MimeTypes.isAudio(format.sampleMimeType)) {
                    audioId = format.id;
                }
                if (MimeTypes.isText(format.sampleMimeType)) {
                    subtitleId = format.id;
                }                
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrack(@Nullable TrackInfoBean videoTrackBean) {
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            if (videoTrackBean == null) {
                for (int renderIndex = 0; renderIndex < trackInfo.getRendererCount(); renderIndex++) {
                    if (trackInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().getParameters().buildUpon();
                        parametersBuilder.setRendererDisabled(renderIndex, true);
                        getTrackSelector().setParameters(parametersBuilder);
                        break;
                    }
                }
            } else {
                TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(videoTrackBean.renderId);
                @SuppressLint("UnsafeOptInUsageError") DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(videoTrackBean.trackGroupId, videoTrackBean.trackId);
                DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
                parametersBuilder.setRendererDisabled(videoTrackBean.renderId, false);
                parametersBuilder.setSelectionOverride(videoTrackBean.renderId, trackGroupArray, override);
                getTrackSelector().setParameters(parametersBuilder);
            }
        }

    }
