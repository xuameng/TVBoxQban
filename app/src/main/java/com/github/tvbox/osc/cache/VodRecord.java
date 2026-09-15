package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，不碰 dataJson
 */

@Entity(tableName = "vodRecord")
public class VodRecord implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "vodId")
    public String vodId;

    @ColumnInfo(name = "updateTime")
    public long updateTime;

    @ColumnInfo(name = "sourceKey")
    public String sourceKey;

    // 列表用轻量字段
    @ColumnInfo(name = "vodName")
    public String vodName;

    @ColumnInfo(name = "vodPic")
    public String vodPic;

    @ColumnInfo(name = "playNote")
    public String playNote;

    // 大字段，详情页按需读取
    public String dataJson;

    // 大字段存文件，这里只存路径
    @ColumnInfo(name = "dataJsonPath")
    public String dataJsonPath;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
