package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;

/* @author xuameng
 * @since 2026/9/15
 * 历史列表专用轻量 POJO，不映射到表
 */

public class VodRecordSummary {
    public int id;
    public String vodId;
    public long updateTime;
    public String sourceKey;
    public String vodName;
    public String vodPic;
    public String playNote;
}
