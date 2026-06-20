/**
 * 额外信息数据访问对象 - 管理键值对形式的额外数据存储
 *
 */
package com.raincat.dolby_beta.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * 额外信息DAO - 提供键值对的CRUD操作
 */
class ExtraDao private constructor(context: Context) {

    companion object {
        const val TABLE_NAME = "extra"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_VALUE = "extra_value"

        @Volatile
        private var dao: ExtraDao? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): ExtraDao = dao ?: throw IllegalStateException("ExtraDao未初始化，请先调用init()")

        @JvmStatic
        fun init(context: Context) {
            if (dao == null) {
                dao = ExtraDao(context)
            }
        }
    }

    private val dbHelper: ExtraDbOpenHelper = ExtraDbOpenHelper.getInstance(context)

    /**
     * 保存额外记录（存在则替换）
     */
    @Synchronized
    fun saveExtra(key: String, value: String) {
        val db = dbHelper.writableDatabase
        if (db.isOpen) {
            val values = ContentValues().apply {
                put(EXTRA_KEY, key)
                put(EXTRA_VALUE, value)
            }
            db.replace(TABLE_NAME, null, values)
        }
        db.close()
    }

    /**
     * 获取某个额外记录
     */
    @Synchronized
    fun getExtra(key: String): String {
        var extra = "-1"
        val db = dbHelper.readableDatabase
        if (db.isOpen) {
            val cursor = db.rawQuery(
                "select * from $TABLE_NAME where $EXTRA_KEY = '$key'", null
            )
            if (cursor.moveToNext()) {
                extra = cursor.getString(cursor.getColumnIndex(EXTRA_VALUE))
            }
            cursor.close()
        }
        db.close()
        return extra
    }

    /**
     * 删除某条额外记录
     */
    @Synchronized
    fun deleteExtra(key: String) {
        val db = dbHelper.writableDatabase
        if (db.isOpen) {
            db.delete(TABLE_NAME, "$EXTRA_KEY = ? ", arrayOf(key))
        }
        db.close()
    }

    /**
     * 删除所有额外记录
     */
    @Synchronized
    fun deleteAllExtra() {
        val db = dbHelper.writableDatabase
        if (db.isOpen) {
            db.delete(TABLE_NAME, null, null)
        }
        db.close()
    }
}
