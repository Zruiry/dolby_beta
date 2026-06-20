/**
 * 一起听解锁Hook
 *
 * 功能：
 * 1. Hook PlayerActivity.onCreate - 在播放界面创建时解锁一起听功能
 * 2. Hook RoomInfo.getUnlockedIdentity - 返回已解锁状态
 *
 * 适配高版本网易云：使用非混淆类名
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class ListentogetherHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "ListentogetherHook"
    }

    init {
        // 仅在一起听解锁开关开启时初始化
        if (SettingHelper.getInstance().getSetting(SettingHelper.listen_key)) {
            try {
                hookPlayerActivityOnCreate()
                hookRoomInfoGetUnlockedIdentity()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        }
    }

    /**
     * Hook PlayerActivity.onCreate - 在播放界面创建时写入解锁状态
     */
    private fun hookPlayerActivityOnCreate() {
        val playerActivityClass = findClassIfExists("com.netease.cloudmusic.activity.PlayerActivity", context.classLoader) ?: return
        val onCreateMethod = findMethodIfExists(playerActivityClass, "onCreate", android.os.Bundle::class.java) ?: return

        module.hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    // 写入解锁状态到SharedPreferences
                    val listening = context.getSharedPreferences("LISTEN_TOGETHER", Context.MODE_MULTI_PROCESS)
                    listening.edit().putBoolean("match_unlock_status" + ExtraHelper.USER_ID, true).apply()
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookPlayerActivityOnCreate 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookPlayerActivityOnCreate 成功")
    }

    /**
     * Hook RoomInfo.getUnlockedIdentity - 返回已解锁状态
     * 在init中一次性注册，避免在onCreate回调中重复注册导致hook累积
     */
    private fun hookRoomInfoGetUnlockedIdentity() {
        val roomInfoClass = findClassIfExists("com.netease.cloudmusic.module.listentogether.meta.RoomInfo", context.classLoader)
            ?: run {
                LogUtils.w("$TAG: RoomInfo类未找到，跳过getUnlockedIdentity hook")
                return
            }
        val getUnlockedIdentityMethod = findMethodIfExists(roomInfoClass, "getUnlockedIdentity")
            ?: run {
                LogUtils.w("$TAG: getUnlockedIdentity方法未找到")
                return
            }

        module.hook(getUnlockedIdentityMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = true
        })
        LogUtils.i("$TAG: hookRoomInfoGetUnlockedIdentity 成功")
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
