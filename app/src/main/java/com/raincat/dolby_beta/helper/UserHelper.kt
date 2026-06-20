/**
 * 用户状态帮助类 - 通过Cookie获取用户信息
 *
 * 功能：从网易云API获取用户ID，保存到本地数据库
 * 依赖：ExtraHelper.COOKIE 必须已通过UserProfileHook获取
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.helper

import com.raincat.dolby_beta.net.Http
import com.raincat.dolby_beta.utils.LogUtils
import org.json.JSONObject

object UserHelper {

    private const val TAG = "UserHelper"

    /**
     * 通过cookie获取用户信息
     * 请求 https://music.163.com/api/nuser/account/get，解析返回的JSON获取用户ID
     */
    @JvmStatic
    fun getUserInfo() {
        try {
            val headers = HashMap<String, Any>()
            headers["cookie"] = ExtraHelper.getExtraDate(ExtraHelper.COOKIE)
            val userInfo = Http("GET", "https://music.163.com/api/nuser/account/get", headers, "").getResult()
            val json = JSONObject(userInfo)
            val profile = json.optJSONObject("profile")
            if (profile != null) {
                val userId = profile.optLong("userId")
                if (userId > 0) {
                    ExtraHelper.setExtraDate(ExtraHelper.USER_ID, userId.toString())
                    LogUtils.i("$TAG: 获取用户ID成功 - $userId")
                }
            }
        } catch (e: Exception) {
            LogUtils.e("$TAG: 获取用户信息失败 - ${e.message}")
        }
    }
}
