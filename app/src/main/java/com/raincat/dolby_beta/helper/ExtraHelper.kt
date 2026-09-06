/**
 * 额外数据帮助类 - 仅保留音源代理相关数据
 *
 */
package com.raincat.dolby_beta.helper

import android.content.Context
import com.raincat.dolby_beta.db.ExtraDao

object ExtraHelper {
    /** 脚本运行状态：0=启动中，1=已启动 */
    const val SCRIPT_STATUS = "script_status"
    /** APP版本号 */
    const val APP_VERSION = "app_version"

    /**
     * 初始化数据库
     */
    @JvmStatic
    fun init(context: Context) {
        ExtraDao.init(context)
    }

    @JvmStatic
    fun getExtraDate(key: String): String {
        return ExtraDao.getInstance().getExtra(key)
    }

    @JvmStatic
    fun setExtraDate(key: String, value: Any) {
        ExtraDao.getInstance().saveExtra(key, value.toString())
    }
}
