package com.github.tvbox.osc.player.controller;
import android.animation.Animator; //xuameng动画
import android.animation.AnimatorListenerAdapter; //xuameng动画
import android.animation.ObjectAnimator; //xuameng动画
import android.view.animation.DecelerateInterpolator;  //xuameng动画
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Color; //xuameng获取颜色值
import android.widget.FrameLayout; //xuameng倍速播放
import com.github.tvbox.osc.server.RemoteServer; //xuameng新增广告过滤
import com.github.tvbox.osc.util.M3u8; //xuameng新增广告过滤
import com.lzy.okgo.OkGo; //xuameng新增广告过滤
import com.lzy.okgo.callback.AbsCallback; //xuameng新增广告过滤
import com.lzy.okgo.model.HttpHeaders; //xuameng新增广告过滤
import com.lzy.okgo.model.Response; //xuameng新增广告过滤
import java.net.MalformedURLException; //xuameng新增广告过滤
import com.github.tvbox.osc.base.App; //xuameng停止磁力下载
import com.github.tvbox.osc.util.thunder.Jianpian; //xuameng停止磁力下载
import com.github.tvbox.osc.util.thunder.Thunder; //xuameng停止磁力下载
import java.net.URL; //xuameng新增广告过滤
import java.util.HashMap; //xuameng新增广告过滤
import java.util.Map; //xuameng新增广告过滤
import org.json.JSONArray; //xuameng  b站
import android.widget.ProgressBar; //xuameng loading
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FastClickCheckUtilxu; //xuameng防连击1秒
import com.github.tvbox.osc.server.ControlManager;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Calendar; //xuameng 获取时间
import java.util.Locale; //xuameng 获取时间
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;
import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;
import static xyz.doikki.videoplayer.util.PlayerUtils.safeTimeMs;
import com.squareup.picasso.Picasso; //xuameng播放音频切换图片
import com.squareup.picasso.MemoryPolicy; //xuameng播放音频切换图片
import com.squareup.picasso.NetworkPolicy; //xuameng播放音频切换图片
import android.graphics.Bitmap; //xuameng播放音频切换图片
import com.github.tvbox.osc.api.ApiConfig; //xuameng播放音频切换图片
import com.github.tvbox.osc.ui.tv.widget.MusicVisualizerView;  //xuameng音乐播放动画
import android.media.audiofx.Visualizer;  //xuameng音乐播放动画
import android.util.Log; //xuameng音乐播放动画
import android.os.Looper; //xuameng音乐播放动画
import android.media.AudioManager;  //xuameng音乐播放动画

import com.github.tvbox.osc.subtitle.LrcView;  //xuameng LRC歌词字幕
import android.text.TextUtils;  //xuameng LRC歌词字幕
import com.github.tvbox.osc.picasso.RoundTransformation; //xuameng 新增给vod显示旋转图片用
import me.jessyan.autosize.utils.AutoSizeUtils; //xuameng 新增给vod显示旋转图片用
import com.github.tvbox.osc.util.MD5; //xuameng 新增给vod显示旋转图片用
import android.widget.FrameLayout.LayoutParams; //xuameng 新增给vod显示旋转图片用
import android.view.Gravity; //xuameng 新增给vod显示旋转图片用
import android.util.TypedValue; //xuameng 新增给vod显示旋转图片用
import master.flame.danmaku.ui.widget.DanmakuView;
import org.greenrobot.eventbus.EventBus;

import com.google.android.exoplayer2.ui.SubtitleView;   // 用于显示ExoPlayer内置字幕

import android.os.Build;
import android.webkit.WebView;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.VideoParseRuler;
import org.xwalk.core.XWalkView;
public class VodController extends BaseController {
    public VodController(@NonNull @NotNull Context context) {
        super(context);
        mHandlerCallback = new HandlerCallback() {
            @Override
            public void callback(Message msg) {
                switch(msg.what) {
                    case 1000: { // seek 刷新
                        if(iv_circle_bg.getVisibility() == View.VISIBLE && mLrcView.getVisibility() == View.GONE) { //xuameng音乐播放时图标  当字幕显示时不隐藏旋转图标
                            iv_circle_bg.setVisibility(GONE);
                        }
                        if(mPlayLoadNetSpeed.getVisibility() == View.VISIBLE) { //xuameng网速
                            mPlayLoadNetSpeed.setVisibility(View.GONE);
                        }
                        if(XuLoading.getVisibility() == View.VISIBLE) { //xuameng loading
                            XuLoading.setVisibility(GONE);
                        }
                        mProgressRoot.setVisibility(VISIBLE);
                        break;
                    }
                    case 1001: { // seek 关闭
                        if(mProgressRoot.getVisibility() == View.VISIBLE) { //xuameng进程图标
                            mProgressRoot.setVisibility(GONE);
                        }
                        if (isBufferIng){  //xuameng 修复缓存图标不显示
                            XuLoading.setVisibility(View.VISIBLE);
                            mPlayLoadNetSpeed.setVisibility(View.VISIBLE);
                        }
                        break;
                    }
                    case 1005: { // 隐藏底部菜单Xu
                        // 底部视图滑出动画
                        mBottomRoot.animate()
                                .translationY(230)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                        super.onAnimationStart(animation);
                                        isAnimation = true;
                                    }
                
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        mBottomRoot.setVisibility(GONE); // xuameng动画结束后隐藏下菜单
                                        mTopRoot1.setVisibility(GONE); // xuameng动画结束后隐藏上菜单
                                        mTopRoot2.setVisibility(GONE); // xuameng动画结束后隐藏上菜单
                                        isAnimation = false;
                                    }
                                });

                        // xuameng顶部视图1滑出动画
                        mTopRoot1.animate()
                                .translationY(-120)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);

