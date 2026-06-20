/**
 * 额外信息数据库帮助类 - SQLiteOpenHelper单例实现
 *
 */
package com.raincat.dolby_beta.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 额外信息数据库帮助类
 * 数据库名称格式：Extra_{version}.db
 */
class ExtraDbOpenHelper private constructor(
    context: Context
) : SQLiteOpenHelper(context, getUserDatabaseName(), null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1

        /** 建表SQL */
        private val EXTRA_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
                ExtraDao.TABLE_NAME + " (" +
                ExtraDao.EXTRA_KEY + " VARCHAR(20) PRIMARY KEY, " +
                ExtraDao.EXTRA_VALUE + " TEXT ); "

        @Volatile
        private var instance: ExtraDbOpenHelper? = null

        @JvmStatic
        fun getInstance(context: Context): ExtraDbOpenHelper {
            return instance ?: synchronized(this) {
                instance ?: ExtraDbOpenHelper(context).also { instance = it }
            }
        }

        private fun getUserDatabaseName(): String = "Extra_${DATABASE_VERSION}.db"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(EXTRA_TABLE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 无升级逻辑
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 无降级逻辑
    }

    override fun close() {
        instance?.let {
            try {
                it.writableDatabase.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            instance = null
        }
    }
}
