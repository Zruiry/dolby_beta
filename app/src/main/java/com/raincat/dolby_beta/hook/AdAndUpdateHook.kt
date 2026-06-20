/**
 * 去广告和升级提示Hook
 *
 * 功能：
 * 1. Hook OkHttpClient.newCall - 拦截广告和升级相关请求
 * 2. Hook LoadingAdActivity.onCreate - 直接关闭开屏广告Activity
 *
 * 适配高版本网易云：使用非混淆类名（okhttp3.OkHttpClient）
 */
package com.raincat.dolby_beta.hook

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class AdAndUpdateHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "AdAndUpdateHook"
    }

    init {
        try {
            hookAdAndUpdate()
            hookLoadingAd()
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * Hook OkHttpClient.newCall - 拦截广告和升级相关请求
     * 通过修改请求URL为无效地址，使广告/升级请求失败
     */
    private fun hookAdAndUpdate() {
        val okHttpClientClass = findClassIfExists("okhttp3.OkHttpClient", context.classLoader) ?: return
        val newCallMethods = okHttpClientClass.declaredMethods.filter { it.name == "newCall" }

        for (method in newCallMethods) {
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    try {
                        val request = chain.getArg(0) ?: return chain.proceed()
                        // 获取请求URL
                        val urlField = request.javaClass.getDeclaredField("url")
                        urlField.isAccessible = true
                        val urlObj = urlField.get(request) ?: return chain.proceed()
                        val urlStr = urlObj.toString()

                        // 判断是否需要拦截
                        val needBlock = urlStr.contains("appcustomconfig/get") ||
                                (SettingHelper.getInstance().getSetting(SettingHelper.black_key) &&
                                        !urlStr.contains("music.126.net") &&
                                        (urlStr.contains("resource-exposure/config") ||
                                                urlStr.contains("api/ad") ||
                                                urlStr.endsWith(".jpg") ||
                                                urlStr.endsWith(".mp4") ||
                                                urlStr.contains("ad/get") ||
                                                urlStr.contains("ad/loading"))) ||
                                (SettingHelper.getInstance().getSetting(SettingHelper.update_key) &&
                                        (urlStr.contains("android/version") ||
                                                urlStr.contains("android/upgrade")))

                        if (needBlock) {
                            // 修改URL为无效地址
                            val urlField2 = urlObj.javaClass.getDeclaredField("url")
                            val urlAccessible = urlField2.isAccessible
                            urlField2.isAccessible = true
                            urlField2.set(urlObj, "https://999.0.0.1/")
                            urlField2.isAccessible = urlAccessible
                            // 使用修改后的参数执行原始方法
                            return chain.proceed(arrayOf(request))
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("$TAG: hookAdAndUpdate 异常 - ${e.message}")
                    }
                    return chain.proceed()
                }
            })
        }
        LogUtils.i("$TAG: hookAdAndUpdate 成功")
    }

    /**
     * Hook LoadingAdActivity.onCreate - 直接关闭开屏广告Activity
     */
    private fun hookLoadingAd() {
        if (!SettingHelper.getInstance().getSetting(SettingHelper.black_key)) return
        val clazz = findClassIfExists("com.netease.cloudmusic.activity.LoadingAdActivity", context.classLoader) ?: return
        val method = findMethodIfExists(clazz, "onCreate", Bundle::class.java) ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                chain.proceed()
                (chain.thisObject as? Activity)?.finish()
                return null
            }
        })
        LogUtils.i("$TAG: hookLoadingAd 成功")
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
