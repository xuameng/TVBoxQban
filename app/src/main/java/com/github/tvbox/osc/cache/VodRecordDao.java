package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VodRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(VodRecord record);

    // 旧方法保留（详情页还在用）
    @Query("select * from vodRecord order by updateTime desc limit :size")
    List<VodRecord> getAll(int size);

    @Query("select * from vodRecord where `sourceKey`=:sourceKey and `vodId`=:vodId")
    VodRecord getVodRecord(String sourceKey, String vodId);

    @Delete
    int delete(VodRecord record);

    @Query("select count(*) from vodRecord")
    int getCount();

    @Query("DELETE FROM vodRecord")
    void deleteAll();

    @Query("DELETE FROM vodRecord where id NOT IN (SELECT id FROM vodRecord ORDER BY updateTime desc LIMIT :size)")
    int reserver(int size);

    // ✅ 历史列表专用：只查轻量字段，不碰 dataJson
    @Query("""
        SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote
        FROM vodRecord
        ORDER BY updateTime DESC
        LIMIT :size
    """)
    List<VodRecordSummary> getHistorySummary(int size);

    @Query("DELETE FROM vodRecord WHERE sourceKey = :sourceKey")
    void deleteBySourceKey(String sourceKey);
}
