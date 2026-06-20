/**
 * 自动签到Hook
 *
 * 功能：
 * 1. Hook MainActivity.onStart - 每天首次启动时自动签到（云贝签到 + 每日歌曲打卡）
 * 2. Hook Profile.isMobileSign - 修改签到状态为已签到
 *
 * 适配高版本网易云：使用非混淆类名
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.helper.SignSongHelper
import com.raincat.dolby_beta.net.Http
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class AutoSignInHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "AutoSignInHook"
    }

    init {
        // 当自动签到或每日歌曲打卡任一开启时初始化
        if (SettingHelper.getInstance().getSetting(SettingHelper.sign_key) ||
            SettingHelper.getInstance().getSetting(SettingHelper.sign_song_key)) {
            try {
                hookMainActivityOnStart()
                hookProfileIsMobileSign()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        }
    }

    /**
     * Hook MainActivity.onStart - 每天首次启动时自动签到
     * 包含云贝签到和每日歌曲打卡两种功能
     */
    private fun hookMainActivityOnStart() {
        val clazz = findClassIfExists("com.netease.cloudmusic.activity.MainActivity", context.classLoader) ?: return
        val method = findMethodIfExists(clazz, "onStart") ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val userId = ExtraHelper.getExtraDate(ExtraHelper.USER_ID)
                    val cookie = ExtraHelper.getExtraDate(ExtraHelper.COOKIE)
                    if (userId == "-1" || cookie == "-1") {
                        return result
                    }
                    val c = chain.thisObject as Context

                    // 自动签到（云贝签到）
                    if (SettingHelper.getInstance().getSetting(SettingHelper.sign_key)) {
                        val lastSignInTime = ExtraHelper.getExtraDate(ExtraHelper.SIGN_TIME + userId).toLongOrNull() ?: 0L
                        if (lastSignInTime < Tools.getTodayStartTime()) {
                            sign(c, cookie)
                            ExtraHelper.setExtraDate(ExtraHelper.SIGN_TIME + userId, System.currentTimeMillis().toString())
                        }
                    }

                    // 每日歌曲打卡
                    if (SettingHelper.getInstance().getSetting(SettingHelper.sign_song_key)) {
                        val lastSignSongTime = ExtraHelper.getExtraDate(ExtraHelper.SIGN_SONG_TIME + userId).toLongOrNull() ?: 0L
                        if (lastSignSongTime < Tools.getTodayStartTime()) {
                            SignSongHelper.showSignStatusDialog(c, SettingHelper.sign_song_title, null)
                            ExtraHelper.setExtraDate(ExtraHelper.SIGN_SONG_TIME + userId, System.currentTimeMillis().toString())
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookMainActivityOnStart 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookMainActivityOnStart 成功")
    }

    /**
     * Hook Profile.isMobileSign - 修改签到状态为已签到
     */
    private fun hookProfileIsMobileSign() {
        val clazz = findClassIfExists("com.netease.cloudmusic.meta.Profile", context.classLoader) ?: return
        val method = findMethodIfExists(clazz, "isMobileSign") ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = true
        })
        LogUtils.i("$TAG: hookProfileIsMobileSign 成功")
    }

    /**
     * 执行签到
     * 调用网易云API进行每日签到
     */
    private fun sign(context: Context, cookie: String) {
        try {
            val header = HashMap<String, Any>()
            header["Cookie"] = cookie

            val param = HashMap<String, Any>()
            param["type"] = "1"
            Http("POST", "http://music.163.com/api/point/dailyTask", param, header).getResult()

            param["type"] = "0"
            val result = Http("POST", "http://music.163.com/api/point/dailyTask", param, header).getResult()
            if (result.contains("200") && !result.contains("msg")) {
                Tools.showToastOnLooper(context, "自动签到成功")
            }
        } catch (e: Throwable) {
            LogUtils.e("$TAG: sign 异常 - ${e.message}")
        }
    }

    // ==================== 辅助方法 ====================

    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private fun findMethodIfExists(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
        return try {
            if (paramTypes.isEmpty()) {
                clazz.declaredMethods.firstOrNull { it.name == methodName }
            } else {
                clazz.getDeclaredMethod(methodName, *paramTypes)
            }
        } catch (e: NoSuchMethodException) {
            null
        }
    }
}
