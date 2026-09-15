package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;

/**
 * 历史列表专用轻量 POJO，不映射到表
 */
public class VodRecordSummary {
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "vodId")
    public String vodId;

    @ColumnInfo(name = "updateTime")
    public long updateTime;

    @ColumnInfo(name = "sourceKey")
    public String sourceKey;

    @ColumnInfo(name = "vodName")
    public String vodName;

    @ColumnInfo(name = "vodPic")
    public String vodPic;

    @ColumnInfo(name = "playNote")
    public String playNote;
}
