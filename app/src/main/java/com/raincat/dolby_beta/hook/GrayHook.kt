/**
 * 不变灰Hook - Hook MusicInfo.hasCopyRight() 返回 true，使无版权歌曲显示为可播放状态
 *
 * 参考dev分支GrayHook实现：
 * - 仅当proxy_gray_key开关开启时执行
 * - Hook com.netease.cloudmusic.meta.MusicInfo 的 hasCopyRight() 方法，返回 true
 *
 * 注意：仅影响显示效果，与是否能播放无关，会导致无音源歌曲无法播放且无法自动跳过
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class GrayHook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "GrayHook"
    }

    init {
        hookGrayFunction()
    }

    /**
     * Hook MusicInfo.hasCopyRight() 返回 true，使无版权歌曲不变灰
     */
    private fun hookGrayFunction() {
        // 仅当不变灰开关开启时执行
        if (!SettingHelper.getInstance().getSetting(SettingHelper.proxy_gray_key)) {
            LogUtils.i("$TAG: 未启用不变灰功能")
            return
        }

        try {
            val musicInfoClass = findClassIfExists(
                "com.netease.cloudmusic.meta.MusicInfo", context.classLoader
            )
            if (musicInfoClass == null) {
                LogUtils.w("$TAG: MusicInfo类未找到")
                return
            }

            // 查找 hasCopyRight() 方法
            val method = findMethodIfExists(musicInfoClass, "hasCopyRight")
            if (method == null) {
                LogUtils.w("$TAG: hasCopyRight方法未找到")
                return
            }

            // Hook hasCopyRight() 返回 true，使无版权歌曲不变灰
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? = true
            })
            LogUtils.i("$TAG: 成功hook MusicInfo.hasCopyRight()，返回true")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * 查找类（兼容类不存在的情况）
     */
    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    /**
     * 查找方法（兼容方法不存在的情况）
     */
    private fun findMethodIfExists(clazz: Class<*>, methodName: String): java.lang.reflect.Method? {
        return try {
            clazz.getDeclaredMethod(methodName)
        } catch (e: NoSuchMethodException) {
            // 尝试从父类查找
            try {
                clazz.getMethod(methodName)
            } catch (e2: NoSuchMethodException) {
                null
            }
        }
    }
}
