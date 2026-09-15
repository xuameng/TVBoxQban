package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * @author xuameng
 * @since 2026/9/15
 * 历史列表专用：只查轻量字段，不碰 dataJson
 */

@Dao
public interface VodRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(VodRecord record);

    @Query("SELECT id FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    Integer getVodRecordId(String sourceKey, String vodId);

    @Query("SELECT id, dataJsonPath FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    VodRecordPath getVodRecordPath(String sourceKey, String vodId);

    // ===== 新增：带播放状态的摘要查询 =====
    @Query("SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote, currentPlayFlag, currentPlayIndex, playerCfg, reverseSort FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    VodRecordSummary getVodRecordSummary(String sourceKey, String vodId);
    // ===== 新增结束 =====

    @Query("SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote FROM vodRecord ORDER BY updateTime DESC LIMIT :size")
    List<VodRecordSummary> getHistorySummary(int size);

    @Query("SELECT count(*) FROM vodRecord")
    int getCount();

    @Query("DELETE FROM vodRecord")
    void deleteAll();

    @Query("DELETE FROM vodRecord WHERE id NOT IN (SELECT id FROM vodRecord ORDER BY updateTime DESC LIMIT :size)")
    int reserver(int size);

    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey")
    void deleteBySourceKey(String sourceKey);

    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId")
    void deleteBySourceAndVodId(String sourceKey, String vodId);
}
