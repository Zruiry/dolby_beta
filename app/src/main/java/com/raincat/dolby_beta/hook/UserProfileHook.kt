/**
 * 用户资料Hook - 获取用户ID、Cookie和我喜欢的歌单ID
 *
 * 功能：
 * 1. Hook Profile.setNickname - 获取用户ID
 * 2. Hook MainActivity.onResume - 获取Cookie和用户信息
 * 3. Hook LoginActivity.onCreate - 清除用户数据
 * 4. Hook PlayList.setSpecialType - 获取喜欢的歌单ID
 *
 * 参考dev分支UserProfileHook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.os.Bundle
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.UserHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class UserProfileHook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "UserProfileHook"
    }

    init {
        try {
            hookProfileSetNickname()
            hookMainActivityOnResume()
            hookLoginActivityOnCreate()
            hookPlayListSetSpecialType()
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * Hook Profile.setNickname - 获取用户ID
     * 当设置昵称为非"未登录"且非空时，如果当前是登录用户且USER_ID未设置，则保存用户ID
     */
    private fun hookProfileSetNickname() {
        val profileClass = findClassIfExists("com.netease.cloudmusic.meta.Profile", context.classLoader) ?: return
        val setNicknameMethod = findMethodIfExists(profileClass, "setNickname", String::class.java) ?: return

        module.hook(setNicknameMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val nickName = chain.getArg(0) as? String ?: return result
                    if (nickName == "未登录" || nickName.isEmpty()) return result

                    val isMe = profileClass.getDeclaredMethod("isMe").invoke(chain.thisObject) as? Boolean ?: false
                    if (isMe && ExtraHelper.getExtraDate(ExtraHelper.USER_ID) == "-1") {
                        val userId = profileClass.getDeclaredMethod("getUserId").invoke(chain.thisObject)?.toString()
                        if (userId != null) {
                            ExtraHelper.setExtraDate(ExtraHelper.USER_ID, userId)
                            LogUtils.i("$TAG: 获取用户ID - $userId")
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookProfileSetNickname 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookProfileSetNickname 成功")
    }

    /**
     * Hook MainActivity.onResume - 获取Cookie和用户信息
     * 在后台线程中获取Cookie和用户信息，避免阻塞UI
     */
    private fun hookMainActivityOnResume() {
        val mainActivityClass = findClassIfExists("com.netease.cloudmusic.activity.MainActivity", context.classLoader) ?: return
        val onResumeMethod = findMethodIfExists(mainActivityClass, "onResume") ?: return

        module.hook(onResumeMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    Thread {
                        if (ExtraHelper.getExtraDate(ExtraHelper.COOKIE) == "-1") {
                            val cookie = ClassHelper.Cookie.getCookie(context)
                            ExtraHelper.setExtraDate(ExtraHelper.COOKIE, cookie)
                            LogUtils.i("$TAG: 获取Cookie成功")
                        }
                        if (ExtraHelper.getExtraDate(ExtraHelper.USER_ID) == "-1") {
                            UserHelper.getUserInfo()
                        }
                    }.start()
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookMainActivityOnResume 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookMainActivityOnResume 成功")
    }

    /**
     * Hook LoginActivity.onCreate - 清除用户数据
     * 登录页创建时清除旧用户数据，确保切换账号时数据不混淆
     */
    private fun hookLoginActivityOnCreate() {
        val loginActivityClass = findClassIfExists("com.netease.cloudmusic.activity.LoginActivity", context.classLoader) ?: return
        val onCreateMethod = findMethodIfExists(loginActivityClass, "onCreate", Bundle::class.java) ?: return

        module.hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    ExtraHelper.cleanUserData()
                    LogUtils.i("$TAG: 清除用户数据")
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookLoginActivityOnCreate 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookLoginActivityOnCreate 成功")
    }

    /**
     * Hook PlayList.setSpecialType - 获取喜欢的歌单ID
     * 当歌单类型为5（我喜欢的音乐）且LOVE_PLAY_LIST未设置时，保存歌单ID
     */
    private fun hookPlayListSetSpecialType() {
        val playListClass = findClassIfExists("com.netease.cloudmusic.meta.PlayList", context.classLoader) ?: return
        val setSpecialTypeMethod = findMethodIfExists(playListClass, "setSpecialType", Integer.TYPE) ?: return

        module.hook(setSpecialTypeMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val specialType = chain.getArg(0) as? Int ?: return result
                    if (specialType == 5 && ExtraHelper.getExtraDate(ExtraHelper.LOVE_PLAY_LIST) == "-1") {
                        val playListId = playListClass.getDeclaredMethod("getId").invoke(chain.thisObject)?.toString()
                        if (playListId != null) {
                            ExtraHelper.setExtraDate(ExtraHelper.LOVE_PLAY_LIST, playListId)
                            LogUtils.i("$TAG: 获取喜欢的歌单ID - $playListId")
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookPlayListSetSpecialType 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookPlayListSetSpecialType 成功")
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
