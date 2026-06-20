/**
 * 黑胶VIP Hook - 解锁黑胶VIP特权
 *
 * 功能：
 * 1. Hook UserPrivilege.fromJson() - 修改用户权限数据，设置VIP过期时间为1年后
 * 2. Hook ThemeInfo - 修改主题价格/积分/VIP状态
 * 3. Hook ResourcePrivilege - 解除音质限制（播放/下载/无损）
 * 4. Hook SongPrivilege - 解除歌曲权限限制
 *
 * 适配高版本网易云：使用非混淆类名（ThemeInfo/UserPrivilege/ResourcePrivilege/SongPrivilege）
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.json.JSONObject

class BlackHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "BlackHook"
    }

    init {
        // 仅在黑胶VIP开关开启时初始化
        if (SettingHelper.getInstance().getSetting(SettingHelper.black_key)) {
            try {
                hookUserPrivilege()
                hookThemeInfo()
                hookResourcePrivilege()
                hookSongPrivilege()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        }
    }

    /**
     * Hook UserPrivilege.fromJson() - 修改用户权限数据
     * 设置VIP过期时间为1年后，vipCode为100/220，redVipAnnualCount为1，redVipLevel为9
     */
    private fun hookUserPrivilege() {
        val clazz = findClassIfExists("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.classLoader) ?: return
        val method = findMethodIfExists(clazz, "fromJson", JSONObject::class.java) ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val obj = chain.getArg(0) as? JSONObject
                    if (obj != null && obj.optInt("code") == 200 && !obj.isNull("data") &&
                        !obj.getJSONObject("data").isNull("userId") &&
                        obj.getJSONObject("data").optLong("userId") == ExtraHelper.getExtraDate(ExtraHelper.USER_ID).toLong()
                    ) {
                        // 修改VIP权限数据
                        val data = obj.getJSONObject("data")
                        if (!data.isNull("associator")) {
                            val associator = data.getJSONObject("associator")
                            associator.put("expireTime", System.currentTimeMillis() + 31536000000L)
                            associator.put("vipCode", 100)
                        }
                        if (!data.isNull("musicPackage")) {
                            val musicPackage = data.getJSONObject("musicPackage")
                            musicPackage.put("expireTime", System.currentTimeMillis() + 31536000000L)
                            musicPackage.put("vipCode", 220)
                        }
                        data.put("redVipAnnualCount", 1)
                        data.put("redVipLevel", 9)
                        // 使用修改后的参数执行原始方法
                        return chain.proceed(arrayOf(obj))
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookUserPrivilege 异常 - ${e.message}")
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookUserPrivilege 成功")
    }

    /**
     * Hook ThemeInfo - 修改主题价格/积分/VIP状态
     */
    private fun hookThemeInfo() {
        val clazz = findClassIfExists("com.netease.cloudmusic.theme.core.ThemeInfo", context.classLoader) ?: return

        // 优先尝试非混淆方法名
        val methods = listOf("getPoints" to 0, "getPrice" to "免费", "isVip" to false, "isDigitalAlbum" to false)
        for ((methodName, returnValue) in methods) {
            val method = findMethodIfExists(clazz, methodName) ?: continue
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? = returnValue
            })
        }
        LogUtils.i("$TAG: hookThemeInfo 成功")
    }

    /**
     * Hook ResourcePrivilege - 解除音质限制
     */
    private fun hookResourcePrivilege() {
        val clazz = findClassIfExists("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.classLoader) ?: return

        val returnConstants = listOf(
            "isVipFee" to false,
            "getPlayMaxLevel" to 999000,
            "getDownMaxLevel" to 999000,
            "getFee" to 0,
            "getPayed" to 0,
            "isFee" to false,
            "getFreeLevel" to 999000
        )
        for ((methodName, returnValue) in returnConstants) {
            val method = findMethodIfExists(clazz, methodName) ?: continue
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? = returnValue
            })
        }

        // Hook getFlag - 云盘歌曲&运算0x8不等于0时保留原值
        val getFlagMethod = findMethodIfExists(clazz, "getFlag") ?: return
        module.hook(getFlagMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed() as? Int ?: return 0
                return if ((result and 0x8) == 0) 0 else result
            }
        })
        LogUtils.i("$TAG: hookResourcePrivilege 成功")
    }

    /**
     * Hook SongPrivilege - 解除歌曲权限限制
     */
    private fun hookSongPrivilege() {
        val clazz = findClassIfExists("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.classLoader) ?: return

        val methods = listOf("canShare" to true, "getFreeLevel" to 999000)
        for ((methodName, returnValue) in methods) {
            val method = findMethodIfExists(clazz, methodName) ?: continue
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? = returnValue
            })
        }
        LogUtils.i("$TAG: hookSongPrivilege 成功")
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
