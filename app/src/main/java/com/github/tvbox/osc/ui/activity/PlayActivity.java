package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.github.tvbox.osc.util.parser.SuperParse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.IjkMediaPlayer;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.util.StringUtils;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.SubtitleDialog;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.XWalkUtils;
import com.github.tvbox.osc.util.thunder.Jianpian;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.text.Cue;
import com.github.tvbox.osc.bean.IJKCode;  //xuamengIJK切换用

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;  //xuameng 修复B站base64视频解析URL为 JSON的情况
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import me.jessyan.autosize.AutoSize;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.ProgressManager;
import com.github.tvbox.osc.util.SubtitleHelper; //xuameng 保存字幕颜色信息用

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import com.github.tvbox.osc.ui.dialog.DanmuSettingDialog;  //xuameng 弹幕
import com.github.tvbox.osc.player.danmu.DanmuLoadController; //xuameng 弹幕
import com.github.tvbox.osc.api.DanmakuApi; //xuameng 弹幕

public class PlayActivity extends BaseActivity {
    private MyVideoView mVideoView;
    private TextView mPlayLoadTip;
    private ImageView mPlayLoadErr;
    private ProgressBar mPlayLoading;
    private VodController mController;
    private SourceViewModel sourceViewModel;
    private Handler mHandler;

    private long videoDuration = -1;
    private boolean isJianpian = false;  //xuameng判断视频是否为荐片
    private boolean selectExoTrack = false;  //xuameng判断exo选择音轨
    private int mRetryCountExo = 0;  //xuameng播放出错计数器
    private int mRetryCountIjk = 0;  //xuameng播放出错计数器
    private int mRetryCountJP = 0;  //xuameng播放出错计数器
    private static final int MAX_RETRIES = 2;  //xuameng播放出错切换2次
    private boolean isChineseSubtitle = false;   //xuameng 判断中文字幕
	private int currentSubtitleStyle = 0; // xuameng当前字幕颜色索引

