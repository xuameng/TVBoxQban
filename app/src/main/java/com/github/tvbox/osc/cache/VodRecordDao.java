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

    // 详情页用（单行，安全）
    @Query("SELECT * FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId")
    VodRecord getVodRecord(String sourceKey, String vodId);

    // 只查 id，不读 dataJson
    @Query("SELECT id FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
    Integer getVodRecordId(String sourceKey, String vodId);

    // 历史列表用，不碰 dataJson
    @Query("SELECT id, vodId, updateTime, sourceKey, vodName, vodPic, playNote FROM vodRecord ORDER BY updateTime DESC LIMIT :size")
    List<VodRecordSummary> getHistorySummary(int size);

// ✅ 只查 id + 文件路径，不碰 dataJson 大字段
@Query("SELECT id, dataJsonPath FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId LIMIT 1")
VodRecord getVodRecordPath(String sourceKey, String vodId);

// ✅ 迁移用：查旧的大字段
@Query("SELECT id, dataJson FROM vodRecord WHERE dataJson IS NOT NULL AND dataJson != ''")
List<VodRecord> getRecordsWithDataJson();

    @Query("SELECT count(*) FROM vodRecord")
    int getCount();

    @Query("DELETE FROM vodRecord")
    void deleteAll();

    @Query("DELETE FROM vodRecord WHERE id NOT IN (SELECT id FROM vodRecord ORDER BY updateTime DESC LIMIT :size)")
    int reserver(int size);

    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey")
    void deleteBySourceKey(String sourceKey);

    // 替代 @Delete，不读 dataJson
    @Query("DELETE FROM vodRecord WHERE `sourceKey`=:sourceKey AND `vodId`=:vodId")
    void deleteBySourceAndVodId(String sourceKey, String vodId);
}
