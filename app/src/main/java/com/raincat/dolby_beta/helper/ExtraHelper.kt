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
    /** 用户ID（用于签到等场景标识当前登录用户） */
    const val USER_ID = "user_id"
    /** 用户Cookie（用于自动签到等需要鉴权的请求） */
    const val COOKIE = "cookie"
    /** 上次签到时间（后缀用户ID） */
    const val SIGN_TIME = "sign_time_"
    /** 上次歌曲打卡时间（后缀用户ID） */
    const val SIGN_SONG_TIME = "sign_song_time_"
    /** 我喜欢的音乐歌单ID */
    const val LOVE_PLAY_LIST = "play_list"

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

    /**
     * 清除当前用户的数据（登录页创建时调用）
     */
    @JvmStatic
    fun cleanUserData() {
        setExtraDate(COOKIE, "-1")
        setExtraDate(USER_ID, "-1")
        setExtraDate(LOVE_PLAY_LIST, "-1")
        setExtraDate(SIGN_TIME, "-1")
        setExtraDate(SIGN_SONG_TIME, "-1")
    }
}