    private DanmakuView mDanmuView;  //xuameng 弹幕
    private DanmuLoadController danmuLoadController;  //xuameng 弹幕

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            mController.mSubtitleView.setTextSize((int) event.obj);
            mController.mLrcView.setNormalTextSize((int) event.obj); //xuameng 设置LRC歌词 全屏非全屏状态同步
            mController.mLrcView.setHighlightTextSize((int) event.obj); //xuameng 设置LRC歌词 全屏非全屏状态同步
        } else if (event.type == RefreshEvent.TYPE_CLOSE_PLAY_ACTIVITY) {  //xuameng 远程关闭playactivity 用于push推送解析刷新
            // 收到指令，执行关闭
            finish(); 
        } else if (event.type == RefreshEvent.TYPE_SET_DANMU_SETTINGS) {  //xuameng 弹幕
            setDanmuViewSettings(event.obj instanceof Boolean && (Boolean) event.obj);
        } else if (event.type == RefreshEvent.TYPE_DANMU_REFRESH) {  //xuameng 弹幕
            checkDanmu(event.obj instanceof String ? (String) event.obj : "");
        }
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        initViewModel();
        initData();
        initDanmuView();  //xuameng 弹幕
        Hawk.put(HawkConfig.PLAYER_IS_LIVE,false);  //xuameng新增
        HawkConfig.exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕
    }

    private void initDanmuView() {  //xuameng 弹幕
        mDanmuView = mController.getDanmuView();
        danmuLoadController = new DanmuLoadController(mVideoView, mController, mDanmuView);
    }

    private void setDanmuViewSettings(boolean reload) {  //xuameng 弹幕
        if (danmuLoadController != null) danmuLoadController.applySettings(reload);
    }

    private void checkDanmu(String danmu) { //xuameng 弹幕
        if (danmuLoadController != null) {
            VodInfo.VodSeries series = mVodInfo == null ? null : getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
            danmuLoadController.check(danmu, mVodInfo == null ? "" : mVodInfo.name, series == null ? "" : series.name);
        }
    }

    private void startDanmuIfReady() { //xuameng 弹幕
        if (danmuLoadController != null) danmuLoadController.startIfReady();
    }

    private void resetDanmuState() { //xuameng 弹幕
        if (danmuLoadController != null) danmuLoadController.reset();
    }

    public long getSavedProgress(String url) {
        int st = 0;
        try {
            st = mVodPlayerCfg.getInt("st");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        long skip = st * 1000;
        if (CacheManager.getCache(MD5.string2MD5(url)) == null) {
            return skip;
        }
        long rec = (long) CacheManager.getCache(MD5.string2MD5(url));
        if (rec < skip)
            return skip;
        return rec;
    }

    private void initView() {
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case 100:
                        stopParse();
                        errorWithRetry("嗅探错误", false);
                        break;
                }
                return false;
            }
        });
        mVideoView = findViewById(R.id.mVideoView);
        mPlayLoadTip = findViewById(R.id.play_load_tip);
        mPlayLoading = findViewById(R.id.play_loading);
        mPlayLoadErr = findViewById(R.id.play_load_error);
        mController = new VodController(this);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setGestureEnabled(true);
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                if (videoDuration ==0) return;
                CacheManager.save(MD5.string2MD5(url), progress);
            }

            @Override
            public long getSavedProgress(String url) {
                return PlayActivity.this.getSavedProgress(url);
            }
        };
        mVideoView.setProgressManager(progressManager);
        mController.setListener(new VodController.VodControlListener() {
            @Override
            public void showDanmuSetting() { //xuameng 弹幕
                DanmuSettingDialog dialog = new DanmuSettingDialog(PlayActivity.this, mDanmuView);
                dialog.show();
            }
            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = progressKey;
                PlayActivity.this.playNext(rmProgress);
                if (rmProgress && preProgressKey != null)
                    CacheManager.delete(MD5.string2MD5(preProgressKey), 0);
            }

            @Override
            public void playPre() {
                PlayActivity.this.playPrevious();
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                mRetryCountExo = 0;  //xuameng播放出错计数器重置
                mRetryCountIjk = 0;
                mRetryCountJP = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfg.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
            }

            @Override
            public void replay(boolean replay) {
                autoRetryCount = 0;
                mRetryCountExo = 0;  //xuameng播放出错计数器重置
                mRetryCountIjk = 0;
                mRetryCountJP = 0;
                if(replay){  //xuameng新增
                    play(true);
                }else {
                    play(false);
                }  //xuameng新增完
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false);
            }

            public void hideTipXu() {        //xuameng隐藏错误信息
               if (mPlayLoadTip.getVisibility() == View.VISIBLE){
                   mPlayLoadTip.setVisibility(View.GONE);
               }
               if (mPlayLoading.getVisibility() == View.VISIBLE){
                   mPlayLoading.setVisibility(View.GONE);
               }
               if (mPlayLoadErr.getVisibility() == View.VISIBLE){
                   mPlayLoadErr.setVisibility(View.GONE);
               }
            }

            @Override
            public void selectSubtitle() {
                try {
                    selectMySubtitle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void selectAudioTrack() {
                selectMyAudioTrack();
            }

            @Override
            public void prepared() {
                initSubtitleView();
                startDanmuIfReady(); //xuameng 弹幕
            }
            @Override
            public void startPlayUrl(String url, HashMap<String, String> headers) {
                goPlayUrl(url, headers);
            }
        });
        mVideoView.setVideoController(mController);
    }

    //设置字幕
    void setSubtitle(String path) {
        if (path != null && path .length() > 0) {
            // 设置字幕
            mController.mSubtitleView.setVisibility(View.GONE);
            mController.mSubtitleView.setSubtitlePath(path);
            mController.mSubtitleView.setVisibility(View.VISIBLE);
        }
    }

    void selectMySubtitle() throws Exception {
        SubtitleDialog subtitleDialog = new SubtitleDialog(PlayActivity.this);
        int playerType = mVodPlayerCfg.getInt("pl");
        if (mController.mSubtitleView.hasInternal && playerType == 1 ||mController.mSubtitleView.hasInternal && playerType == 2) {
            subtitleDialog.selectInternal.setVisibility(View.VISIBLE);
        } else {
            subtitleDialog.selectInternal.setVisibility(View.GONE);
        }

        // xuameng 读取保存的字幕颜色信息
        currentSubtitleStyle = SubtitleHelper.getTextStyle();
        // xuameng初始化对话框时传递当前样式和颜色数组
        if (subtitleDialog != null) {
            subtitleDialog.updateStyleButtons(currentSubtitleStyle, subtitleColors);
        }

        subtitleDialog.setSubtitleViewListener(new SubtitleDialog.SubtitleViewListener() {
            @Override
            public void setTextSize(int size) {
                mController.mSubtitleView.setTextSize(size);
                mController.mLrcView.setNormalTextSize(size);  //xuameng 设置LRC歌词字体大小
                mController.mLrcView.setHighlightTextSize(size);  //xuameng 设置LRC歌词字体大小
            }
            @Override
            public void setSubtitleDelay(int milliseconds) {
                mController.mSubtitleView.setSubtitleDelay(milliseconds);
            }
            @Override
            public void selectInternalSubtitle() {
                selectMyInternalSubtitle();
            }
            @Override
            public void setTextStyle(int style) {
                setSubtitleViewTextStyle(style);
                if (subtitleDialog != null) {
                    subtitleDialog.updateStyleButtons(style, subtitleColors);  // xuameng 更新字幕颜色信息
                }
            }
        });
        subtitleDialog.setSearchSubtitleListener(new SubtitleDialog.SearchSubtitleListener() {
            @Override
            public void openSearchSubtitleDialog() {
                SearchSubtitleDialog searchSubtitleDialog = new SearchSubtitleDialog(PlayActivity.this);
                searchSubtitleDialog.setSubtitleLoader(new SearchSubtitleDialog.SubtitleLoader() {
                    @Override
                    public void loadSubtitle(Subtitle subtitle) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String zimuUrl = subtitle.getUrl();
                                LOG.i("echo-Remote Subtitle Url: " + zimuUrl);
                                setSubtitle(zimuUrl);//设置字幕
                                if (mController.mExoSubtitleView.getVisibility() == View.VISIBLE){  //xuameng 使用搜索字幕隐藏EXO PGS字幕
                                    mController.mExoSubtitleView.setVisibility(View.GONE);
                                }
                                HawkConfig.exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕 
                                if (searchSubtitleDialog != null) {
                                    searchSubtitleDialog.dismiss();
                                }
                            }
                        });
                    }
                });
                if(mVodInfo.playFlag.contains("Ali")||mVodInfo.playFlag.contains("parse")){
                    searchSubtitleDialog.setSearchWord(mVodInfo.playNote);
                }else {
                    searchSubtitleDialog.setSearchWord(mVodInfo.name);
                }
                searchSubtitleDialog.show();
            }
        });
        subtitleDialog.setLocalFileChooserListener(new SubtitleDialog.LocalFileChooserListener() {
            @Override
            public void openLocalFileChooserDialog() {
                new ChooserDialog(PlayActivity.this,R.style.FileChooserXu)   //xuameng本地字幕风格
                        .withResources(R.string.title_choose_file, R.string.title_choose, R.string.dialog_cancel)  //xuameng本地字幕风格
                        .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                        .withStartFile("/storage/emulated/0/Download")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                LOG.i("echo-Local Subtitle Path: " + path);
                                setSubtitle(path);//设置字幕
                                if (mController.mExoSubtitleView.getVisibility() == View.VISIBLE){  //xuameng 使用搜索字幕隐藏EXO PGS字幕
                                    mController.mExoSubtitleView.setVisibility(View.GONE);
                                }
                                HawkConfig.exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕
                            }
                        })
                        .build()
                        .show();
            }
        });
        subtitleDialog.show();
    }

    void setSubtitleViewTextStyle(int style) {   //xuameng 设置字幕颜色
        if (style >= 0 && style < subtitleColors.length) {
            // xuameng保存当前样式
            currentSubtitleStyle = style;
            SubtitleHelper.setTextStyle(style); // xuameng持久化存储
            // xuameng设置字幕颜色
            mController.mSubtitleView.setTextColor(subtitleColors[style]);
		    mController.mLrcView.setHighlightColor(subtitleColors[style]);  //xuameng LRC歌词字幕 高亮颜色
            // xuameng更新按钮颜色        
        }
    }

    void selectMyAudioTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer) mediaPlayer).getTrackInfo();
        }
        if (trackInfo == null) {
            App.showToastShort(mContext, "没有音轨！");
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getAudio();
        if (bean.size() < 1){
            App.showToastShort(mContext, "没有内置音轨！");
            return;
        }

        final int selectedId = trackInfo.getAudioSelected(false);  //xuameng判断选中的音轨
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(PlayActivity.this);
        dialog.setTip("切换音轨");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                if (selectedId == 99999) { // xuameng99999表示未选中
                    App.showToastShort(mContext, "切换音轨失败！请切换解码方式或刷新重试！");
                    return;
                }
                try {
                    for (TrackInfoBean audio : bean) {
                        audio.selected = audio.trackId == value.trackId;
                    }
                    long progress = mediaPlayer.getCurrentPosition() - 3000L;//XUAMENG保存当前进度，//XUAMENG保存当前进度，回退3秒
                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        ((IjkMediaPlayer)mediaPlayer).setTrack(value.trackId,progressKey);
                    }
                    if (mediaPlayer instanceof EXOmPlayer) {
                        ((EXOmPlayer) mediaPlayer).selectExoTrackAudio(value,progressKey);
                        selectExoTrack = true;  //xuameng 选择音轨
                    }
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mediaPlayer.seekTo(progress);
                        }
                    }, 300);
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换音轨出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                String name = val.name.replace("AUDIO,", "");
                name = name.replace("N/A,", "");
                name = name.replace(" ", "");
                return name + (StringUtils.isEmpty(val.language) ? "" : " " + val.language);
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }
        }, bean, trackInfo.getAudioSelected(false));
        dialog.show();
    }

    void selectMyInternalSubtitle() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer)mediaPlayer).getTrackInfo();
        }
        if (trackInfo == null) {
            App.showToastShort(mContext, "没有内置字幕！");
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getSubtitle();
        if (bean.size() < 1) {
            App.showToastShort(mContext, "没有内置字幕！");
            return;
        }
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(PlayActivity.this);
        dialog.setTip("切换内置字幕");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                mController.mSubtitleView.setVisibility(View.VISIBLE);
                try {
                    for (TrackInfoBean subtitle : bean) {
                        subtitle.selected = subtitle.trackId == value.trackId;
                    }
                    long progress = mediaPlayer.getCurrentPosition() - 3000L;//XUAMENG保存当前进度，//XUAMENG保存当前进度，回退3秒
                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        mController.mSubtitleView.destroy();
                        mController.mSubtitleView.clearSubtitleCache();
                        mController.mSubtitleView.isInternal = true;
                        ((IjkMediaPlayer)mediaPlayer).setTrack(value.trackId);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                            }
                        }, 300);
                    }

					// xuameng判断选中的字幕是否为 PGS 格式
                    boolean isPgsSubtitle = value.language != null && value.language.toLowerCase().contains("pgs");
                    if (mediaPlayer instanceof EXOmPlayer) {

                        if (isPgsSubtitle) {
                            // xuameng选中的是 PGS 字幕：使用 ExoPlayer 内置视图
                            mController.mExoSubtitleView.setVisibility(View.VISIBLE);
                            mController.mSubtitleView.setVisibility(View.GONE);
                            mController.mSubtitleView.destroy();
                            mController.mSubtitleView.clearSubtitleCache();
                            mController.mSubtitleView.onSubtitleChanged(null);
                            mController.mSubtitleView.isInternal = true; // 外部视图处理
                            HawkConfig.exoSubtitle = true;  //xuameng 判断当前是否播放EXO内置字幕
                        } else {
                            // xuameng选中的是其他字幕：使用外部视图
                            mController.mExoSubtitleView.setVisibility(View.GONE);
                            mController.mSubtitleView.setVisibility(View.VISIBLE);
                            mController.mSubtitleView.destroy();
                            mController.mSubtitleView.clearSubtitleCache();
                            mController.mSubtitleView.isInternal = true; // 外部视图处理
                            HawkConfig.exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕
                        }

                        ((EXOmPlayer)mediaPlayer).selectExoTrack(value);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                            }
                        }, 300);
                    }
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换内置字幕出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name + (StringUtils.isEmpty(val.language) ? "" : " " + val.language);
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }
        }, bean, trackInfo.getSubtitleSelected(false));
        dialog.show();
    }

    void setTip(String msg, boolean loading, boolean err) {
        runOnUiThread(new Runnable() {//影魔 解决解析偶发闪退
            @Override
            public void run() {
                mPlayLoadTip.setText(msg);
                mPlayLoadTip.setVisibility(View.VISIBLE);
                mPlayLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
                mPlayLoadErr.setVisibility(err ? View.VISIBLE : View.GONE);
            }
        });
    }

    void hideTip() {
        mPlayLoadTip.setVisibility(View.GONE);
        mPlayLoading.setVisibility(View.GONE);
        mPlayLoadErr.setVisibility(View.GONE);
    }

    void errorWithRetry(String err, boolean finish) {
        if (!autoRetry()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (finish) {
                        setTip(err, false, true);
                        App.showToastShort(mContext, err);
                        finish();
                    } else {
                        setTip(err, false, true);
                    }
                }
            });
        }
    }

    void playUrl(String url, HashMap<String, String> headers) {
        if(!url.startsWith("data:application"))EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, url));//更新播放地址
        if (!Hawk.get(HawkConfig.M3U8_PURIFY, false)) {   //xuameng广告过滤
            goPlayUrl(url,headers);
            return;
        }
        if (url.startsWith("http://127.0.0.1") || !url.contains(".m3u8")) {
            goPlayUrl(url,headers);
            return;
        }
        if(DefaultConfig.noAd(mVodInfo.playFlag)){
            goPlayUrl(url,headers);
            return;
        }
        LOG.i("echo-playM3u8:" + url);
        mController.playM3u8(url,headers);
    }
    void goPlayUrl(String url, HashMap<String, String> headers) {
        LOG.i("echo-goPlayUrl:" + url);
        final String finalUrl = url;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopParse();
                if (mVideoView != null) {
                    mVideoView.release();
                    if (finalUrl != null) {
                        String url = finalUrl;
                        if (url.startsWith("push://") && ApiConfig.get().getSource("push_agent") != null) {  //xuameng 如是推送链接直接返回由detailactivity重新解析
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));  //xuameng 通知detailactivity退出playactivity页面以便更新数据
                            return;
                        }
                        try {
                            int playerType = mVodPlayerCfg.getInt("pl");
                            if (playerType >= 10) {
                                VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
                                String playTitle = mVodInfo.name + " " + vs.name;
                                setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                                boolean callResult = false;
                                long progress = getSavedProgress(progressKey);
                                callResult = PlayerHelper.runExternalPlayer(playerType, PlayActivity.this, url, playTitle, playSubtitle, headers, progress);
                                setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (callResult ? "成功" : "失败"), callResult, !callResult);
                                return;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        hideTip();

                        //xuameng 修复B站base64视频解析URL为 JSON的情况
                        if (url.startsWith("[")){
                            try {
                                JSONArray array = new JSONArray(url);
                                for (int i = 0; i < array.length(); i++) {
                                    String s = array.optString(i);
                                    if (s.contains("application/dash+xml;base64,")) {     //xuameng 一种写法
                                        String base64 = s.substring(s.indexOf("base64,") + 7)
                                                .replaceAll("\\s+", "");
                                        App.getInstance().setDashData(base64);
                                        url = ControlManager.get().getAddress(true) + "dash/proxy.mpd";
                                        break;
                                    } else if (s.contains("proxy://")) {   //xuameng 另一种写法
                                        String base = ControlManager.get().getAddress(true);
                                        url = base + "proxy?" + s.substring("proxy://".length());
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        //xuameng 修复B站base64视频解析URL为 JSON的情况完

                        if (url.startsWith("data:application/dash+xml;base64,")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                            App.getInstance().setDashData(url.split("base64,")[1]);
                            url = ControlManager.get().getAddress(true) + "dash/proxy.mpd";
                        } else if (url.contains(".mpd") || url.contains("type=mpd")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                        } else {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                        }
                        mVideoView.setProgressKey(progressKey);
                        if (headers != null) {
                            mVideoView.setUrl(url, headers);
                        } else {
                            mVideoView.setUrl(url);
                        }
                        mVideoView.start();
                        mController.resetSpeed();
                    }
                }
            }
        });
    }

    private void initSubtitleView() {
        TrackInfo trackInfo = null;

        // xuameng应用保存的字幕颜色
        int savedStyle = SubtitleHelper.getTextStyle();
        if (savedStyle >= 0 && savedStyle < subtitleColors.length) {
            mController.mSubtitleView.setTextColor(subtitleColors[savedStyle]);
            mController.mLrcView.setHighlightColor(subtitleColors[savedStyle]);
            currentSubtitleStyle = savedStyle;
        }

        if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;
            }else{
                mController.mSubtitleView.hasInternal = false;  //xuameng修复切换播放器内置字幕不刷新
            }
            final int selectedIdIjk = trackInfo.getAudioSelected(false);  //xuameng判断选中的音轨
            Hawk.put(HawkConfig.IJK_PROGRESS_KEY, progressKey);  //xuameng存储进程KEY
            if (selectedIdIjk != 99999) { // xuameng99999表示未选中
               ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).loadDefaultTrack(trackInfo,progressKey);      //xuameng记忆选择音轨  如果未选中音轨就不选择记忆音轨
            }
            ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).setOnTimedTextListener(new IMediaPlayer.OnTimedTextListener() {
                @Override
                public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
                    if(text==null)return;   //xuameng
                    if (mController.mSubtitleView.isInternal) {
                        com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                        subtitle.content = text.getText();
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }

     if (mVideoView.getMediaPlayer() instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer) (mVideoView.getMediaPlayer())).getTrackInfo();

            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;

                // 获取当前选中的字幕轨道
                TrackInfoBean selectedSubtitleTrack = null;
                for (TrackInfoBean subtitleTrack : trackInfo.getSubtitle()) {
                    if (subtitleTrack.selected) {
                        selectedSubtitleTrack = subtitleTrack;
                        break;
                    }
                }

                // 判断当前选中的字幕是否为 PGS
                boolean isPgsSelected = false;
                if (selectedSubtitleTrack != null && selectedSubtitleTrack.language != null) {
                    try {
                        isPgsSelected = selectedSubtitleTrack.language.toLowerCase().contains("pgs");
                    } catch (Exception e) {
                    // 处理可能的异常情况
                        isPgsSelected = false;
                    }
                }

                if (isPgsSelected) {
                    // 当前选中的是 PGS 字幕，使用 ExoPlayer 内置视图
                    ((EXOmPlayer) mVideoView.getMediaPlayer()).setSubtitleView(mController.mExoSubtitleView);   //xuameng绑定Exo字幕视图
                    mController.mExoSubtitleView.setVisibility(View.VISIBLE);
                    mController.mSubtitleView.setVisibility(View.GONE);
                    HawkConfig.exoSubtitle = true;  //xuameng 判断当前是否播放EXO内置字幕
                } else {
                    // 当前选中的是其他格式字幕，使用外部视图
                    mController.mExoSubtitleView.setVisibility(View.GONE);
                    mController.mSubtitleView.setVisibility(View.VISIBLE);
                    HawkConfig.exoSubtitle = false;  //xuameng 判断当前是否播放EXO内置字幕
                }
            } else {
                mController.mSubtitleView.hasInternal = false;
            }

            final int selectedIdExo = trackInfo.getAudioSelected(false);  //xuameng判断选中的音轨
            Hawk.put(HawkConfig.EXO_PROGRESS_KEY, progressKey);  //xuameng存储进程KEY
            if (selectedIdExo != 99999) { // xuameng99999表示未选中
                ((EXOmPlayer) (mVideoView.getMediaPlayer())).loadDefaultTrack(progressKey);      //xuameng记忆选择音轨  如果未选中音轨就不选择记忆音轨
            }
            ((EXOmPlayer) (mVideoView.getMediaPlayer())).setOnTimedTextListener(new Player.Listener() {
                @Override
                public void onCues(@NonNull List<Cue> cues) {
                    if (cues.size() > 0) {
                        CharSequence ss = cues.get(0).text;
                        if (ss != null && mController.mSubtitleView.isInternal) {
                            com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                            subtitle.content = ss.toString();
                            mController.mSubtitleView.onSubtitleChanged(subtitle);
                        }
                    }else{
                        com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                        subtitle.content = "";
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }
        mController.mSubtitleView.bindToMediaPlayer(mVideoView.getMediaPlayer());
        mController.mSubtitleView.setPlaySubtitleCacheKey(subtitleCacheKey);
        String subtitlePathCache = (String)CacheManager.getCache(MD5.string2MD5(subtitleCacheKey));
        if (subtitlePathCache != null && !subtitlePathCache.isEmpty()) {
            mController.mSubtitleView.setSubtitlePath(subtitlePathCache);
        } else {
            if (playSubtitle != null && playSubtitle .length() > 0) {
                mController.mSubtitleView.setSubtitlePath(playSubtitle);
            } else {
                if (mController.mSubtitleView.hasInternal) {
                    mController.mSubtitleView.isInternal = true;
                    if (trackInfo != null && !trackInfo.getSubtitle().isEmpty()) {
                        List<TrackInfoBean> subtitleTrackList = trackInfo.getSubtitle();
                        int selectedIndex = trackInfo.getSubtitleSelected(true);
                        boolean hasCh =false;
                        for(TrackInfoBean subtitleTrackInfoBean : subtitleTrackList) {
                        String lowerLang = subtitleTrackInfoBean.language.toLowerCase();
                        if (isChineseSubtitle(subtitleTrackInfoBean)){    //xuameng修复EXO播放器也可以默认选择中文字幕
                            hasCh=true;
                            if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer){
                                if (selectedIndex != subtitleTrackInfoBean.trackId) {
                                    ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).setTrack(subtitleTrackInfoBean.trackId);
                                }
                                }else if (mVideoView.getMediaPlayer() instanceof EXOmPlayer){
                                    ((EXOmPlayer)(mVideoView.getMediaPlayer())).selectExoTrack(subtitleTrackInfoBean);
                                }
                                break;
                            }
                        }
                        if(!hasCh){
                            if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer){
                                ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).setTrack(subtitleTrackList.get(0).trackId);
                            }else if (mVideoView.getMediaPlayer() instanceof EXOmPlayer){
                                ((EXOmPlayer)(mVideoView.getMediaPlayer())).selectExoTrack(subtitleTrackList.get(0));
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isChineseSubtitle(TrackInfoBean subtitleTrackInfoBean) {   //xuameng判断中文字幕
       if (subtitleTrackInfoBean == null || subtitleTrackInfoBean.language == null) {
            return false;
        }
    
        String lowerLang = subtitleTrackInfoBean.language.toLowerCase();
    
        // 检测顺序很重要，先检测更具体的标识
        if (lowerLang.contains("中英")) {
            return true;
        }
    
        // 其他中文标识检测
        if (lowerLang.contains("简体") || lowerLang.contains("中文") || 
            lowerLang.contains("国语") || lowerLang.contains("国配") ||
            lowerLang.contains("普通") || lowerLang.contains("粤语") || 
            lowerLang.contains("双语") || lowerLang.contains("繁体") ||
            lowerLang.contains("zh") || lowerLang.contains("ch")) {
            return true;
        }
    
        return false;
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject info) {
                if (info != null) {
                    try {
                        progressKey = info.optString("proKey", null);
                        boolean parse = info.optString("parse", "1").equals("1");
                        boolean jx = info.optString("jx", "0").equals("1");

                        // xuameng优先检查 artwork 字段（歌手图片）
                        if (info.has("artwork")) {
                            String picUrl = info.optString("artwork", "");
                            if (!TextUtils.isEmpty(picUrl)) {
                                mController.setVideoPicUrl(picUrl);  //xuameng 新增给vod显示旋转图片用
                            }
                        }
                        // xuameng优先检查 lrc 字段（歌词字符串）
                        if (info.has("lrc")) {
                            String lrcContent = info.optString("lrc", "");
                            if (!TextUtils.isEmpty(lrcContent) && lrcContent.length() > 10) {
                                // 新增：判断 lrcContent 是否为 URL
                                if (lrcContent.startsWith("http://") || lrcContent.startsWith("https://")) {
                                    // 异步加载网络歌词
                                    loadLrcFromUrl(lrcContent);
                                } else {
                                    // 直接使用歌词文本
                                    mController.setLrcContent(lrcContent);
                                    mController.mLrcView.setVisibility(View.VISIBLE);
                                }
                                playSubtitle = "";
                            } else {
                                playSubtitle = info.optString("subt", "");
                                mController.mLrcView.setVisibility(View.GONE);
                            }
                        } else {
                            playSubtitle = info.optString("subt", "");
                            mController.mLrcView.setVisibility(View.GONE);
                        }

                        // 如果 playSubtitle 仍为空，且存在 subs 字段，则按原有逻辑处理字幕数组
                        if(playSubtitle.isEmpty() && info.has("subs")) {
                            try {
                                JSONObject obj = info.getJSONArray("subs").optJSONObject(0);
                                String url = obj.optString("url", "");
                                if (!TextUtils.isEmpty(url) && !FileUtils.hasExtension(url)) {
                                    String format = obj.optString("format", "");
                                    String name = obj.optString("name", "字幕");
                                    String ext = ".srt";
                                    switch (format) {
                                        case "text/x-ssa":
                                            ext = ".ass";
                                            break;
                                        case "text/vtt":
                                            ext = ".vtt";
                                            break;
                                        case "application/x-subrip":
                                            ext = ".srt";
                                            break;
                                        case "text/lrc":
                                            ext = ".lrc";
                                            break;
                                }
                                String filename = name + (name.toLowerCase().endsWith(ext) ? "" : ext);
                                url += "#" + mController.encodeUrl(filename);
                                }
                                 playSubtitle = url;
                             } catch (Throwable th) {
                                 // 异常处理
                             }
                        }
                        subtitleCacheKey = info.optString("subtKey", null);
                        String playUrl = info.optString("playUrl", "");
                        String msg = info.optString("msg", "");
                        if(!msg.isEmpty()){
                            App.showToastShort(mContext, msg);
                        }
                        String flag = info.optString("flag");
                        String url = info.getString("url");
                        String danmaku = info.optString("danmaku", "");
                        if(url.startsWith("[")){
                            url=mController.firstUrlByArray(url);
                        }
                        HashMap<String, String> headers = null;
                        webUserAgent = null;
                        webHeaderMap = null;
                        if (info.has("header")) {
                            try {
                                JSONObject hds = new JSONObject(info.getString("header"));
                                Iterator<String> keys = hds.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    if (headers == null) {
                                        headers = new HashMap<>();
                                    }
                                    headers.put(key, hds.getString(key));
                                    if (key.equalsIgnoreCase("user-agent")) {
                                        webUserAgent = hds.getString(key).trim();
                                    }
                                }
                                webHeaderMap = headers;
                            } catch (Throwable th) {

                            }
                        }
                        if (parse || jx) {
                            boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                            initParse(flag, userJxList, playUrl, url);
                        } else {
                            mController.showParse(false);
                            playUrl(playUrl + url, headers);
                        }
                      checkDanmu(danmaku); //xuameng 弹幕
                      searchDanmu(danmaku); //xuameng 弹幕
                    } catch (Throwable th) {
                    }
                } else {
                    errorWithRetry("获取播放信息错误", true);
                }
            }
        });
    }

    private void searchDanmu(String danmaku) { //xuameng 弹幕
        if (!TextUtils.isEmpty(danmaku) || !DanmakuApi.canSearch() || mVodInfo == null) return;
        VodInfo.VodSeries series = getCurrentSeries(mVodInfo.playFlag, mVodInfo.playIndex);
        String key = progressKey;
        DanmakuApi.search(mVodInfo.name, series == null ? "" : series.name, new DanmakuApi.SearchCallback() {
            @Override
            public void onFound(String url) {
                if (!TextUtils.equals(key, progressKey)) return;
                checkDanmu(url);
            }

            @Override
            public void onNotFound() {
                if (!TextUtils.equals(key, progressKey)) return;
                checkDanmu("");
            }
        });
    }
    private VodInfo.VodSeries getCurrentSeries(String flag, int index) {
        if (flag == null || mVodInfo == null || mVodInfo.seriesMap == null) {
            return null;
        }
        List<VodInfo.VodSeries> currentList = mVodInfo.seriesMap.get(flag);
        if (currentList == null || currentList.isEmpty()) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(index, currentList.size() - 1));
        return currentList.get(safeIndex);
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
//            mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
            mVodInfo = App.getInstance().getVodInfo();
            sourceKey = bundle.getString("sourceKey");
            sourceBean = ApiConfig.get().getSource(sourceKey);
            String picUrl = bundle.getString("videoPic");  //xuameng 新增给vod显示旋转图片用
            mController.setVideoPicUrl(picUrl);  //xuameng 新增给vod显示旋转图片用
            initPlayerCfg();
            play(false);
        }
    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", (sourceBean.getPlayerType() == -1) ? (int)Hawk.get(HawkConfig.PLAY_TYPE, 1) : sourceBean.getPlayerType() );
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("music")) {    //xuameng音频柱状图
                mVodPlayerCfg.put("music", Hawk.get(HawkConfig.VOD_MUSIC_ANIMATION, false));
            }
            if (!mVodPlayerCfg.has("exocode")) {    //xuameng exo解码
                mVodPlayerCfg.put("exocode", 0);    //xuameng默认选择，大于0为选择
                Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 0);  // 大于0为选择
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    @Override
    public void onBackPressed() {
        App.HideToast();
        if (mController.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.onKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event != null ) {
            if (mController.onKeyDown(keyCode,event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event != null ) {
            if (mController.onKeyUp(keyCode,event)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (danmuLoadController != null) { //xuameng 弹幕
            danmuLoadController.destroy();
            danmuLoadController = null;
        }
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        ClearOtherCache();
        stopLoadWebView(true);
        stopParse();
    }

    private VodInfo mVodInfo;
    private JSONObject mVodPlayerCfg;
    private String sourceKey;
    private SourceBean sourceBean;

    private void playNext(boolean isProgress) {
        boolean hasNext = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            if(isProgress && mVodInfo!= null){
                mVodInfo.playIndex=0;
                App.showToastShort(mContext, "已经是最后一集了！即将跳到第一集继续播放！");
            }else {
                App.showToastShort(mContext, "已经是最后一集了！");
                return;
            }
        }else {
            mVodInfo.playIndex++;
        }
        play(false);
    }

    private void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            App.showToastShort(mContext, "已经是第一集了！");
            return;
        }
        mVodInfo.playIndex--;
        play(false);
    }

    private int autoRetryCount = 0;
    private long lastRetryTime = 0; // 记录上次调用时间（毫秒）  //xuameng新增

    boolean autoRetry() {
        boolean exoCode=Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false); //xuameng EXO默认设置解码
        boolean switchCode=Hawk.get(HawkConfig.VOD_SWITCHDECODE, false); //xuameng 解码切换
        boolean switchPlayer=Hawk.get(HawkConfig.VOD_SWITCHPLAYER, true); //xuameng 播放器切换
        int exoSelect = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);  //xuameng exo解码动态选择
        long currentTime = System.currentTimeMillis();
        int playerType = 0;   //xuameng默认播放器类型
        if (selectExoTrack){   //xuameng如果是EXO在选择音轨就重置次数
            autoRetryCount = 0;
            mRetryCountExo = 0;  //xuameng播放出错计数器重置
            mRetryCountIjk = 0;
            selectExoTrack = false;
        }
        try {
            if (mVodPlayerCfg.has("pl")) {
                playerType = mVodPlayerCfg.getInt("pl");     //xuameng 获取播放器类型
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 如果距离上次重试超过 30 秒（30000 毫秒），重置重试次数
        if (currentTime - lastRetryTime > 30_000) {
            LOG.i("echo-reset-autoRetryCount");
            autoRetryCount = 0;
            mRetryCountExo = 0;  //xuameng播放出错计数器重置
            mRetryCountIjk = 0;
            mRetryCountJP = 0;
        }
        lastRetryTime = currentTime;  // 更新上次调用时间
        if (loadFoundVideoUrls != null && !loadFoundVideoUrls.isEmpty()) {
            autoRetryFromLoadFoundVideoUrls();
            return true;
        }
        if (autoRetryCount <= 1) {
            if (isJianpian && mRetryCountJP < MAX_RETRIES){
                String CachePath = FileUtils.getCachePath();     //xuameng 清空缓存
                File CachePathDir = new File(CachePath); 
                new Thread(() -> {
                    try {
                        if(CachePathDir.exists())FileUtils.cleanDirectory(CachePathDir);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                App.showToastShort(mContext, "播放失败！立即清空缓存！重试！");
                new Handler().postDelayed(new Runnable() {
                @Override
                    public void run() {
                       play(false);
                       mRetryCountJP++;
                    }
                }, 400);
                return true;
            }
            if (isJianpian && mRetryCountJP >= MAX_RETRIES){
                App.showToastShort(mContext, "荐片播放地址获取失败！");
                mRetryCountJP = 0;
                return false;
            }
            if (playerType == 1 && mRetryCountIjk < MAX_RETRIES && switchCode) {     //xuameng播放出错计数器  是否开启解码切换
                try {
                    String ijk = mVodPlayerCfg.getString("ijk");
                    List < IJKCode > codecs = ApiConfig.get().getIjkCodes();
                    for(int i = 0; i < codecs.size(); i++) {
                        if(ijk.equals(codecs.get(i).getName())) {
                            if(i >= codecs.size() - 1) ijk = codecs.get(0).getName();
                            else {
                                ijk = codecs.get(i + 1).getName();
                            }
                            break;
                        }
                    }
                    mVodPlayerCfg.put("ijk", ijk);
                    App.showToastShort(mContext, String.valueOf("播放出错！自动切换IJK" + ijk));
                    mRetryCountIjk++;   //xuameng播放出错计数器
                    mController.setPlayerConfig(mVodPlayerCfg);   //xuameng更新变更
                    mController.updatePlayerCfg();  //xuameng更新变更
                    play(false);
                    return true;
                } catch (JSONException e) {
                      e.printStackTrace();
                }
            }
            if (playerType == 2 && mRetryCountExo < MAX_RETRIES && switchCode) {     //xuameng播放出错计数器   是否开启解码切换
                try {
                    exoSelect = mVodPlayerCfg.getInt("exocode");  //xuameng exo解码动态选择
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (exoSelect == 1 && mRetryCountExo < MAX_RETRIES) {
                    try {
                        mVodPlayerCfg.put("exocode", 2);  //xuameng默认选择，大于0为选择
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);  // 硬解码标记存储
                    App.showToastShort(mContext, "播放出错！自动切换EXO软解码");
                    mRetryCountExo++;   //xuameng播放出错计数器
                } else if (exoSelect == 2 && mRetryCountExo < MAX_RETRIES){
                    try {
                        mVodPlayerCfg.put("exocode", 1);  //xuameng默认选择，大于0为选择
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);  // 软解码标记存储
                    App.showToastShort(mContext, "播放出错！自动切换EXO硬解码");
                    mRetryCountExo++;   //xuameng播放出错计数器
                } else if (exoSelect == 0 && mRetryCountExo < MAX_RETRIES){
                    if (exoCode){
                        try {
                            mVodPlayerCfg.put("exocode", 1);  //xuameng默认选择，大于0为选择
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);  // 软解码标记存储
                        App.showToastShort(mContext, "播放出错！自动切换EXO硬解码");
                        mRetryCountExo++;  //xuameng播放出错计数器
                    }else{
                        try {
                            mVodPlayerCfg.put("exocode", 2);  //xuameng默认选择，大于0为选择
                        } catch (JSONException e) {
                            e.printStackTrace();
                              }
                        Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);  // 软解码标记存储
                        App.showToastShort(mContext, "播放出错！自动切换EXO软解码");
                        mRetryCountExo++;  //xuameng播放出错计数器
                    }
                }
                mController.setPlayerConfig(mVodPlayerCfg);   //xuameng更新变更
                mController.updatePlayerCfg();  //xuameng更新变更
                play(false);
                return true;
           }        
           //切换播放器不占用重试次数
           if (switchPlayer){  //xuameng是否开启播放切换
               if(mController.switchPlayer()){
                   autoRetryCount++;
               }else {
                   autoRetryCount++;
               }
           }else {
               autoRetryCount++;
           }
           mRetryCountExo = 0;  //xuameng播放出错计数器重置
           mRetryCountIjk = 0;	
           play(false);
           return true;
        } else {
            mRetryCountExo = 0;  //xuameng播放出错计数器重置
            mRetryCountIjk = 0;
            autoRetryCount = 0;
            return false;
        }
    }

    void autoRetryFromLoadFoundVideoUrls() {
        String videoUrl = loadFoundVideoUrls.poll();
        HashMap<String,String> header = loadFoundVideoUrlsHeader.get(videoUrl);
        playUrl(videoUrl, header);
    }

    void initParseLoadFound() {
        loadFoundCount.set(0);
        loadFoundVideoUrls = new LinkedList<String>();
        loadFoundVideoUrlsHeader = new HashMap<String, HashMap<String, String>>();
    }

    public void play(boolean reset) {
        if(mVodInfo==null)return;
        isJianpian = false;
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        setTip("正在获取播放信息", true, false);
        String playTitleInfo = mVodInfo.name + " " + vs.name;
        int lengthplayTitleInfo = playTitleInfo.length();
        if (lengthplayTitleInfo <= 7 ){
            mController.setTitle("您正在观看影片：" + playTitleInfo);
        }else if (lengthplayTitleInfo > 7 && lengthplayTitleInfo <= 10 ){
            mController.setTitle("正在观看：" + playTitleInfo);
        }else if (lengthplayTitleInfo > 10 && lengthplayTitleInfo <= 12 ){
            mController.setTitle("影片：" + playTitleInfo);
        }else{
            mController.setTitle(playTitleInfo);
        }
        stopParse();
        initParseLoadFound();
        resetDanmuState(); //xuameng 弹幕
//xuameng某些设备有问题        mController.stopOther();
        if(mVideoView!= null) mVideoView.release();
        subtitleCacheKey = mVodInfo.sourceKey + "-" + mVodInfo.id + "-" + mVodInfo.playFlag + "-" + mVodInfo.playIndex+ "-" + vs.name + "-subt";
        progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex + vs.name;       //xuameng
        //重新播放清除现有进度
        if (reset) {
            CacheManager.delete(MD5.string2MD5(progressKey), 0);
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), 0);
        }

        if(Jianpian.isJpUrl(vs.url)){//荐片地址特殊判断
            String jp_url= vs.url;
            mController.showParse(false);
            if(vs.url.startsWith("tvbox-xg:")){
                playUrl(Jianpian.JPUrlDec(jp_url.substring(9)), null);
                isJianpian = true;
            }else {
                playUrl(Jianpian.JPUrlDec(jp_url), null);
                isJianpian = true;
            }
            return;
        }
        if (Thunder.play(vs.url, new Thunder.ThunderCallback() {
            @Override
            public void status(int code, String info) {
                if (code < 0) {
                    setTip(info, false, true);
                } else {
                    setTip(info, true, false);
                }
            }

            @Override
            public void list(Map<Integer, String> urlMap) {
            }

            @Override
            public void play(String url) {
                playUrl(url, null);
            }
        })) {
            mController.showParse(false);
            return;
        }
        ClearOtherCache();
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url, subtitleCacheKey);
    }

    private String playSubtitle;
    private String subtitleCacheKey;
    private String progressKey;
    private String parseFlag;
    private String webUrl;
    private String webUserAgent;
    private HashMap<String, String > webHeaderMap;

    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        parseFlag = flag;
        webUrl = url;
        ParseBean parseBean = null;
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
                mController.showParse(false);
                App.showToastShort(mContext, "解析站点未配置，直接嗅探播放！");
            }else{
                mController.showParse(useParse);
            }
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }
        doParse(parseBean);
    }

    JSONObject jsonParse(String input, String json) throws JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        String url;
        if (jsonPlayData.has("data")) {
            url = jsonPlayData.getJSONObject("data").getString("url");
        } else {
            url = jsonPlayData.getString("url");
        }
        if (url.startsWith("//")) {
            url = "http:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        JSONObject headers = new JSONObject();
        String ua = jsonPlayData.optString("user-agent", "");
        if (ua.trim().length() > 0) {
            headers.put("User-Agent", " " + ua);
        }
        String referer = jsonPlayData.optString("referer", "");
        if (referer.trim().length() > 0) {
            headers.put("Referer", " " + referer);
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        return taskResult;
    }

    void stopParse() {
        mHandler.removeMessages(100);
        stopLoadWebView(false);
        OkGo.getInstance().cancelTag("json_jx");
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdown();
                parseThreadPool = null;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    ExecutorService parseThreadPool;

    private void doParse(ParseBean pb) {
        stopParse();
        initParseLoadFound();
        mVideoView.release();       //XUAMENG修复嗅探换源闪退
        if (pb.getType() == 4) {
            parseMix(pb,true);
        }
        else if (pb.getType() == 0) {
            setTip("正在嗅探播放地址", true, false);
            mHandler.removeMessages(100);
            mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
            if(pb.getExt()!= null){
                // 解析ext
                try {
                    HashMap<String, String> reqHeaders = new HashMap<>();
                    JSONObject jsonObject = new JSONObject(pb.getExt());
                    if (jsonObject.has("header")) {
                        JSONObject headerJson = jsonObject.optJSONObject("header");
                        Iterator<String> keys = headerJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (key.equalsIgnoreCase("user-agent")) {
                                webUserAgent = headerJson.getString(key).trim();
                            }else {
                                reqHeaders.put(key, headerJson.optString(key, ""));
                            }
                        }
                        if(reqHeaders.size()>0)webHeaderMap = reqHeaders;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            loadWebView(pb.getUrl() + webUrl);
        } else if (pb.getType() == 1) { // json 解析
            setTip("正在解析播放地址", true, false);
            // 解析ext
            HttpHeaders reqHeaders = new HttpHeaders();
            try {
                JSONObject jsonObject = new JSONObject(pb.getExt());
                if (jsonObject.has("header")) {
                    JSONObject headerJson = jsonObject.optJSONObject("header");
                    Iterator<String> keys = headerJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        reqHeaders.put(key, headerJson.optString(key, ""));
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            OkGo.<String>get(pb.getUrl() + mController.encodeUrl(webUrl))  //xuameng新增
                    .tag("json_jx")
                    .headers(reqHeaders)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                return response.body().string();
                            } else {
                                throw new IllegalStateException("网络请求错误");
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            String json = response.body();
                            try {
                                JSONObject rs = jsonParse(webUrl, json);
                                HashMap<String, String> headers = null;
                                if (rs.has("header")) {
                                    try {
                                        JSONObject hds = rs.getJSONObject("header");
                                        Iterator<String> keys = hds.keys();
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (headers == null) {
                                                headers = new HashMap<>();
                                            }
                                            headers.put(key, hds.getString(key));
                                        }
                                    } catch (Throwable th) {

                                    }
                                }
                                playUrl(rs.getString("url"), headers);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                errorWithRetry("解析错误", false);
//                                setTip("解析错误", false, true);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            errorWithRetry("解析错误", false);
//                            setTip("解析错误", false, true);
                        }
                    });
        } else if (pb.getType() == 2) { // json 扩展
            setTip("正在解析播放地址", true, false);
            parseThreadPool = Executors.newSingleThreadExecutor();
            LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                if (p.getType() == 1) {
                    jxs.put(p.getName(), p.mixUrl());
                }
            }
            parseThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    JSONObject rs = ApiConfig.get().jsonExt(pb.getUrl(), jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry("解析错误", false);//没有url重试也没有重新获取
                        setTip("解析错误", false, true);
                    } else {
                        HashMap<String, String> headers = null;
                        if (rs.has("header")) {
                            try {
                                JSONObject hds = rs.getJSONObject("header");
                                Iterator<String> keys = hds.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    if (headers == null) {
                                        headers = new HashMap<>();
                                    }
                                    headers.put(key, hds.getString(key));
                                }
                            } catch (Throwable th) {

                            }
                        }
                        if (rs.has("jxFrom")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    App.showToastShort(mContext, "解析来自:" + rs.optString("jxFrom"));
                                }
                            });
                        }
                        boolean parseWV = rs.optInt("parse", 0) == 1;
                        if (parseWV) {
                            String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            loadUrl(wvUrl);
                        } else {
                            playUrl(rs.optString("url", ""), headers);
                        }
                    }
                }
            });
        } else if (pb.getType() == 3) { // json 聚合
            parseMix(pb,false);
        }
    }
      private void parseMix(ParseBean pb,boolean isSuper){
        setTip("正在解析播放地址", true, false);
        parseThreadPool = Executors.newSingleThreadExecutor();
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        String extendName = "";
        for (ParseBean p : ApiConfig.get().getParseBeanList()) {
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("url", p.getUrl());
            if (p.getUrl().equals(pb.getUrl())) {
                extendName = p.getName();
            }
            data.put("type", p.getType() + "");
            data.put("ext", p.getExt());
            jxs.put(p.getName(), data);
        }
        String finalExtendName = extendName;
        parseThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                 if(isSuper){
                    JSONObject rs = SuperParse.parse(jxs,parseFlag+"123",webUrl);
                    if (!rs.has("url") || rs.optString("url").isEmpty()) {
                        setTip("解析错误", false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            setTip("聚汇超级解析中", true, false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    mHandler.removeMessages(100);
                                    mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                            parseThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    JSONObject res = SuperParse.doJsonJx(webUrl);
                                    rsJsonJx(res, true);
                                }
                            });
                        } else {
                            rsJsonJx(rs,false);
                        }
                    }
                }else {
                    JSONObject rs = ApiConfig.get().jsonExtMix(parseFlag + "111", pb.getUrl(), finalExtendName, jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
                        setTip("解析错误", false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    setTip("正在嗅探播放地址", true, false);
                                    mHandler.removeMessages(100);
                                    mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                        } else {
                           rsJsonJx(rs,false);
                        }
                    }
                }
            }
        });
    }
    private void rsJsonJx(JSONObject rs,boolean isSuper)
    {
        if(isSuper){
            if(rs==null || !rs.has("url"))return;
            stopLoadWebView(false);
        }
        HashMap<String, String> headers = null;
        if (rs.has("header")) {
            try {
                JSONObject hds = rs.getJSONObject("header");
                Iterator<String> keys = hds.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (headers == null) {
                        headers = new HashMap<>();
                    }
                    headers.put(key, hds.getString(key));
                }
            } catch (Throwable th) {

            }
        }
        if (rs.has("jxFrom")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    App.showToastShort(mContext, "解析来自:" + rs.optString("jxFrom"));
                }
            });
        }
        playUrl(rs.optString("url", ""), headers);
    }

    // webview
    private XWalkView mXwalkWebView;
    private XWalkWebClient mX5WebClient;
    private WebView mSysWebView;
    private SysWebClient mSysWebClient;
    private Map<String, Boolean> loadedUrls = new HashMap<>();
    private LinkedList<String> loadFoundVideoUrls = new LinkedList<>();
    private HashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new HashMap<>();
    private AtomicInteger loadFoundCount = new AtomicInteger(0);

    void loadWebView(String url) {
        if (mSysWebView == null && mXwalkWebView == null) {
            boolean useSystemWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
            if (!useSystemWebView) {
                XWalkUtils.tryUseXWalk(mContext, new XWalkUtils.XWalkState() {
                    @Override
                    public void success() {
                        initWebView(!sourceBean.getClickSelector().isEmpty());
                        loadUrl(url);
                    }

                    @Override
                    public void fail() {
                        App.showToastShort(mContext, "XWalkView不兼容，已替换为系统自带WebView");
                        initWebView(true);
                        loadUrl(url);
                    }

                    @Override
                    public void ignore() {
                        App.showToastShort(mContext, "XWalkView运行组件未下载，已替换为系统自带WebView");
                        initWebView(true);
                        loadUrl(url);
                    }
                });
            } else {
                initWebView(true);
                loadUrl(url);
            }
        } else {
            loadUrl(url);
        }
    }

    void initWebView(boolean useSystemWebView) {
        if (useSystemWebView) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
        } else {
            mXwalkWebView = new MyXWalkView(mContext);
            configWebViewX5(mXwalkWebView);
        }
    }

    void loadUrl(String url) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    if(webUserAgent != null) {
                        mXwalkWebView.getSettings().setUserAgentString(webUserAgent);
                    }
             //       mXwalkWebView.clearCache(true);
                    if(webHeaderMap != null){
                        mXwalkWebView.loadUrl(url,webHeaderMap);
                    }else {
                        mXwalkWebView.loadUrl(url);
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    if(webUserAgent != null) {
                        mSysWebView.getSettings().setUserAgentString(webUserAgent);
                    }
        //            mSysWebView.clearCache(true);
                    if(webHeaderMap != null){
                        mSysWebView.loadUrl(url,webHeaderMap);
                    }else {
                        mSysWebView.loadUrl(url);
                    }
                }
            }
        });
    }

    void stopLoadWebView(boolean destroy) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    mXwalkWebView.loadUrl("about:blank");
                    if (destroy) {
                        mXwalkWebView.clearCache(true);
                        mXwalkWebView.removeAllViews();
                        mXwalkWebView.onDestroy();
                        mXwalkWebView = null;
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    mSysWebView.loadUrl("about:blank");
                    if (destroy) {
                        mSysWebView.clearCache(true);
                        mSysWebView.removeAllViews();
                        mSysWebView.destroy();
                        mSysWebView = null;
                    }
                }
            }
        });
    }

    boolean checkVideoFormat(String url) {
        if (url.contains("url=http") || url.contains(".html")) {
            return false;
        }
        if (sourceBean.getType() == 3) {
            Spider sp = ApiConfig.get().getCSP(sourceBean);
            if (sp != null && sp.manualVideoCheck())
                return sp.isVideoFormat(url);
        }
        return VideoParseRuler.checkIsVideoForParse(webUrl, url);
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class MyXWalkView extends XWalkView {
        public MyXWalkView(Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* 添加webView配置 */
        //设置编码
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
        // settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted( view,  url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view,url);
            String click=sourceBean.getClickSelector();
            LOG.i("echo-onPageFinished url:" + url);
            if(!url.equals("about:blank")){
               mController.evaluateScript(sourceBean,url,view,null);
            }
        }

        WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i( "shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }

            if (!ad) {
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, headers);
                    LOG.i("echo-loadFoundVideoUrl:" + url );
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false);
                        SuperParse.stopJsonJx();
                        mHandler.removeMessages(100);
                        url = loadFoundVideoUrls.poll();
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if(!TextUtils.isEmpty(cookie))headers.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, headers);
                    }
                }
            }

            return ad || loadFoundCount.get() > 0 ?
                    AdBlocker.createEmptyResource() :
                    null;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return null;
        }

        @Nullable
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("echo-shouldInterceptRequest url:" + url);
            HashMap<String, String> webHeaders = new HashMap<>();
            Map<String, String> hds = request.getRequestHeaders();
            if (hds != null && hds.keySet().size() > 0) {
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k," " + hds.get(k));
                    }
                }
            }
            return checkIsVideo(url, webHeaders);
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewX5(XWalkView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final XWalkSettings settings = webView.getSettings();
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // settings.setUserAgentString(ANDROID_UA);

        webView.setBackgroundColor(Color.BLACK);
        webView.setUIClient(new XWalkUIClient(webView) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                return false;
            }

            @Override
            public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
                return true;
            }
        });
        mX5WebClient = new XWalkWebClient(webView);
        webView.setResourceClient(mX5WebClient);
    }

    private class XWalkWebClient extends XWalkResourceClient {
        public XWalkWebClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {
            super.onLoadFinished(view, url);
            LOG.i("echo-onPageFinished url:" + url);
            if(!url.equals("about:blank")){
                mController.evaluateScript(sourceBean,url,null,view);
            }
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("echo-shouldInterceptLoadRequest url:" + url);
            // suppress favicon requests as we don't display them anywhere
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return createXWalkWebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i( "shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }
            if (!ad ) {
                if (checkVideoFormat(url)) {
                    HashMap<String, String> webHeaders = new HashMap<>();
                    Map<String, String> hds = request.getRequestHeaders();
                    if (hds != null && hds.keySet().size() > 0) {
                        for (String k : hds.keySet()) {
                            if (k.equalsIgnoreCase("user-agent")
                                    || k.equalsIgnoreCase("referer")
                                    || k.equalsIgnoreCase("origin")) {
                                webHeaders.put(k," " + hds.get(k));
                            }
                        }
                    }
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, webHeaders);
                    LOG.i("echo-loadFoundVideoUrl:" + url );
                    if (loadFoundCount.incrementAndGet() == 1) {
                        stopLoadWebView(false);
                        SuperParse.stopJsonJx();
                        mHandler.removeMessages(100);
                        url = loadFoundVideoUrls.poll();
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if(!TextUtils.isEmpty(cookie))webHeaders.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, webHeaders);
                    }
                }
            }
            return ad || loadFoundCount.get() > 0 ?
                    createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())) :
                    null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
            return false;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

    public void ClearOtherCache() {    //xuameng清空荐片迅雷缓存
        mController.stopOther();
        String CachePath = FileUtils.getCachePath();     //xuameng 清空缓存
        File CachePathDir = new File(CachePath); 
        new Thread(() -> {
        try {
            if(CachePathDir.exists())FileUtils.cleanDirectory(CachePathDir);
        } catch (Exception e) {
              e.printStackTrace();
        }
        }).start();
    }

	// xuameng 字幕颜色类中添加颜色数组
    private int[] subtitleColors = {
        0xFFFFFFFF, // 白色
        0xFF02F8E1, // 青色
        0xFFFFD700, // 黄色
        0xFFFF69B4, // 亮粉色（调亮点）
        0xFF00FF7F, // 亮绿色
        0xFF4169E1, // 亮蓝色
        0xFFFF4500, // 橙红色（调亮的橙红色）
        0xFFDA70D6, // 亮紫色
        0xFF00CED1, // 亮青色
        0xFFEE82EE  // 亮紫色
    };

    private void loadLrcFromUrl(String lrcUrl) {        //xuameng LRC歌词从URL加载
        if (lrcUrl.contains(":9976/")) {
            // 将端口9976替换为9978
            lrcUrl = lrcUrl.replace(":9976/", ":9978/");
        } else if (lrcUrl.contains(":0/")) {
            // 将端口0替换为9978
            lrcUrl = lrcUrl.replace(":0/", ":9978/");
        }
        OkGo.<String>get(lrcUrl)
            .tag("lrc_load")
            .execute(new AbsCallback<String>() {
                @Override
                public void onSuccess(Response<String> response) {
                    String lrcText = response.body();
                    if (!TextUtils.isEmpty(lrcText) && lrcText.length() > 10) {
                        // 切换到主线程更新 UI
                        PlayActivity.this.runOnUiThread(() -> {
                            mController.setLrcContent(lrcText);
                            mController.mLrcView.setVisibility(View.VISIBLE);
                        });
                    } else {
                        // 歌词内容为空，隐藏歌词视图
                            PlayActivity.this.runOnUiThread(() -> {
                            mController.mLrcView.setVisibility(View.GONE);
                        });
                    }
                }

                @Override
                public void onError(Response<String> response) {
                    super.onError(response);
                    // 加载失败，隐藏歌词视图
                    PlayActivity.this.runOnUiThread(() -> {
                        mController.mLrcView.setVisibility(View.GONE);
                    });
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
    }

}
