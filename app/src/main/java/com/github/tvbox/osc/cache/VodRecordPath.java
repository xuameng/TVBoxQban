package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;

public class VodRecordPath {
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "dataJsonPath")
    public String dataJsonPath;
}