                        // xuameng顶部视图2滑出动画
                        mTopRoot2.animate()
                                .translationY(-120)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);

                        // xuameng返回按钮隐藏
                        backBtn.setVisibility(GONE);
                        break;
                    }
                    case 1002: { // 显示底部菜单
                        // xuameng底部视图动画
                        mBottomRoot.setVisibility(VISIBLE);
                        mBottomRoot.setAlpha(0.0f);
                        mBottomRoot.setTranslationY(230);
                        mBottomRoot.animate()
                                .translationY(0)
                                .alpha(1.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                        super.onAnimationStart(animation);
                                        isDisplay = true;
                                    }
                
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        isDisplay = false;
                                    }
                                });
                        // xuameng顶部视图1动画
                        mTopRoot1.setVisibility(VISIBLE);
                        mTopRoot1.setAlpha(0.0f);
                        mTopRoot1.setTranslationY(-120);
                        mTopRoot1.animate()
                                .translationY(0)
                                .alpha(1.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);
    
                        // xuameng顶部视图2动画
                        mTopRoot2.setVisibility(VISIBLE);
                        mTopRoot2.setAlpha(0.0f);
                        mTopRoot2.setTranslationY(-120);
                        mTopRoot2.animate()
                                .translationY(0)
                                .alpha(1.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);
    
                        // xuameng其他设置
                        mxuPlay.requestFocus();   //xuameng底部菜单默认焦点为播放
                        backBtn.setVisibility(ScreenUtils.isTv(getContext()) ? GONE : VISIBLE);   //xuameng返回按钮
                        showLockView();    //xuameng屏幕锁
                        mPauseContainer.setVisibility(GONE);  // xuameng播放标题、暂停时间
    
                        if(mLandscapePortraitBtn.getVisibility() == View.VISIBLE) {    //xuameng 横竖屏显示BUG
                            setLandscapePortraitXu();
                        }
                        break;
                    }
                    case 1003: { // xuameng隐藏底部菜单
                        // xuameng底部视图滑出动画
                        mBottomRoot.animate()
                                .translationY(230)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                        super.onAnimationStart(animation);
                                        isAnimation = true;
                                    }
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        mBottomRoot.setVisibility(GONE);   //动画结束后隐藏下菜单
                                        mTopRoot1.setVisibility(GONE);    //动画结束后隐藏上菜单
                                        mTopRoot2.setVisibility(GONE);   //动画结束后隐藏上菜单
                                        isAnimation = false;
                                    }
                                });

                        // xuameng顶部视图1滑出动画
                        mTopRoot1.animate()
                                .translationY(-120)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);

                        // xuameng顶部视图2滑出动画
                        mTopRoot2.animate()
                                .translationY(-120)
                                .alpha(0.0f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .setListener(null);

                        // xuameng返回按钮隐藏
                        backBtn.setVisibility(GONE);

                        // xuameng播放控制视图处理
                        if (mControlWrapper.isPlaying()) {
                            // xuameng播放状态处理
                        } else {
                            // xuameng显示播放标题、暂停时间动画
                            mPauseContainer.setVisibility(VISIBLE);
                            mPauseContainer.setTranslationY(-120);
                            mPauseContainer.animate()
                                    .translationY(0)
                                    .alpha(1.0f)
                                    .setDuration(300)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .setListener(null);
                        }
                        break;
                    }
                    case 1004: { // 设置速度
                        if(isInPlaybackState()) {
                            try {
                                float speed = (float) mPlayerConfig.getDouble("sp");
                                mControlWrapper.setSpeed(speed);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else mHandler.sendEmptyMessageDelayed(1004, 100);
                        break;
                    }
                }
            }
        };
    }
    SeekBar mSeekBar;
    TextView mCurrentTime;
    TextView mTotalTime;
    boolean mIsDragging;
    LinearLayout mProgressRoot;
    TextView mProgressText;
    ImageView mProgressIcon;
    ImageView mLockView;
    LinearLayout mBottomRoot;
    LinearLayout mTopRoot1;
    LinearLayout mTopRoot2;
    LinearLayout mParseRoot;
    LinearLayout mTvPausexu; //xuameng暂停动画
    TvRecyclerView mGridView;
    TextView mPlayTitle;
    TextView mPlayTitle1;
    TextView mPlayLoadNetSpeedRightTop;
    TextView mPlayTimeEnd; //xuameng影片结束时间
    TextView mNextBtn;
    TextView mPreBtn;
    TextView mPlayerScaleBtn;
    public TextView mPlayerSpeedBtn;
    TextView mPlayerBtn;
    TextView mPlayerIJKBtn;
    TextView mPlayerEXOBtn;  //exo解码
    TextView mPlayerRetry;
    TextView mPlayrefresh;
    TextView mxuPlay; //xuameng 底部播放ID
    TextView mPlayrender;  //xuameng渲染方式
    TextView mPlayanimation; //xuameng音柱动画
    private ImageView iv_circle_bg; //xuameng音乐播放时图标
    private FrameLayout play_speed_3; //xuameng倍速播放
    private FrameLayout mPauseContainer; // xuameng播放标题、暂停时间
    private TextView tv_slide_progress_text;  //xuameng 旧的亮度调节框已作废
    ImageView MxuamengMusic; //xuameng播放音乐背景
    private ProgressBar XuLoading; //xuameng  loading
    public TextView mPlayerTimeStartEndText;
    public TextView mPlayerTimeStartBtn;
    public TextView mPlayerTimeSkipBtn;
    public TextView mPlayerTimeResetBtn;
    TextView mPlayPauseTime;
    TextView mPlayPauseTimexu; //xuameng系统时间
    TextView mPlayLoadNetSpeed;
    TextView mVideoSize;
    public SimpleSubtitleView mSubtitleView;
    TextView mZimuBtn;
    TextView mAudioTrackBtn;
    TextView mDanmuSettingBtn;
    public TextView mLandscapePortraitBtn;
    private View backBtn; //返回键
    private boolean isClickBackBtn;
    private double DOUBLE_CLICK_TIME = 0L; //xuameng返回键防连击1.5秒（为动画）
    private double DOUBLE_CLICK_TIME_2 = 0L; //xuameng防连击1秒（为动画）
    LockRunnable lockRunnable = new LockRunnable();
    private boolean isLock = false;
    private boolean isSEEKBAR = false; //xuameng进入SEEKBAR
    private boolean isPlaying = false; //xuameng判断暂停动画
    private boolean isAnimation = false; //xuameng判断隐藏菜单动画
    private boolean isDisplay = false; //xuameng判断显示菜单动画
    private boolean isVideoPlay = false; //xuameng判断视频开始播放
    private boolean isLongClick = false; //xuameng判断长按
    private boolean mSeekBarhasFocus = false; //xuameng seekbar是否拥有焦点
    private boolean isBufferIng = false; //xuameng 判断是否进在缓冲视频
    private Visualizer mVisualizer;  //xuameng音乐播放动画
    private MusicVisualizerView customVisualizer; //xuameng播放音乐柱状图
    private int audioSessionId = -1; // 使用-1表示未初始化状态 //xuameng音乐播放动画
    private boolean musicAnimation = Hawk.get(HawkConfig.VOD_MUSIC_ANIMATION, false);     //xuameng 音柱动画 加载设置
    public SubtitleView mExoSubtitleView;   // 用于显示ExoPlayer内置字幕
    private static final String TAG = "VodController";  //xuameng音乐播放动画
    public LrcView mLrcView;   //xuameng LRC歌词字幕
    private String mLrcContent = "";  //xuameng LRC歌词字幕
	private String videoPicUrl; //xuameng 新增给vod显示旋转图片用
    private boolean hasDanmu = false;
    private DanmakuView mDanmuView;

    Handler myHandle;
    Runnable myRunnable;
    int myHandleSeconds = 50000; //闲置多少毫秒秒关闭底栏  默认100秒
    int videoPlayState = 0;

    private Runnable myRunnable2 = new Runnable() {
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            mPlayPauseTime.setText(timeFormat.format(date));
            String speed = PlayerHelper.getDisplaySpeed(mControlWrapper.getTcpSpeed());
            mPlayLoadNetSpeedRightTop.setText("[ " + speed + " ]");
            mPlayLoadNetSpeed.setText(speed);
            int duration = safeTimeMs(mControlWrapper.getDuration());
            if(isInPlaybackState() && duration >= 1000 && duration <= 180000000) {
                int position = safeTimeMs(mControlWrapper.getCurrentPosition());
                if(position < 0) position = 0; //xuameng系统播放器有时会有负进度的BUG
                int TimeRemaining = safeTimeMs(mControlWrapper.getDuration()) - position;
                Calendar dateXu = Calendar.getInstance();
                long t = dateXu.getTimeInMillis();
                Date afterAdd = new Date(t + TimeRemaining);
                SimpleDateFormat timeEnd = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
                mPlayTimeEnd.setVisibility(VISIBLE);
                mPlayTimeEnd.setText("影片剩余时间" + " " + PlayerUtils.stringForTime((int) TimeRemaining) + " ｜ " + "影片结束时间" + " " + timeEnd.format(afterAdd));
            } else {
                mPlayTimeEnd.setVisibility(GONE);
            }
            mHandler.postDelayed(this, 1000);
        }
    };
    private Runnable myRunnableXu = new Runnable() {
        @Override
        public void run() {
            String width = Integer.toString(mControlWrapper.getVideoSize()[0]);
            String height = Integer.toString(mControlWrapper.getVideoSize()[1]);
            if(isInPlaybackState()) { //xuameng 重新选择解析视频大小不刷新
                mVideoSize.setText("[ " + width + " X " + height + " ]");
            }else{
                mHandler.postDelayed(this, 100);
                return;
            }
            if(mControlWrapper.isPlaying()) { //xuameng音乐播放时图标判断
                if(!mIsDragging) {
                    mControlWrapper.startProgress(); //xuameng启动进程
                    mControlWrapper.startFadeOut();
                }
                mxuPlay.setText("暂停");
                mHidePauseIng(); //xuameng 隐藏暂停图标
                try {
                    musicAnimation = mPlayerConfig.getBoolean("music");  //xuameng音乐播放动画获取设置
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (musicAnimation){
                    if(customVisualizer.getVisibility() == View.GONE) { //xuameng播放音乐柱状图
                        customVisualizer.setVisibility(VISIBLE);
                    }
                }else{
                    if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                        customVisualizer.setVisibility(GONE);
                    }
                }
                if(width.length() > 1 && height.length() > 1) {
                    if(iv_circle_bg.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                        iv_circle_bg.setVisibility(GONE);
                    }
                    if(MxuamengMusic.getVisibility() == View.VISIBLE) { //xuameng播放音乐背景
                        MxuamengMusic.setVisibility(GONE);
                    }
                } else {
                    if(MxuamengMusic.getVisibility() == View.GONE) { //xuameng播放音乐背景
                        MxuamengMusic.setVisibility(VISIBLE);
                    }
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) iv_circle_bg.getLayoutParams(); //xuameng 新增给vod显示旋转图片用
                    if(mLrcView.getVisibility() == View.VISIBLE) {   //xuameng LRC歌词字幕
                        int position = safeTimeMs(mControlWrapper.getCurrentPosition());
                        if (mLrcView != null) {
                            mLrcView.updateTime(position);  //xuameng 刷新LRC歌词字幕
                        }
                        // xuameng如果 mLrcView 显示，则将 iv_circle_bg 置于屏幕左上角
                        params.gravity = Gravity.TOP | Gravity.LEFT;
                        // xuameng设置左上角的外边距为 30dp
                        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());
                        params.topMargin = margin;
                        params.leftMargin = margin;

                        loadVideoPic();    //xuameng 加载网络PIC图片
                        iv_circle_bg.setVisibility(VISIBLE);
                    }else {
                        // xuameng如果 mLrcView 不显示，则将 iv_circle_bg 置于屏幕中心
                        params.gravity = Gravity.CENTER;
                        params.topMargin = 0; // 重置上边距
                        params.leftMargin = 0; // 重置左边距

                        // 字幕未显示时，检查是否需要隐藏图标
                        boolean shouldHide = mProgressRoot.getVisibility() == View.VISIBLE || 
                            mPlayLoadNetSpeed.getVisibility() == View.VISIBLE || 
                            XuLoading.getVisibility() == View.VISIBLE || 
                            play_speed_3.getVisibility() == View.VISIBLE;
    
                        if(shouldHide) {
                            if(iv_circle_bg.getVisibility() == View.VISIBLE) {
                                iv_circle_bg.setVisibility(GONE);
                            }
                        } else {
                            loadVideoPic();  //xuameng 加载网络PIC图片
                            iv_circle_bg.setVisibility(VISIBLE);
                        }
                    }
                    iv_circle_bg.setLayoutParams(params);
                }
            } else {
                iv_circle_bg.setVisibility(GONE);
            } //xuameng音乐播放时图标判断完
            if(mLrcView.getVisibility() == View.VISIBLE) {  
                mHandler.postDelayed(this, 10);
            } else {
                mHandler.postDelayed(this, 100);
			}
        }
    };
    private Runnable myRunnableMusic = new Runnable() { //xuameng播放音频切换图片
        @Override
        public void run() {
            if(MxuamengMusic.getVisibility() == View.VISIBLE) {
                if(!ApiConfig.get().musicwallpaper.isEmpty()) {
                    String Url = ApiConfig.get().musicwallpaper;
                    Picasso.get().load(Url)
                        //				.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
                        .noPlaceholder() //不使用站位图，效果不好
                        				.resize(1920,1080)
                        //				.centerCrop()
                        //				.error(R.drawable.xumusic)
                        .config(Bitmap.Config.RGB_565).memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE).networkPolicy(NetworkPolicy.NO_CACHE).into(MxuamengMusic); // xuameng内容空显示banner
                    mHandler.postDelayed(this, 15000);
                    return;
                } else if(!ApiConfig.get().wallpaper.isEmpty()) {
                    String Url = ApiConfig.get().wallpaper;
                    Picasso.get().load(Url)
                        //				.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
                        .noPlaceholder() //不使用站位图，效果不好
                        .resize(1920, 1080)
                        //				.centerCrop()
                        //				.error(R.drawable.xumusic)
                        .config(Bitmap.Config.RGB_565).memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE).networkPolicy(NetworkPolicy.NO_CACHE).into(MxuamengMusic); // xuameng内容空显示banner
                    mHandler.postDelayed(this, 15000);
                    return;
                }
                String Url = "https://api.miaomc.cn/image/get";
                Picasso.get().load(Url)
                    //				.placeholder(R.drawable.xumusic)   //xuameng默认的站位图
                    .noPlaceholder() //不使用站位图，效果不好
                    .resize(1920, 1080)
                    //				.centerCrop()
                    //				.error(R.drawable.xumusic)
                    .config(Bitmap.Config.RGB_565).memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE).networkPolicy(NetworkPolicy.NO_CACHE).into(MxuamengMusic); // xuameng内容空显示banner
            }
            mHandler.postDelayed(this, 15000);
        }
    };
    private Runnable xuRunnable = new Runnable() { //xuameng显示系统时间
        @Override
        public void run() {
            Date date1 = new Date();
            SimpleDateFormat timeFormat1 = new SimpleDateFormat("HH:mm:ss");
            mPlayPauseTimexu.setText(timeFormat1.format(date1));
            mHandler.postDelayed(this, 1000);
        }
    }; //xuameng显示系统时间
    private void showLockView() {
        mLockView.setVisibility(ScreenUtils.isTv(getContext()) ? GONE : VISIBLE);
        mHandler.removeCallbacks(lockRunnable);
        mHandler.postDelayed(lockRunnable, 3000);
    }
    @Override
    protected void initView() {
        super.initView();
        mCurrentTime = findViewById(R.id.curr_time);
        mTotalTime = findViewById(R.id.total_time);
        mPlayTitle = findViewById(R.id.tv_info_name);
        mPauseContainer = findViewById(R.id.tv_pause_container);  // xuameng播放标题、暂停时间
        mPlayTitle1 = findViewById(R.id.tv_info_name1);
        mPlayLoadNetSpeedRightTop = findViewById(R.id.tv_play_load_net_speed_right_top);
        mPlayTimeEnd = findViewById(R.id.play_time_end_xu); //xuameng影片结束时间
        mSeekBar = findViewById(R.id.seekBar);
        mProgressRoot = findViewById(R.id.tv_progress_container);
        mProgressIcon = findViewById(R.id.tv_progress_icon);
        mProgressText = findViewById(R.id.tv_progress_text);
        mBottomRoot = findViewById(R.id.bottom_container);
        mTopRoot1 = findViewById(R.id.tv_top_l_container);
        mTopRoot2 = findViewById(R.id.tv_top_r_container);
        mParseRoot = findViewById(R.id.parse_root);
        mGridView = findViewById(R.id.mGridView);
        mPlayerRetry = findViewById(R.id.play_retry);
        mPlayrefresh = findViewById(R.id.play_refresh);
        mNextBtn = findViewById(R.id.play_next);
        mPreBtn = findViewById(R.id.play_pre);
        mPlayerScaleBtn = findViewById(R.id.play_scale);
        mPlayerSpeedBtn = findViewById(R.id.play_speed);
        mPlayerBtn = findViewById(R.id.play_player);
        mPlayerIJKBtn = findViewById(R.id.play_ijk);
        mPlayerEXOBtn = findViewById(R.id.play_exo);  //exo解码
        mPlayerTimeStartEndText = findViewById(R.id.play_time_start_end_text);
        mPlayerTimeStartBtn = findViewById(R.id.play_time_start);
        mPlayerTimeSkipBtn = findViewById(R.id.play_time_end);
        mPlayerTimeResetBtn = findViewById(R.id.play_time_reset);
        mPlayPauseTime = findViewById(R.id.tv_sys_time);
        mPlayPauseTimexu = findViewById(R.id.tv_sys_time_xu); //XUAMENG的系统时间
        mTvPausexu = findViewById(R.id.tv_pause_xu); //XUAMENG暂停动画
        iv_circle_bg = (ImageView) findViewById(R.id.iv_circle_bg); //xuameng音乐播放时图标
        MxuamengMusic = (ImageView) findViewById(R.id.xuamengMusic); //xuameng播放音乐背景
        play_speed_3 = findViewById(R.id.play_speed_3_container); //xuameng倍速播放
        XuLoading = findViewWithTag("vod_control_loading"); //xuameng  loading 
        customVisualizer = findViewById(R.id.visualizer_view);  //xuameng播放音乐柱状图
        tv_slide_progress_text = findViewById(R.id.tv_slide_progress_text);   //xuameng 旧的亮度调节框已作废
        mPlayLoadNetSpeed = findViewById(R.id.tv_play_load_net_speed);
        mVideoSize = findViewById(R.id.tv_videosize);
        mSubtitleView = findViewById(R.id.subtitle_view);
        mZimuBtn = findViewById(R.id.zimu_select);
        mAudioTrackBtn = findViewById(R.id.audio_track_select);
        mDanmuSettingBtn = findViewById(R.id.danmu_setting);
        mLandscapePortraitBtn = findViewById(R.id.landscape_portrait);
        backBtn = findViewById(R.id.tv_back);
        mxuPlay = findViewById(R.id.mxuplay); //xuameng  低菜单播放
        mPlayrender = findViewById(R.id.play_render);   //xuameng渲染方式
        mPlayanimation = findViewById(R.id.play_animation);  //xuameng音柱动画
        mExoSubtitleView = findViewById(R.id.exo_subtitle_view); // 用于显示ExoPlayer内置字幕
        mLrcView = findViewById(R.id.lrc_view);  //xuameng LRC歌词字幕
        mDanmuView = findViewById(R.id.danmaku);

        //xuameng音乐播放时图标
        ObjectAnimator animator20 = ObjectAnimator.ofFloat(iv_circle_bg, "rotation", 360.0f);
        animator20.setDuration(10000);
        animator20.setRepeatCount(-1);
        animator20.start();
        backBtn.setOnClickListener(new OnClickListener() { //xuameng  屏幕上的返回键
            @Override
            public void onClick(View view) {
                if(getContext() instanceof Activity) {
                    if(isDisplay || isAnimation || isPlaying) {
                        return;
                    } else {
                        isClickBackBtn = true;
                        ((Activity) getContext()).onBackPressed();
                    }
                }
            }
        });
        mLockView = findViewById(R.id.tv_lock);
        mLockView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isDisplay || isAnimation || isPlaying) {
                    return;
                }
                isLock = !isLock;
                mLockView.setImageResource(isLock ? R.drawable.icon_lock : R.drawable.icon_unlock);
                if(isLock) {
                    if(mBottomRoot.getVisibility() == View.VISIBLE) {
                        Message obtain = Message.obtain();
                        obtain.what = 1003; //隐藏底部菜单
                        mHandler.sendMessage(obtain);
                    }
                }
                showLockView();
            }
        });
        View rootView = findViewById(R.id.rootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLock) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (mLockView.getVisibility() == View.VISIBLE) {
                            mLockView.setVisibility(GONE);  //xuameng 必须GONE如果写成INVISIBLE在surface下会没反应
                        } else {
                            showLockView();
                        }
                    }
                }
                return isLock;
            }
        });
        initSubtitleInfo();
        myHandle = new Handler();
        myRunnable = new Runnable() {
            @Override
            public void run() {
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottom();
                }
            }
        };
        mPlayPauseTime.post(new Runnable() {
            @Override
            public void run() {
                mHandler.post(myRunnable2);
                mHandler.post(myRunnableMusic); //xuameng播放音频切换图片
                mHandler.post(myRunnableXu); //xuameng播放音频切换图片
            }
        });
        mPlayPauseTimexu.post(new Runnable() { //xuameng显示系统时间
            @Override
            public void run() {
                mHandler.post(xuRunnable);
            }
        });
        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
        ParseAdapter parseAdapter = new ParseAdapter();
        parseAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                ParseBean parseBean = parseAdapter.getItem(position);
                // 当前默认解析需要刷新
                int currentDefault = parseAdapter.getData().indexOf(ApiConfig.get().getDefaultParse());
                parseAdapter.notifyItemChanged(currentDefault);
                ApiConfig.get().setDefaultParse(parseBean);
                parseAdapter.notifyItemChanged(position);
                listener.changeParse(parseBean);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottom();
                }
            }
        });
        mGridView.setAdapter(parseAdapter);
        parseAdapter.setNewData(ApiConfig.get().getParseBeanList());
        //     mParseRoot.setVisibility(VISIBLE);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(!fromUser) {
                    return;
                }
                long duration = safeTimeMs(mControlWrapper.getDuration());
                long newPosition = (duration * progress) / seekBar.getMax();
                if(mCurrentTime != null) mCurrentTime.setText(stringForTime(safeTimeMs(newPosition)));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                long duration = safeTimeMs(mControlWrapper.getDuration());
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo(newPosition);
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
            }
        });
        //xuameng监听底部进度条遥控器
        mSeekBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View arg0, int keycode, KeyEvent event) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    boolean isInPlayback = isInPlaybackState();
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if(isInPlayback) {
                            tvSlideStartXu(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                            return true;
                        } else {
                            mSeekBar.setEnabled(false);
                            return true;
                        }
                    }
                    if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                            return true;
                        }
                        DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                        if(isInPlayback) {
                            if(!isDisplay || !isAnimation) {
                                if(mControlWrapper.isPlaying()) {
                                    pauseIngXu();
                                    togglePlay();
                                    return true;
                                }
                                if(!mControlWrapper.isPlaying()) {
                                    playIngXu();
                                    togglePlay();
                                    return true;
                                }
                            }
                            return true;
                        }
                    }
                }
                if(event.getAction() == KeyEvent.ACTION_UP) {
                    int keyCode = event.getKeyCode();
                    int action = event.getAction();
                    boolean isInPlayback = isInPlaybackState();
                    if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if(isInPlayback) {
                            tvSlideStopXu(); //xuameng修复SEEKBAR快进重新播放问题
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        //xuameng监听底部进度条遥控器结束
        mSeekBar.setOnFocusChangeListener(new View.OnFocusChangeListener() { //XUAMENG SEEKBAR获得焦点
            @Override //xuameng进入SEEKBAR
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus) {
                    mSeekBarhasFocus = true; //xuameng进入SEEKBAR
                } else {
                    mSeekBarhasFocus = false; //xuameng进入SEEKBAR
                    isSEEKBAR = false; //xuameng SEEKBAR 失去焦点
                }
            }
        });
        mPlayerRetry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(v);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                listener.replay(true);
            }
        });
        mPlayrefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(v);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                listener.replay(false);
            }
        });
        mNextBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(view);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                listener.playNext(false);
            }
        });
        mxuPlay.setOnClickListener(new OnClickListener() { //xuameng 低菜单播放监听
            @Override //xuameng 低菜单播放监听
            public void onClick(View view) { //xuameng 低菜单播放监听
                boolean isInPlayback = isInPlaybackState();
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                if(isInPlayback) {
                    if(!isDisplay || !isAnimation) {
                        if(mControlWrapper.isPlaying()) {
                            pauseIngXu();
                            togglePlay();
                            return;
                        }
                        if(!mControlWrapper.isPlaying()) {
                            playIngXu();
                            togglePlay();
                            return;
                        }
                    } //xuameng 低菜单播放监听
                }
            }
        });
        mPlayrender.setOnClickListener(new OnClickListener() { //xuameng渲染选择
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                int pr = Hawk.get(HawkConfig.PLAY_RENDER, 0);      //xuameng pr 0 text渲染 1 sur渲染
                try {
                    pr = mPlayerConfig.getInt("pr");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(pr == 0) {
                    try {
                        mPlayerConfig.put("pr", 1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                } else {
                    try {
                        mPlayerConfig.put("pr", 0);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                }
            }
        });

        mPlayanimation.setOnClickListener(new OnClickListener() { //xuameng音柱动画
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                try {
                    musicAnimation = mPlayerConfig.getBoolean("music");   //xuameng音乐播放动画获取设置
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(musicAnimation) {
                    try {
                        mPlayerConfig.put("music", false);  //xuameng音乐播放动画关闭
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    releaseVisualizer();  //xuameng音乐播放动画
                } else {
                    try {
                        mPlayerConfig.put("music", true);   //xuameng音乐播放动画开启
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    initVisualizer();  //xuameng音乐播放动画
                }
            }
        });

        mPlayerEXOBtn.setOnClickListener(new OnClickListener() { //xuameng EXO解码
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                boolean exocode=Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false);  //xuameng exo解码默认设置
                int exoselect = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);  //xuameng exo解码动态选择
                try {
                    exoselect = mPlayerConfig.getInt("exocode");    //xuameng exo解码动态选择 0默认设置 1硬解 2软解
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(exoselect == 0) {
                    if (!exocode){
                        try {
                            mPlayerConfig.put("exocode", 2); 
                            Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }else{
                        try {
                            mPlayerConfig.put("exocode", 1);
                            Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }else if(exoselect == 1) {
                    try {
                        mPlayerConfig.put("exocode", 2);
                        Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else if(exoselect == 2) {
                    try {
                        mPlayerConfig.put("exocode", 1);
                        Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                listener.replay(false);
            }
        });

        mxuPlay.setOnFocusChangeListener(new View.OnFocusChangeListener() { //XUAMENG播放键预选取消SEEKBAR进度
            @Override //xuameng进入SEEKBAR
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus) {
                    isSEEKBAR = false; //xuameng进入SEEKBAR
                }
            }
        });
        mPreBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(view);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                listener.playPre();
            }
        });
        mPlayerScaleBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int scaleType = mPlayerConfig.getInt("sc");
                    scaleType++;
                    if(scaleType > 5) scaleType = 0;
                    mPlayerConfig.put("sc", scaleType);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    mControlWrapper.setScreenScaleType(scaleType);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerSpeedBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    float speed = (float) mPlayerConfig.getDouble("sp");
                    speed += 0.25f;
                    if(speed > 3) speed = 0.5f;
                    mPlayerConfig.put("sp", speed);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    speed_old = speed;
                    mControlWrapper.setSpeed(speed);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerSpeedBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("sp", 1.0f);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    speed_old = 1.0f;
                    mControlWrapper.setSpeed(1.0f);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(view);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int playerType = mPlayerConfig.getInt("pl");
                    ArrayList < Integer > exsitPlayerTypes = PlayerHelper.getExistPlayerTypes();
                    int playerTypeIdx = 0;
                    int playerTypeSize = exsitPlayerTypes.size();
                    for(int i = 0; i < playerTypeSize; i++) {
                        if(playerType == exsitPlayerTypes.get(i)) {
                            if(i == playerTypeSize - 1) {
                                playerTypeIdx = 0;
                            } else {
                                playerTypeIdx = i + 1;
                            }
                        }
                    }
                    playerType = exsitPlayerTypes.get(playerTypeIdx);
                    mPlayerConfig.put("pl", playerType);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                //xuameng不用选中焦点       mPlayerBtn.requestFocus();
                //xuameng不用选中焦点       mPlayerBtn.requestFocusFromTouch();
            }
        });
        mPlayerBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                FastClickCheckUtil.check(view);
                hideBottom();
                try {
                    int playerType = mPlayerConfig.getInt("pl");
                    int defaultPos = 0;
                    ArrayList < Integer > players = PlayerHelper.getExistPlayerTypes();
                    ArrayList < Integer > renders = new ArrayList < > ();
                    for(int p = 0; p < players.size(); p++) {
                        renders.add(p);
                        if(players.get(p) == playerType) {
                            defaultPos = p;
                        }
                    }
                    SelectDialog < Integer > dialog = new SelectDialog < > (mActivity);
                    dialog.setTip("请选择播放器");
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface < Integer > () {
                        @Override
                        public void click(Integer value, int pos) {
                            try {
                                dialog.cancel();
                                int thisPlayType = players.get(pos);
                                if(thisPlayType != playerType) {
                                    mPlayerConfig.put("pl", thisPlayType);
                                    updatePlayerCfgView();
                                    listener.updatePlayerCfg();
                                    listener.replay(false);
                                    if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                                        hideBottomXu();
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                     //xuameng不用选中焦点       mPlayerBtn.requestFocus();
                      //xuameng不用选中焦点      mPlayerBtn.requestFocusFromTouch();
                        }
                        @Override
                        public String getDisplay(Integer val) {
                            Integer playerType = players.get(val);
                            return PlayerHelper.getPlayerName(playerType);
                        }
                    }, new DiffUtil.ItemCallback < Integer > () {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                            return oldItem.intValue() == newItem.intValue();
                        }
                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                            return oldItem.intValue() == newItem.intValue();
                        }
                    }, renders, defaultPos);
                    dialog.show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerIJKBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
                    return;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                FastClickCheckUtil.check(view);
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottomXu();
                }
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    String ijk = mPlayerConfig.getString("ijk");
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
                    mPlayerConfig.put("ijk", ijk);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    listener.replay(false);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mPlayerIJKBtn.requestFocus();
                mPlayerIJKBtn.requestFocusFromTouch();
            }
        });
        //        增加播放页面片头片尾时间重置
        mPlayerTimeResetBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    mPlayerConfig.put("et", 0);
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeStartBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = safeTimeMs(mControlWrapper.getCurrentPosition());
                    int duration = safeTimeMs(mControlWrapper.getDuration());
                    if(current > duration / 2) return;
                    mPlayerConfig.put("st", current / 1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeStartBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("st", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mPlayerTimeSkipBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                try {
                    int current = safeTimeMs(mControlWrapper.getCurrentPosition());
                    int duration = safeTimeMs(mControlWrapper.getDuration());
                    if(current < duration / 2  || duration <= 1) return;     //xuameng 防止负数BUG
                    mPlayerConfig.put("et", (duration - current) / 1000);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mPlayerTimeSkipBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    mPlayerConfig.put("et", 0);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mZimuBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectSubtitle();
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottom();
                }
            }
        });
        mZimuBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                FastClickCheckUtil.check(view); //xuameng 防播放打断动画
                isLongClick = true;
                if (HawkConfig.exoSubtitle){      //xuameng 打开关闭exo内置方法字幕
                    if(mExoSubtitleView.getVisibility() == View.GONE  && mLrcView.getVisibility() == View.GONE) {
                        if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                            hideBottom();
                        }
                        mExoSubtitleView.setVisibility(VISIBLE);
                        if (!TextUtils.isEmpty(mLrcContent) && mLrcContent.length() > 10) {
                            mLrcView.setVisibility(View.VISIBLE);  //xuameng LRC歌词字幕
                        }
                        App.showToastShort(getContext(), "字幕已开启！");
                    } else {
                        if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                            hideBottom();
                        }
                        mExoSubtitleView.setVisibility(View.GONE);
                        mLrcView.setVisibility(View.GONE);   //xuameng LRC歌词字幕
                        App.showToastShort(getContext(), "字幕已关闭！");
                    }
                    return true;
                }

                if(mSubtitleView.getVisibility() == View.GONE && mLrcView.getVisibility() == View.GONE) {  //xuameng 打开关闭外置方法字幕
                    if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                        hideBottom();
                    }
                    mSubtitleView.setVisibility(VISIBLE);
                    if (!TextUtils.isEmpty(mLrcContent) && mLrcContent.length() > 10) {
                        mLrcView.setVisibility(View.VISIBLE);  //xuameng LRC歌词字幕
                    }
                    App.showToastShort(getContext(), "字幕已开启！");
                } else {
                    if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                        hideBottom();
                    }
                    mSubtitleView.setVisibility(View.GONE);
                    mLrcView.setVisibility(View.GONE);  //xuameng LRC歌词字幕
                    //                  mSubtitleView.destroy();
                    //                  mSubtitleView.clearSubtitleCache();
                    //                  mSubtitleView.isInternal = false;
                    App.showToastShort(getContext(), "字幕已关闭！");
                }
                return true;
            }
        });
        mAudioTrackBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.selectAudioTrack();
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottom();
                }
            }
        });
        mDanmuSettingBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                listener.showDanmuSetting();
            }
        });
        mDanmuSettingBtn.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if(mDanmuView.getVisibility() == View.VISIBLE) {
                    mDanmuView.hide();
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
                    App.showToastShort(getContext(), "弹幕已关闭");
                } else {
                    mDanmuView.show();
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, true));
                    App.showToastShort(getContext(), "弹幕已开启");
                }
                return true;
            }
        });
        mLandscapePortraitBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                setLandscapePortrait();
                if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                    hideBottom();
                }
            }
        });
        mxuPlay.setNextFocusRightId(R.id.seekBar); //xuameng底部菜单播放右键是进度条
        mSeekBar.setNextFocusLeftId(R.id.mxuplay); //xuameng底部菜单进度条左键是播放
        mNextBtn.setNextFocusLeftId(R.id.audio_track_select); //xuameng底部菜单下一集左键是音轨
        mxuPlay.setNextFocusLeftId(R.id.seekBar); //xuameng底部菜单播放左键是进度条
        mxuPlay.setNextFocusDownId(R.id.play_next); //xuameng底部菜单所有键上键都是播放
        mSeekBar.setNextFocusDownId(R.id.play_next);
        mNextBtn.setNextFocusUpId(R.id.mxuplay);
        mPreBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerRetry.setNextFocusUpId(R.id.mxuplay);
        mPlayrefresh.setNextFocusUpId(R.id.mxuplay);
        mPlayerScaleBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerSpeedBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerIJKBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerTimeStartEndText.setNextFocusUpId(R.id.mxuplay);
        mPlayerTimeStartBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerTimeSkipBtn.setNextFocusUpId(R.id.mxuplay);
        mPlayerTimeResetBtn.setNextFocusUpId(R.id.mxuplay);
        mZimuBtn.setNextFocusUpId(R.id.mxuplay);
        mAudioTrackBtn.setNextFocusRightId(R.id.play_next);
        mAudioTrackBtn.setNextFocusUpId(R.id.mxuplay); //xuameng底部菜单所有键上键都是播放完
        mPlayrender.setNextFocusUpId(R.id.mxuplay);
    }
    private void hideLiveAboutBtn() {
        if(mControlWrapper != null && mControlWrapper.getDuration() <= 1) {
            mPlayerSpeedBtn.setVisibility(GONE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(GONE);
            mPlayerTimeSkipBtn.setVisibility(GONE);
            mPlayerTimeResetBtn.setVisibility(GONE);
            mNextBtn.setNextFocusLeftId(R.id.audio_track_select); //xuameng底部菜单下一集左键是音轨
        } else {
            mPlayerSpeedBtn.setVisibility(View.VISIBLE);
            mPlayerTimeStartEndText.setVisibility(View.VISIBLE);
            mPlayerTimeStartBtn.setVisibility(View.VISIBLE);
            mPlayerTimeSkipBtn.setVisibility(View.VISIBLE);
            mPlayerTimeResetBtn.setVisibility(View.VISIBLE);
            mNextBtn.setNextFocusLeftId(R.id.audio_track_select); //xuameng底部菜单下一集左键是音轨
        }
    }
    public void initLandscapePortraitBtnInfo() {
        if(mControlWrapper != null && mActivity != null) {
            int width = mControlWrapper.getVideoSize()[0];
            int height = mControlWrapper.getVideoSize()[1];
            if(width == 0 || height == 0) {
                return;
            }
            double screenSqrt = ScreenUtils.getSqrt(mActivity);
            if(screenSqrt < 10.0 && width <= height) {
                mLandscapePortraitBtn.setVisibility(View.VISIBLE);
                mAudioTrackBtn.setNextFocusRightId(R.id.landscape_portrait);
                mLandscapePortraitBtn.setNextFocusRightId(R.id.play_next); //xuameng底部菜音轨右键是下一集
                int requestedOrientation = mActivity.getRequestedOrientation(); //xuameng 横竖屏显示BUG
                if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    mLandscapePortraitBtn.setText("竖屏");
                } else if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    mLandscapePortraitBtn.setText("横屏");
                }
            } else {
                mLandscapePortraitBtn.setVisibility(View.GONE);
                mAudioTrackBtn.setNextFocusRightId(R.id.play_next); //xuameng底部菜音轨右键是下一集
            }
        }
    }
    void setLandscapePortrait() {
        int requestedOrientation = mActivity.getRequestedOrientation();
        if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            mLandscapePortraitBtn.setText("横屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            mLandscapePortraitBtn.setText("竖屏");
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }
    void setLandscapePortraitXu() { //xuameng 横竖屏显示BUG
        int requestedOrientation = mActivity.getRequestedOrientation();
        if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            mLandscapePortraitBtn.setText("竖屏");
        } else if(requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            mLandscapePortraitBtn.setText("横屏");
        }
    }
    void initSubtitleInfo() {
        int subtitleTextSize = SubtitleHelper.getTextSize(mActivity);
        mSubtitleView.setTextSize(subtitleTextSize);
        mLrcView.setNormalColor(Color.WHITE);      //xuameng LRC歌词字幕 默认颜色
        mLrcView.setHighlightColor(Color.parseColor("#ff02f8e1"));  //xuameng LRC歌词字幕 高亮颜色
        mLrcView.setNormalTextSize(subtitleTextSize);  //xuameng LRC歌词字幕  默认字体大小
        mLrcView.setHighlightTextSize(subtitleTextSize);  //xuameng LRC歌词字幕  高亮字体大小
    }
    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }
    public void showParse(boolean userJxList) {
        mParseRoot.setVisibility(userJxList ? VISIBLE : GONE);
    }
    private JSONObject mPlayerConfig = null;
    public void setPlayerConfig(JSONObject playerCfg) {
        this.mPlayerConfig = playerCfg;
        updatePlayerCfgView();
    }
    void updatePlayerCfgView() {
        try {
            musicAnimation = mPlayerConfig.getBoolean("music");   //xuameng音乐播放动画设置
            Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 0);  // xuameng exo动态解码 大于0为选择 EXO解码恢复默认值
            boolean exoCode=Hawk.get(HawkConfig.EXO_PLAYER_DECODE, false); //xuameng EXO默认设置解码
            int exoSelect = Hawk.get(HawkConfig.EXO_PLAY_SELECTCODE, 0);  //xuameng exo解码动态选择
            exoSelect = mPlayerConfig.getInt("exocode");  //xuameng exo解码动态选择
            int playerType = mPlayerConfig.getInt("pl");   //xuameng播放器选择
            if (playerType >= 10) {   //xuameng 修复 外部播放器失效的BUG
                // 检查播放器是否在可用列表中
                ArrayList<Integer> existPlayerTypes = PlayerHelper.getExistPlayerTypes();
                boolean isPlayerAvailable = false;
        
                for (Integer availableType : existPlayerTypes) {
                    if (availableType == playerType) {
                        isPlayerAvailable = true;
                        break;
                    }
                }
                // 如果播放器不可用，则使用默认播放器1
                if (!isPlayerAvailable && existPlayerTypes.contains(1)) {
                    playerType = 1;  // 使用默认播放器1
                    mPlayerConfig.put("pl", playerType);  // 更新配置
                    listener.updatePlayerCfg();   //xuameng 必须同步以便持久化保存
                    App.showToastShort(getContext(), "上次使用的播放器已失效，已切换到默认播放器！");
                } 
            }

            int pr = mPlayerConfig.getInt("pr");  //xuameng渲染选择
            mPlayerBtn.setText(PlayerHelper.getPlayerName(playerType));
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerIJKBtn.setText(mPlayerConfig.getString("ijk"));
            mPlayerIJKBtn.setVisibility(playerType == 1 ? VISIBLE : GONE);
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerSpeedBtn.setText("x" + mPlayerConfig.getDouble("sp"));
            mPlayerTimeStartBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("st") * 1000));
            mPlayerTimeSkipBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("et") * 1000));
  //          mAudioTrackBtn.setVisibility((playerType == 1 || playerType == 2) ? VISIBLE : GONE);     //xuameng不判断音轨了全部显示
            mAudioTrackBtn.setVisibility(View.VISIBLE);
            mPlayrender.setText((pr == 0) ? "T渲染" : "S渲染"); //xuameng 渲染
            mPlayanimation.setText(musicAnimation ? "音柱已开" : "音柱已关");  //xuameng音乐播放动画获取状态
            if (exoSelect > 0){
                mPlayerEXOBtn.setText(exoSelect == 1 ? "硬解码" : "软解码");  //xuameng EXO解码 
                // xuameng EXO 动态选择解码 存储选择状态
                if (exoSelect == 1) {
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 1);  // 硬解码标记存储
                } else {
                    Hawk.put(HawkConfig.EXO_PLAY_SELECTCODE, 2);  // 软解码标记存储
                }
            }else {
                mPlayerEXOBtn.setText(exoCode ? "软解码" : "硬解码");  //xuameng EXO解码
            }
            mPlayerEXOBtn.setVisibility(playerType == 2 ? VISIBLE : GONE);  //xuameng EXO解码
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void setTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
        mPlayTitle1.setText(playTitleInfo);
    }
    public void setUrlTitle(String playTitleInfo) {
        mPlayTitle.setText(playTitleInfo);
    }
    public void resetSpeed() {
        skipEnd = true;
        mHandler.removeMessages(1004);
        mHandler.sendEmptyMessageDelayed(1004, 100);
    }
    public void setHasDanmu(boolean hasDanmu) {
        this.hasDanmu = hasDanmu;
        updateDanmuBtn();
    }

    public void updateDanmuBtn() {
        if (mDanmuSettingBtn == null) return;
        mDanmuSettingBtn.setVisibility(hasDanmu ? VISIBLE : GONE);
    }
    public interface VodControlListener {
        void playNext(boolean rmProgress);
        void playPre();
        void prepared();
        void changeParse(ParseBean pb);
        void updatePlayerCfg();
        void replay(boolean replay);
        void errReplay();
        void selectSubtitle();
        void selectAudioTrack();
        void showDanmuSetting();
        void hideTipXu(); //xuameng隐藏错误信息
        void startPlayUrl(String url, HashMap < String, String > headers); //xuameng广告过滤
    }
    public void setListener(VodControlListener listener) {
        this.listener = listener;
    }

    public void updatePlayerCfg() {    //xuameng新增变更更新方法
        if (listener != null) {
            listener.updatePlayerCfg();
        }
    }

    private VodControlListener listener;
    private boolean skipEnd = true;
    @Override
    protected void setProgress(int duration, int position) {
        if(mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        if(skipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mPlayerConfig.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false;
                listener.playNext(true);
            }
        }
        if(position < 0) position = 0; //xuameng系统播放器有时会有负进度的BUG
        if(duration >= 1000 && duration <= 180000000) {
            mSeekBar.setEnabled(true);
            mSeekBar.setProgress(position); //xuameng当前进程
            mSeekBar.setMax(duration); //xuameng设置总进程必须
            mCurrentTime.setText(PlayerUtils.stringForTime(position)); //xuameng当前进程时间
            mTotalTime.setText(PlayerUtils.stringForTime(duration)); //xuameng总进程时间
        } else {
            mSeekBar.setEnabled(false);
            duration = 0;
            mSeekBar.setProgress(0); //xuameng视频总长度为0重置进度条为0
            mSeekBar.setMax(duration); //xuameng设置总进程必须
            mCurrentTime.setText(PlayerUtils.stringForTime(position)); //xuameng当前进程时间
            mTotalTime.setText(PlayerUtils.stringForTime(duration)); //xuameng总进程时间
        }
        int percent = mControlWrapper.getBufferedPercentage();
        int totalBuffer = percent * duration;
        int SecondaryProgress = totalBuffer / 100;
        if(percent >= 98) {
            mSeekBar.setSecondaryProgress(duration);
        } else {
            mSeekBar.setSecondaryProgress(SecondaryProgress); //xuameng缓冲进度
        }
    }
    private boolean simSlideStart = false;
    private boolean simSlideStartXu = false;
    private int simSeekPosition = 0;
    private int simSlideOffset = 0;
    private long mSpeedTimeUp = 0; //xuameng上键间隔时间
    public void tvSlideStop() {
        int duration = safeTimeMs(mControlWrapper.getDuration());
        if(duration >= 1000 && duration <= 180000000) {
            mIsDragging = false; //xuamengsetProgress监听
            mControlWrapper.startProgress(); //xuameng启动进程
            mControlWrapper.startFadeOut();
            mSpeedTimeUp = 0;
            if(!simSlideStart) return;
            mControlWrapper.seekTo(simSeekPosition);
            //if(!mControlWrapper.isPlaying())
                //xuameng快进暂停就暂停测试    mControlWrapper.start();    //测试成功，如果想暂停时快进自动播放取消注销
            simSlideStart = false;
            //simSeekPosition = 0;  //XUAMENG重要要不然重0播放
            simSlideOffset = 0;
            mHandler.sendEmptyMessageDelayed(1001, 100);   //xuamengTV隐藏快进图标
        }
    }
    public void tvSlideStopXu() { //xuameng修复SEEKBAR快进重新播放问题
        mIsDragging = false; //xuamengsetProgress监听
        mControlWrapper.startProgress(); //xuameng启动进程
        mControlWrapper.startFadeOut();
        mSpeedTimeUp = 0;
        if(!simSlideStartXu) return;
        if(isSEEKBAR) {
            mControlWrapper.seekTo(simSeekPosition);
        }
        //if(!mControlWrapper.isPlaying())
            //xuameng快进暂停就暂停测试    mControlWrapper.start();    //测试成功，如果想暂停时快进自动播放取消注销
        simSlideStartXu = false;
        //		simSeekPosition = 0;      //XUAMENG重要
        simSlideOffset = 0;
    }
    public void tvSlideStart(int dir) {
        int duration = safeTimeMs(mControlWrapper.getDuration());
        if(duration >= 1000 && duration <= 180000000) {
            mIsDragging = true; //xuamengsetProgress不监听
            mControlWrapper.stopProgress(); //xuameng结束进程
            mControlWrapper.stopFadeOut();
            if(!simSlideStart) {
                simSlideStart = true;
            }
            // 每次10秒
            if(mSpeedTimeUp == 0) {
                mSpeedTimeUp = System.currentTimeMillis();
            }
            if(System.currentTimeMillis() - mSpeedTimeUp < 3000) {
                simSlideOffset += (10000 * dir);
            }
            if(System.currentTimeMillis() - mSpeedTimeUp > 3000 && System.currentTimeMillis() - mSpeedTimeUp < 6000) {
                simSlideOffset += (30000 * dir);
            }
            if(System.currentTimeMillis() - mSpeedTimeUp > 6000 && System.currentTimeMillis() - mSpeedTimeUp < 9000) {
                simSlideOffset += (60000 * dir);
            }
            if(System.currentTimeMillis() - mSpeedTimeUp > 9000) {
                simSlideOffset += (120000 * dir);
            }
            int currentPosition = safeTimeMs(mControlWrapper.getCurrentPosition());
            int position = simSlideOffset + currentPosition;
            if(position > duration) position = duration;
            if(position < 0) position = 0;
            updateSeekUI(currentPosition, position, duration);
            simSeekPosition = position;
            mSeekBar.setProgress(simSeekPosition); //xuameng设置SEEKBAR当前进度
            mCurrentTime.setText(PlayerUtils.stringForTime(simSeekPosition)); //xuameng设置SEEKBAR当前进度
        }
    }
    public void tvSlideStartXu(int dir) {
        isSEEKBAR = true;
        mIsDragging = true; //xuamengsetProgress不监听
        mControlWrapper.stopProgress(); //xuameng结束进程
        mControlWrapper.stopFadeOut();
        int duration = safeTimeMs(mControlWrapper.getDuration());
        if(!simSlideStartXu) {
            simSlideStartXu = true;
        }
        // 每次10秒
        if(mSpeedTimeUp == 0) {
            mSpeedTimeUp = System.currentTimeMillis();
        }
        if(System.currentTimeMillis() - mSpeedTimeUp < 3000) {
            simSlideOffset += (10000 * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 3000 && System.currentTimeMillis() - mSpeedTimeUp < 6000) {
            simSlideOffset += (30000 * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 6000 && System.currentTimeMillis() - mSpeedTimeUp < 9000) {
            simSlideOffset += (60000 * dir);
        }
        if(System.currentTimeMillis() - mSpeedTimeUp > 9000) {
            simSlideOffset += (120000 * dir);
        }
        int currentPosition = safeTimeMs(mControlWrapper.getCurrentPosition());
        int position = (int)(simSlideOffset + currentPosition);
        if(position > duration) position = duration;
        if(position < 0) position = 0;
        simSeekPosition = position;
        mSeekBar.setProgress(simSeekPosition); //xuameng设置SEEKBAR当前进度
        mCurrentTime.setText(PlayerUtils.stringForTime(simSeekPosition)); //xuameng设置SEEKBAR当前进度
    }
    @Override
    protected void updateSeekUI(int curr, int seekTo, int duration) { //xuameng手机滑动屏幕快进
        super.updateSeekUI(curr, seekTo, duration);
        if(seekTo > curr) {
            mProgressIcon.setImageResource(R.drawable.icon_prexu); //xuameng快进图标更换
        } else {
            mProgressIcon.setImageResource(R.drawable.icon_backxu); //xuameng快进图标更换
        }
        mIsDragging = false; //xuamengsetProgress监听
        mControlWrapper.startProgress(); //xuameng启动进程 手机滑动快进时候暂停图标文字跟随变化
        mControlWrapper.startFadeOut();
        mProgressText.setText(PlayerUtils.stringForTime(seekTo) + " / " + PlayerUtils.stringForTime(duration));
        mHandler.sendEmptyMessage(1000);
        mHandler.removeMessages(1001);
        if(!simSlideStart) {
            mHandler.sendEmptyMessageDelayed(1001, 100);   //xuameng手机隐藏快进图标
        }
    }
    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        videoPlayState = playState;
        switch(playState) {
            case VideoView.STATE_IDLE:
                mLandscapePortraitBtn.setVisibility(View.GONE);
                if(MxuamengMusic.getVisibility() == View.VISIBLE) { //xuameng播放音乐背景
                    MxuamengMusic.setVisibility(GONE);
                }
                if(iv_circle_bg.getVisibility() == View.VISIBLE) { //xuameng音乐播放时图标
                    iv_circle_bg.setVisibility(GONE);
                }
                if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                    customVisualizer.setVisibility(GONE);
                }
                mPlayLoadNetSpeed.setVisibility(View.GONE);
                if(isBottomVisible() && mSeekBarhasFocus) { //xuameng假如焦点在SeekBar
                    mxuPlay.requestFocus(); //底部菜单默认焦点为播放
                }
                isVideoPlay = false;
                isBufferIng = false; //xuameng 判断是否进在缓冲视频
                mxuPlay.setText("准备");
                mVideoSize.setText("[ 0 X 0 ]");
                releaseVisualizer();  //xuameng播放音乐背景
                clearSubtitleCache();  //xuameng清除字幕缓存
                mHidePauseIng(); //xuameng 隐藏暂停图标
                break;
            case VideoView.STATE_PLAYING:
                isVideoPlay = true;
                //isBufferIng = false; //xuameng 判断是否进在缓冲视频
                mxuPlay.setText("暂停"); //xuameng底部菜单显示暂停
                initLandscapePortraitBtnInfo();
                listener.hideTipXu(); //xuameng 只要播放就隐藏错误信息
                startProgress();
                break;
            case VideoView.STATE_PAUSED:
                isVideoPlay = false;
                mxuPlay.setText("播放"); //xuameng底部菜单显示播放
                //isBufferIng = false; //xuameng 判断是否进在缓冲视频
                //mTopRoot1.setVisibility(GONE);       //xuameng隐藏上面菜单
                //mTopRoot2.setVisibility(GONE);       //xuameng隐藏上面菜单
                //mPlayTitle.setVisibility(VISIBLE);   //xuameng显示上面菜单
                //pauseIngXu();
                break;
            case VideoView.STATE_ERROR:
                mPlayLoadNetSpeed.setVisibility(View.GONE);
                isVideoPlay = false;
                if(isBottomVisible() && mSeekBarhasFocus) { //xuameng假如焦点在SeekBar
                    mxuPlay.requestFocus(); //底部菜单默认焦点为播放
                }
                listener.errReplay();
                mxuPlay.setText("准备");
                mHidePauseIng(); //xuameng 隐藏暂停图标
                break;
            case VideoView.STATE_PREPARED:
                mPlayLoadNetSpeed.setVisibility(View.GONE);
                isVideoPlay = false;
                isBufferIng = false; //xuameng 判断是否进在缓冲视频
                hideLiveAboutBtn();
                listener.prepared();
                String width = Integer.toString(mControlWrapper.getVideoSize()[0]);
                String height = Integer.toString(mControlWrapper.getVideoSize()[1]);
                mVideoSize.setText("[ " + width + " X " + height + " ]");
                try {
                    musicAnimation = mPlayerConfig.getBoolean("music");  //xuameng音乐播放动画获取设置
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (musicAnimation){
                    int newSessionId = mControlWrapper.getAudioSessionId();   //xuameng音乐播放动画
                    if(newSessionId != audioSessionId) { // 避免重复初始化
                       initVisualizer();  //xuameng音乐播放动画
                    }
                }
                break;
            case VideoView.STATE_BUFFERED:
                mPlayLoadNetSpeed.setVisibility(View.GONE);
                isVideoPlay = true;
                isBufferIng = false; //xuameng 判断是否进在缓冲视频
                break;
            case VideoView.STATE_PREPARING:
                if(isBottomVisible() && mSeekBarhasFocus) { //xuameng假如焦点在SeekBar
                    mxuPlay.requestFocus(); //底部菜单默认焦点为播放
                }
                mLandscapePortraitBtn.setVisibility(View.GONE);
                simSeekPosition = 0; //XUAMENG重要,换视频时重新记录进度
                isVideoPlay = false;
            case VideoView.STATE_BUFFERING:
			    if(mProgressRoot.getVisibility() == View.GONE) { //xuameng进程图标
                    mPlayLoadNetSpeed.setVisibility(View.VISIBLE);
                }
                if(iv_circle_bg.getVisibility() == View.VISIBLE && mLrcView.getVisibility() == View.GONE) { //xuameng音乐播放时图标
                    iv_circle_bg.setVisibility(GONE);
                }
                isVideoPlay = false;
                isBufferIng = true; //xuameng 判断是否进在缓冲视频
                speedPlayEnd();  //xuameng 停止快进
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                mPlayLoadNetSpeed.setVisibility(View.GONE);
                clearSubtitleCache();
                isVideoPlay = false;
                isBufferIng = false; //xuameng 判断是否进在缓冲视频
                listener.playNext(true);
                break;
        }
    }
    boolean isBottomVisible() { //xuameng底部菜单是否显示
        return mBottomRoot.getVisibility() == VISIBLE;
    }
    void showBottom() {
        isSEEKBAR = false; //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
    }
    void hideBottom() {
        isSEEKBAR = false; //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1003);
    }
    void hideBottomXu() {
        isSEEKBAR = false; //XUAMENG隐藏菜单时修复进度条BUG
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1005);
    }
    public void playIngXu() {
        mxuPlay.setText("暂停"); //xuameng底部菜单显示暂停
        if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
            hideBottom();
        }
        ObjectAnimator animator9 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0, 700); //xuameng动画暂停菜单开始
        animator9.setDuration(300); //xuameng动画暂停菜单
        animator9.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                isPlaying = true; //xuameng动画开启
            }
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mTvPausexu.setVisibility(GONE); //xuameng动画暂停菜单隐藏 
                isPlaying = false; //xuameng动画开启
            }
        });
        animator9.start(); //xuameng动画暂停菜单结束
    }
    public void pauseIngXu() {
        mTvPausexu.setVisibility(VISIBLE);
        mxuPlay.setText("播放"); //xuameng底部菜单显示播放
        mPauseContainer.setVisibility(GONE); // xuameng播放标题、暂停时间
        if(mBottomRoot.getVisibility() == View.GONE && !isDisplay) { //xuameng如果没显示菜单就显示
            showBottom();
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        }
        ObjectAnimator animator8 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", 700, 0); //xuameng动画暂停菜单开始
        animator8.setDuration(300); //xuameng动画暂停菜单
        animator8.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                isPlaying = true; //xuameng动画开启
            }
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isPlaying = false; //xuameng动画开启
            }
        });
        animator8.start(); //xuameng动画暂停菜单结束
    }

    public void mPauseIngXu() {        //xuameng 全屏时如果是暂停状态就显示暂停图标
		if(isInPlaybackState()){
            if (!mControlWrapper.isPlaying() && mTvPausexu.getVisibility() == View.GONE){
                mTvPausexu.setVisibility(VISIBLE);
                mPauseContainer.setVisibility(VISIBLE);  // xuameng播放标题、暂停时间
                mxuPlay.setText("播放"); //xuameng底部菜单显示播放
                mHandler.postDelayed(mUpdatePauseLayout, 50);   // Workaround Fix : SurfaceView
            }
        }
    }

	public void mHidePauseIng() { //xuameng 隐藏暂停图标
        if(!isPlaying && mTvPausexu.getVisibility() == View.VISIBLE) {
            ObjectAnimator animator30 = ObjectAnimator.ofFloat(mTvPausexu, "translationX", -0, 700); //xuameng动画暂停菜单开始
            animator30.setDuration(300); //xuameng动画暂停菜单
            animator30.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    if(mPauseContainer.getVisibility() == View.VISIBLE) { // xuameng播放标题、暂停时间
                        mPauseContainer.setVisibility(GONE); // xuameng播放标题、暂停时间
                    }
                    isPlaying = true; //xuameng动画开启
                }
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mTvPausexu.setVisibility(GONE); //xuameng动画暂停菜单隐藏 
                    isPlaying = false; //xuameng动画开启
                }
            });
            animator30.start(); //xuameng动画暂停菜单结束
        }
    }

    private final Runnable mUpdatePauseLayout = new Runnable() {  //解决surfaceview不显示问题
        @Override
        public void run() {
            mTvPausexu.requestLayout();  //xuameng暂停图标
            mPauseContainer.requestLayout();  // xuameng播放标题、暂停时间
        }
    };

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        myHandle.removeCallbacks(myRunnable);
        if(super.onKeyEvent(event)) {
            return true;
        }
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if(isBottomVisible()) {
            mHandler.removeMessages(1002);
            //        mHandler.removeMessages(1003);      xuameng重大BUG修复
            myHandle.postDelayed(myRunnable, myHandleSeconds);
            return super.dispatchKeyEvent(event);
        }
        boolean isInPlayback = isInPlaybackState();
        if(action == KeyEvent.ACTION_DOWN) {
            if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if(isInPlayback) {
                    tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isLongClick || isAnimation || isDisplay) { //xuameng 防播放打断动画					
                    return true;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                if(isInPlayback) {
                    if(!isDisplay || !isAnimation) {
                        if(mControlWrapper.isPlaying()) {
                            pauseIngXu();
                            togglePlay();
                            return true;
                        }
                        if(!mControlWrapper.isPlaying()) {
                            playIngXu();
                            togglePlay();
                            return true;
                        }
                    }
                    return true;
                }
            } else if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_MENU) {
                if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画					
                    return true;
                }
                DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
                if(mBottomRoot.getVisibility() == View.GONE && !isDisplay) {
                    showBottom();
                    myHandle.postDelayed(myRunnable, myHandleSeconds);
                    return true;
                }
            }
        } else if(action == KeyEvent.ACTION_UP) {
            if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if(isInPlayback) {
                    tvSlideStop();
                    return true;
                }
            } else if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                isLongClick = false;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean fromLongPress;
    private float speed_old = 1.0f;
    private void speedPlayStart() {    //xuameng 启动快进
        if(isVideoPlay && mControlWrapper.isPlaying()) {
            fromLongPress = true;
            try {
                speed_old = (float) mPlayerConfig.getDouble("sp");
                float speed = 3.0f;
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
                if(iv_circle_bg.getVisibility() == View.VISIBLE && mLrcView.getVisibility() == View.GONE) { //xuameng音乐播放时图标  当歌词显示时不隐藏旋转图标
                    iv_circle_bg.setVisibility(GONE);
                }
                if(mProgressRoot.getVisibility() == View.VISIBLE) { //xuameng进程图标
                    mProgressRoot.setVisibility(GONE);
                }
                findViewById(R.id.play_speed_3_container).setVisibility(View.VISIBLE);
            } catch (JSONException f) {
                f.printStackTrace();
            }
        }
    }
    private void speedPlayEnd() {  //xuameng 停止快进
        if(fromLongPress) {
            fromLongPress = false;
            try {
                float speed = speed_old;
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
            } catch (JSONException f) {
                f.printStackTrace();
            }
            findViewById(R.id.play_speed_3_container).setVisibility(View.GONE);
        }
    }
    @Override
    public void onLongPress(MotionEvent e) {
        speedPlayStart();
    }
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction() == MotionEvent.ACTION_UP) {
            speedPlayEnd();
        }
        return super.onTouchEvent(e);
    }
    private final Handler mmHandler = new Handler();
    private Runnable mLongPressRunnable;
    private static final long LONG_PRESS_DELAY = 300;
    private boolean setMinPlayTimeChange(String typeEt, boolean increase) { //xuameng微调片头片尾
        myHandle.removeCallbacks(myRunnable);
        myHandle.postDelayed(myRunnable, myHandleSeconds);
        try {
            int currentValue = mPlayerConfig.optInt(typeEt, 0);
            if(currentValue != 0) {
                int newValue = increase ? currentValue + 1 : currentValue - 1;
                if(newValue < 0) {
                    newValue = 0;
                }
                mPlayerConfig.put(typeEt, newValue);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(isBottomVisible()) {
            if(keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if(mPlayerTimeStartBtn.hasFocus()) {
                    if(setMinPlayTimeChange("st", true)) { //xuameng微调片头片尾
                        return true;
                    }
                }
            }
            if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { //xuameng微调片头片尾
                if(mPlayerTimeStartBtn.hasFocus()) {
                    if(setMinPlayTimeChange("st", false)) return true;
                }
            }
            if(keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if(mPlayerTimeSkipBtn.hasFocus()) {
                    if(setMinPlayTimeChange("et", true)) { //xuameng微调片头片尾
                        return true;
                    }
                }
            }
            if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { //xuameng微调片头片尾
                if(mPlayerTimeSkipBtn.hasFocus()) {
                    if(setMinPlayTimeChange("et", false)) return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }
        if((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.getRepeatCount() == 0) {
            mLongPressRunnable = new Runnable() {
                @Override
                public void run() {
                    speedPlayStart(); //xuameng长按上键快放
                }
            };
            mmHandler.postDelayed(mLongPressRunnable, LONG_PRESS_DELAY);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if(mLongPressRunnable != null) {
                mmHandler.removeCallbacks(mLongPressRunnable);
                mLongPressRunnable = null;
            }
            speedPlayEnd();
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) { //延时回调,延迟时间是 180 ms,
        if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防止180ms内点击返回键，又会弹击菜单				
            return false;
        }
        DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
        if(isClickBackBtn) { //xuameng 罕见BUG  防止180ms内点击BackBtn键，又会弹击菜单	
            return false;
        }
        myHandle.removeCallbacks(myRunnable);
        if(mBottomRoot.getVisibility() == View.GONE && !isDisplay) {
            showBottom();
            // 闲置计时关闭
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        } else {
            if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                hideBottom();
            }
        }
        return true;
    }
    @Override
    public boolean onDoubleTap(MotionEvent e) { //xuameng双击
        if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画
            return true;
        }
        DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
        if(!isLock && isInPlaybackState()) {
            if(!isDisplay || !isAnimation) {
                if(mControlWrapper.isPlaying()) {
                    pauseIngXu();
                    togglePlay();
                    return true;
                }
                if(!mControlWrapper.isPlaying()) {
                    playIngXu();
                    togglePlay();
                    return true;
                }
            }
        }
        return true;
    }
    private class LockRunnable implements Runnable {
        @Override
        public void run() {
            mLockView.setVisibility(GONE);  //xuameng 必须GONE如果写成INVISIBLE在surface下会没反应
        }
    }
    @Override
    public boolean onBackPressed() {
        if(isBottomVisible() && (System.currentTimeMillis() - DOUBLE_CLICK_TIME) < 300) { //xuameng返回键防连击1.5秒（为动画,当动画显示时）
            DOUBLE_CLICK_TIME = System.currentTimeMillis();
            return true;
        }
        if((System.currentTimeMillis() - DOUBLE_CLICK_TIME_2) < 300 || isAnimation || isDisplay) { //xuameng 防播放打断动画					
            return true;
        }
        DOUBLE_CLICK_TIME_2 = System.currentTimeMillis();
        if(isClickBackBtn) { //xuameng 罕见BUG
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isClickBackBtn = false;
                }
            }, 300);
            if((System.currentTimeMillis() - DOUBLE_CLICK_TIME) > 300) { //xuameng  屏幕上的返回键退出
                DOUBLE_CLICK_TIME = System.currentTimeMillis();
                mBottomRoot.setVisibility(GONE); //动画结束后隐藏下菜单
                mTopRoot1.setVisibility(GONE); //动画结束后隐藏上菜单
                mTopRoot2.setVisibility(GONE); //动画结束后隐藏上菜单
                mPauseContainer.setVisibility(GONE); // xuameng播放标题、暂停时间
                backBtn.setVisibility(GONE); //返回键隐藏菜单
                mTvPausexu.setVisibility(GONE); //隐藏暂停菜单
                mLockView.setVisibility(GONE); //xuameng隐藏屏幕锁
            }
            return false;
        }
        if(super.onBackPressed()) { //xuameng返回退出
            iv_circle_bg.setVisibility(GONE); //xuameng音乐播放时图标
            MxuamengMusic.setVisibility(GONE); //xuameng播放音乐背景
            customVisualizer.setVisibility(GONE);  //xuameng播放音乐柱状图
            return true;
        }
        if(isBottomVisible() && (System.currentTimeMillis() - DOUBLE_CLICK_TIME > 300)) { //xuameng按返回键退出
            DOUBLE_CLICK_TIME = System.currentTimeMillis();
            if(!isAnimation && mBottomRoot.getVisibility() == View.VISIBLE) {
                hideBottom();
            }
            return true;
        }
        mPauseContainer.setVisibility(GONE); // xuameng播放标题、暂停时间
        backBtn.setVisibility(GONE); //返回键隐藏菜单
        mTvPausexu.setVisibility(GONE); //隐藏暂停菜单
        mLockView.setVisibility(GONE); //xuameng隐藏屏幕锁
        return false;
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(myRunnable2);
        mHandler.removeCallbacks(xuRunnable);
        mHandler.removeCallbacks(myRunnableMusic);
        mHandler.removeCallbacks(myRunnableXu);
        if(mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        clearSubtitleCache();  //xuameng清除字幕缓存
        releaseVisualizer();  //xuameng音乐播放动画
    }
    //尝试去bom
    public String getWebPlayUrlIfNeeded(String webPlayUrl) {
        if(webPlayUrl != null && !webPlayUrl.contains("127.0.0.1:9978") && webPlayUrl.contains(".m3u8")) {
            try {
                String urlEncode = URLEncoder.encode(webPlayUrl, "UTF-8");
                LOG.i("echo-BOM-------");
                return ControlManager.get().getAddress(true) + "proxy?go=bom&url=" + urlEncode;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return webPlayUrl;
    }
    public String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }
    private static int switchPlayerCount = 0;
    public boolean switchPlayer() {
        try {
            int playerType = mPlayerConfig.getInt("pl");
            int p_type = (playerType == 1) ? playerType + 1 : (playerType == 2) ? playerType - 1 : playerType;
            if(p_type != playerType) {
                mPlayerConfig.put("pl", p_type);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                App.showToastShort(getContext(), "切换到" + (p_type == 1 ? "IJK" : "EXO") + "播放器重试！");
            } else {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        if(switchPlayerCount == 1) {
            switchPlayerCount = 0;
            return true;
        }
        switchPlayerCount++;
        return false;
    }
    public void playM3u8(final String url, final HashMap < String, String > headers) {
        if(url.contains("url=")) {
            listener.startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        final HttpHeaders okGoHeaders = new HttpHeaders();
        if(headers != null) {
            for(Map.Entry < String, String > entry: headers.entrySet()) {
                okGoHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        OkGo. < String > get(url).tag("m3u8-1").headers(okGoHeaders).execute(new AbsCallback < String > () {
            @Override
            public void onSuccess(Response < String > response) {
                String content = response.body();
                if(!content.startsWith("#EXTM3U")) {
                    listener.startPlayUrl(url, headers);
                    return;
                }
                String forwardUrl = extractForwardUrl(url, content);
                if(forwardUrl.isEmpty()) {
                    LOG.i("echo-m3u81-to-play");
                    processM3u8Content(url, content, headers);
                } else {
                    fetchAndProcessForwardUrl(forwardUrl, headers, okGoHeaders, url);
                }
            }
            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
            @Override
            public void onError(Response < String > response) {
                super.onError(response);
                LOG.e("echo-m3u8请求错误1: " + response.getException());
                listener.startPlayUrl(url, headers);
            }
        });
    }
    private String extractForwardUrl(String baseUrl, String content) {
        String[] lines = content.split("\\r?\\n", 50);
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if(line.startsWith("#EXT-X-STREAM-INF")) {
                // 只需要找接下来的几行
                for(int j = i + 1; j < lines.length; j++) {
                    String targetLine = lines[j].trim();
                    if(targetLine.isEmpty()) continue;
                    if(isValidM3u8Line(targetLine)) {
                        return resolveForwardUrl(baseUrl, targetLine);
                    }
                }
            }
        }
        return "";
    }
    private boolean isValidM3u8Line(String line) {
        return !line.startsWith("#") && (line.endsWith(".m3u8") || line.contains(".m3u8?"));
    }
    private void processM3u8Content(String url, String content, HashMap < String, String > headers) {
        String basePath = getBasePath(url);
        RemoteServer.m3u8Content = M3u8.purify(basePath, content);
        if(RemoteServer.m3u8Content == null || M3u8.currentAdCount == 0) {
            LOG.i("echo-m3u8内容解析：未检测到广告");
            listener.startPlayUrl(url, headers);
        } else {
            listener.startPlayUrl(ControlManager.get().getAddress(true) + "proxyM3u8", headers);
            App.showToastShort(getContext(), "聚汇影视已移除" + M3u8.currentAdCount + "条视频广告！");
        }
    }
    private void fetchAndProcessForwardUrl(final String forwardUrl, final HashMap < String, String > headers, HttpHeaders okGoHeaders, final String fallbackUrl) {
        OkGo. < String > get(forwardUrl).tag("m3u8-2").headers(okGoHeaders).execute(new AbsCallback < String > () {
            @Override
            public void onSuccess(Response < String > response) {
                String content = response.body();
                LOG.i("echo-m3u82-to-play");
                processM3u8Content(forwardUrl, content, headers);
            }
            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
            @Override
            public void onError(Response < String > response) {
                super.onError(response);
                LOG.e("echo-重定向 m3u8 请求错误: " + response.getException());
                listener.startPlayUrl(fallbackUrl, headers);
            }
        });
    }
    private String getBasePath(String url) {
        int ilast = url.lastIndexOf('/');
        return url.substring(0, ilast + 1);
    }
    private String resolveForwardUrl(String baseUrl, String line) {
        try {
            // 使用 URL 构造器自动解析相对路径
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, line);
            return resolved.toString();
        } catch (MalformedURLException e) {
            // 出现异常时可以记录日志，并返回原始 line
            LOG.e("echo-resolveForwardUrl异常: " + e.getMessage());
            return line;
        }
    }
    public String firstUrlByArray(String url) //xuameng B站
    {
        try {
            JSONArray urlArray = new JSONArray(url);
            for(int i = 0; i < urlArray.length(); i++) {
                String item = urlArray.getString(i);
                if(item.contains("http")) {
                    url = item;
                    break; // 找到第一个立即终止循环
                }
            }
        } catch (JSONException e) {}
        return url;
    }
    public void evaluateScript(SourceBean sourceBean, String url, WebView web_view, XWalkView xWalk_view) {
        String clickSelector = sourceBean.getClickSelector().trim();
        clickSelector = clickSelector.isEmpty() ? VideoParseRuler.getHostScript(url) : clickSelector;
        if(!clickSelector.isEmpty()) {
            String selector;
            if(clickSelector.contains(";") && !clickSelector.endsWith(";")) {
                String[] parts = clickSelector.split(";", 2);
                if(!url.contains(parts[0])) {
                    return;
                }
                selector = parts[1].trim();
            } else {
                selector = clickSelector.trim();
            }
            // 构造点击的 JS 代码
            String js = selector;
            //            if(!selector.contains("click()"))js+=".click();";
            LOG.i("echo-javascript:" + js);
            if(web_view != null) {
                //4.4以上才支持这种写法
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    web_view.evaluateJavascript(js, null);
                } else {
                    web_view.loadUrl("javascript:" + js);
                }
            }
            if(xWalk_view != null) {
                //4.0+开始全部支持这种写法
                xWalk_view.evaluateJavascript(js, null);
            }
        }
    }
    public void stopOther(){ //xuameng停止磁力下载
        Thunder.stop(false); //停止磁力下载
        Jianpian.finish(); //停止p2p下载
        App.getInstance().setDashData(null);
    }

    public void clearSubtitleCache(){ //xuameng清除字幕缓存
        mSubtitleView.setVisibility(View.GONE); //xuameng 外部方法字幕
        mSubtitleView.destroy();
        mSubtitleView.clearSubtitleCache();
        mSubtitleView.onSubtitleChanged(null);
        mSubtitleView.setVisibility(View.VISIBLE);
        mExoSubtitleView.setVisibility(View.GONE);    //xuameng EXO内置字幕
        mExoSubtitleView.setCues(null); // xuameng清除EXO字幕数据
        mLrcView.setVisibility(View.GONE);  //xuameng LRC歌词字幕
        mLrcContent = "";  //xuameng 清除LRC歌词字幕
        mLrcView.reset(); //xuameng 清除LRC歌词播放进度重置
    }

    private void initVisualizer() {   //xuameng播放音乐柱状图
        releaseVisualizer();  // 确保先释放已有实例
        // 基础检查
        if (getContext() == null) {
            Log.w(TAG, "Context is null");
            return;
        }
        int sessionId = mControlWrapper != null ? mControlWrapper.getAudioSessionId() : 0;
        if (sessionId <= 0) {
            Log.w(TAG, "Invalid audio session ID");
            return;
        }
        try {
        // 统一创建Visualizer实例（仅一次）
            mVisualizer = new Visualizer(sessionId);
            // 智能采样率设置
            int targetRate = Visualizer.getMaxCaptureRate() / 2;
            // 设置数据捕获监听器
            mVisualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer viz, byte[] bytes, int rate) {
                    // 可选波形数据捕获
                    }
                    @Override
                    public void onFftDataCapture(Visualizer visualizer, byte[] fftData, int samplingRate) {
                        if (fftData == null || customVisualizer == null) return;
                         // 1. 计算当前音量级别（0-1范围）
                        float volumeLevel = calculateVolumeLevel(getContext());
                        Runnable updateTask = () -> {
                            try {
                                if (customVisualizer != null) {
                                    customVisualizer.updateVisualizer(fftData, volumeLevel);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Visualizer update error", e);
                            }
                        };
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            updateTask.run();
                        } else {
                            new Handler(Looper.getMainLooper()).post(updateTask);
                        }
                    }
                },
                targetRate,
                false,  // 不捕获波形数据
                true    // 捕获FFT数据
            );
            mVisualizer.setEnabled(true);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Visualizer state error", e);
            releaseVisualizer();
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "Device doesn't support Visualizer", e);
            releaseVisualizer();
        } catch (Exception e) {
            Log.e(TAG, "Visualizer init failed", e);
            releaseVisualizer();
        }
    }

    private synchronized void releaseVisualizer() {   //xuameng播放音乐柱状图
        try {
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
                Log.d(TAG, "Visualizer released successfully");
            }
            if (customVisualizer != null) {
                customVisualizer.release();
            }
            if(customVisualizer.getVisibility() == View.VISIBLE) { //xuameng播放音乐柱状图
                customVisualizer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing visualizer", e);
        }
    }

    public static float calculateVolumeLevel(Context context) {  //系统音量监控
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        
        // 计算0-1范围的百分比
        float volumePercent = (float) currentVolume / maxVolume;
        
        // 保留两位小数
        return (float) Math.round(volumePercent * 100) / 100.0f;
    }

	// xuameng 设置LRC歌词内容
    public void setLrcContent(String lrcContent) {
        mLrcContent = lrcContent;
        if (mLrcView != null) {
            mLrcView.setLrcText(mLrcContent);
        }
    }

    public void setVideoPicUrl(String picUrl) {  //xuameng 新增给vod显示旋转图片用
        this.videoPicUrl = picUrl;
    }

    public void loadVideoPic() {  //xuameng 新增给vod显示旋转图片用
        if (videoPicUrl != null && !videoPicUrl.isEmpty() && iv_circle_bg != null) {
            Picasso.get()
                   .load(videoPicUrl)
				   .resize(120,120)
                   .transform(new RoundTransformation(MD5.string2MD5(videoPicUrl))
                   .centerCorp(true)
                   .roundRadius(AutoSizeUtils.mm2px(getContext(), 50), RoundTransformation.RoundType.ALL))
                   .placeholder(R.drawable.app_logo)
                   .error(R.drawable.app_logo)
                   .into(iv_circle_bg);
        }
    }

    public DanmakuView getDanmuView() {
        return mDanmuView;
    }

}
