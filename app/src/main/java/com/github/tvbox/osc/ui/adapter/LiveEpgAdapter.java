package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.tv.widget.AudioWaveView;
import com.github.tvbox.osc.bean.Epginfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LiveEpgAdapter extends BaseQuickAdapter<Epginfo, BaseViewHolder> {
    private int selectedEpgIndex = -1;
    private int focusedEpgIndex = -1;
    public static float fontSize = 20;
    private int defaultShiyiSelection = 0;
    private boolean ShiyiSelection = false;
    private String shiyiDate = null;
    private String currentEpgDate = null;
    private int focusSelection = -1;
    private boolean source_include_back = false;

    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
    public LiveEpgAdapter() {
        super(R.layout.epglist_item, new ArrayList<>());
    }

    public void CanBack( Boolean source_include_back){
        this.source_include_back = source_include_back;
    }

    @Override
    protected void convert(BaseViewHolder holder, Epginfo value) {
        TextView textview = holder.getView(R.id.tv_epg_name);
        TextView timeview = holder.getView(R.id.tv_epg_time);
        TextView shiyi = holder.getView(R.id.shiyi);
        AudioWaveView wqddg_AudioWaveView = holder.getView(R.id.wqddg_AudioWaveView);
        wqddg_AudioWaveView.setVisibility(View.GONE);
        if (value.index == selectedEpgIndex && value.index != focusedEpgIndex && (value.currentEpgDate.equals(shiyiDate) || value.currentEpgDate.equals(timeFormat.format(new Date())))) {
            textview.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
            timeview.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
        }else {
            textview.setTextColor(Color.WHITE);
            timeview.setTextColor(Color.WHITE);
        }
        if (new Date().compareTo(value.startdateTime) >= 0 && new Date().compareTo(value.enddateTime) <= 0) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(Color.YELLOW);
            shiyi.setText("直播中");
            shiyi.setTextColor(Color.RED);
        } else if (new Date().compareTo(value.startdateTime) < 0) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(Color.rgb(8, 157, 1));
            shiyi.setTextColor(Color.WHITE);
            shiyi.setText("预告");
        } else if (new Date().compareTo(value.enddateTime) > 0 && source_include_back ) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(Color.rgb(80, 80, 80));
            shiyi.setTextColor(Color.WHITE);
            shiyi.setText("回看");
        } else {
            shiyi.setVisibility(View.GONE);
        }
        textview.setText(value.title);
        timeview.setText(value.start + "--" + value.end);
        Log.e("roinlong", "getView: " + selectedEpgIndex);
        if (ShiyiSelection == false) {
            Date now = new Date();
            if (now.compareTo(value.startdateTime) >= 0 && now.compareTo(value.enddateTime) <= 0) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
            } else {
                wqddg_AudioWaveView.setVisibility(View.GONE);
            }
        } else {
            if (value.index == this.selectedEpgIndex && value.currentEpgDate.equals(shiyiDate)) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
                shiyi.setText("回看中");
                shiyi.setTextColor(Color.RED);
                shiyi.setBackgroundColor(Color.YELLOW);
                if (new Date().compareTo(value.startdateTime) >= 0 && new Date().compareTo(value.enddateTime) <= 0) {
                    shiyi.setVisibility(View.VISIBLE);
                    shiyi.setBackgroundColor(Color.YELLOW);
                    shiyi.setText("直播中");
                    shiyi.setTextColor(Color.RED);
                }
            } else {
                wqddg_AudioWaveView.setVisibility(View.GONE);
            }
        }

    }
    public void setShiyiSelection(int i, boolean t, String currentEpgDate) {
        this.selectedEpgIndex = i;
        this.shiyiDate = t ? currentEpgDate : null;
        ShiyiSelection = t;
        notifyItemChanged(this.selectedEpgIndex);

    }
    public int getSelectedIndex() {
        return selectedEpgIndex;
    }

    public void setSelectedEpgIndex(int selectedEpgIndex) {
        if (selectedEpgIndex == this.selectedEpgIndex) return;
        this.selectedEpgIndex = selectedEpgIndex;
        if (this.selectedEpgIndex != -1)
            notifyItemChanged(this.selectedEpgIndex);
    }


    public int getFocusedEpgIndex() {
        return focusedEpgIndex;
    }

    public void setFocusedEpgIndex(int focusedEpgIndex) {   //xuamengEPG选中时白色，不选中时蓝色
      int prefocusedEpgIndex = this.focusedEpgIndex;   
      this.focusedEpgIndex = focusedEpgIndex;
          if (prefocusedEpgIndex != -1)
            notifyItemChanged(prefocusedEpgIndex);   
          if (this.focusedEpgIndex != -1)
            notifyItemChanged(this.focusedEpgIndex);
    }
}

