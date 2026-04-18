package com.github.tvbox.osc.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class AppDataManager {
    private static final int DB_FILE_VERSION = 3;
    private static final String DB_NAME = "tvbox";
    private static AppDataManager manager;   //xuameng搜索历史
    private static AppDataBase dbInstance;
    
    // 添加引用计数来跟踪活跃的数据库操作
    private static final AtomicInteger connectionRefCount = new AtomicInteger(0);
    // 添加锁来保护数据库关闭操作
    private static final ReentrantLock closeLock = new ReentrantLock();
    // 添加标志位标识数据库是否正在关闭
    private static volatile boolean isClosing = false;
    // 添加数据库状态标志
    private static volatile boolean dbClosed = false;

    private AppDataManager() {
    }

    public static void init() {
        if (manager == null) {
            synchronized (AppDataManager.class) {
                if (manager == null) {
                    manager = new AppDataManager();
                }
            }
        }
    }

    // 开始数据库操作
    public static void beginOperation() {
        connectionRefCount.incrementAndGet();
    }
    
    // 结束数据库操作
    public static void endOperation() {
        connectionRefCount.decrementAndGet();
    }
    
    // 获取活跃操作数量
    public static int getActiveOperations() {
        return connectionRefCount.get();
    }
    
    // 等待所有活跃操作完成
    public static void waitForAllOperations() throws InterruptedException {
        while (connectionRefCount.get() > 0 && !dbClosed) {
            Thread.sleep(100);  // 等待100毫秒
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vodRecordTmp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vodId` TEXT, `updateTime` INTEGER NOT NULL, `sourceKey` TEXT, `data` BLOB, `dataJson` TEXT, `testMigration` INTEGER NOT NULL)");

            database.execSQL("CREATE TABLE IF NOT EXISTS t_search (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, searchKeyWords TEXT)"); //xuameng搜索历史
            database.execSQL("CREATE INDEX IF NOT EXISTS index_t_search_searchKeyWords ON t_search (searchKeyWords)");  //xuameng搜索历史
            // Read every thing from the former Expense table
            Cursor cursor = database.query("SELECT * FROM vodRecord");

            int id;
            int vodId;
            long updateTime;
            String sourceKey;
            String dataJson;

            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex("id"));
                vodId = cursor.getInt(cursor.getColumnIndex("vodId"));
                updateTime = cursor.getLong(cursor.getColumnIndex("updateTime"));
                sourceKey = cursor.getString(cursor.getColumnIndex("sourceKey"));
                dataJson = cursor.getString(cursor.getColumnIndex("dataJson"));
                database.execSQL("INSERT INTO vodRecordTmp (id, vodId, updateTime, sourceKey, dataJson, testMigration) VALUES" +
                        " ('" + id + "', '" + vodId + "', '" + updateTime + "', '" + sourceKey + "', '" + dataJson + "',0  )");
            }

            // Delete the former table
            database.execSQL("DROP TABLE vodRecord");
            // Rename the current table to the former table name so that all other code continues to work
            database.execSQL("ALTER TABLE vodRecordTmp RENAME TO vodRecord");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE vodRecord ADD COLUMN dataJson TEXT");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE localSource ADD COLUMN type INTEGER NOT NULL DEFAULT 0");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static String dbPath() {
        return DB_NAME + ".v" + DB_FILE_VERSION + ".db";
    }

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        
        // 如果数据库正在关闭，等待
        if (isClosing) {
            synchronized (AppDataManager.class) {
                if (isClosing) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        // 如果数据库被关闭了，重新创建
        if (dbClosed || dbInstance == null || !dbInstance.isOpen()) {
            synchronized (AppDataManager.class) {
                if (dbClosed || dbInstance == null || !dbInstance.isOpen()) {
                    try {
                        dbInstance = createDatabaseInstance();
                        dbClosed = false;
                        isClosing = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 如果创建失败，记录错误但不抛异常
                        return null;
                    }
                }
            }
        }
        return dbInstance;
    }
    
    private static AppDataBase createDatabaseInstance() {
        return Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_2_3)     //xuameng搜索历史
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                    }
                }).allowMainThreadQueries()//可以在主线程操作
                .build();
    }

    public static boolean backup(File path) throws IOException {
        beginOperation();
        try {
            closeLock.lock();
            isClosing = true;
            
            // 等待所有活跃操作完成
            try {
                waitForAllOperations();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            if (dbInstance != null && dbInstance.isOpen()) {
                try {
                    dbInstance.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            File db = App.getInstance().getDatabasePath(dbPath());
            if (db.exists()) {
                FileUtils.copyFile(db, path);
                return true;
            } else {
                return false;
            }
        } finally {
            dbClosed = true;
            isClosing = false;
            closeLock.unlock();
        }
    }

    public static boolean restore(File path) throws IOException {
        beginOperation();
        try {
            closeLock.lock();
            isClosing = true;
            
            // 等待所有活跃操作完成
            try {
                waitForAllOperations();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            if (dbInstance != null && dbInstance.isOpen()) {
                try {
                    dbInstance.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            File db = App.getInstance().getDatabasePath(dbPath());
            if (db.exists()) {
                db.delete();
            }
            if (!db.getParentFile().exists())
                db.getParentFile().mkdirs();
            FileUtils.copyFile(path, db);
            
            // 恢复完成后重置数据库实例
            resetDatabase();
            return true;
        } finally {
            dbClosed = false;
            isClosing = false;
            closeLock.unlock();
        }
    }
    
    // 重置数据库连接
    public static void resetDatabase() {
        synchronized (AppDataManager.class) {
            if (dbInstance != null && dbInstance.isOpen()) {
                try {
                    dbInstance.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            dbInstance = null;
            dbClosed = true;  // 设置为关闭状态，下次get()时会重新创建
        }
    }
    
    // 安全关闭数据库
    public static void closeSafely() {
        beginOperation();
        try {
            closeLock.lock();
            isClosing = true;
            
            // 等待所有活跃操作完成
            try {
                waitForAllOperations();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            if (dbInstance != null && dbInstance.isOpen()) {
                try {
                    dbInstance.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    dbInstance = null;
                    dbClosed = true;
                }
            }
        } finally {
            isClosing = false;
            closeLock.unlock();
        }
    }
    
    // 检查数据库是否可用
    public static boolean isDatabaseAvailable() {
        return !dbClosed && dbInstance != null && dbInstance.isOpen();
    }
}
