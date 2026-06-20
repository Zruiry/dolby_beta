/**
 * 登录修复Hook - 修复登录时checkToken为空导致登录失败
 *
 * 功能：Hook NeteaseMusicUtils.serialdata，在登录请求中填充checkToken
 * 当登录请求的checkToken为空时，调用网易盾watchman获取token
 *
 * 参考dev分支LoginFixHook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class LoginFixHook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "LoginFixHook"
        /** 需要填充checkToken的登录接口 */
        private val LOGIN_URLS = listOf("/api/login/cellphone", "/api/login", "/api/login/sns")
        /** 网易盾watchman类名（可能因版本不同而不同） */
        private val WATCHMAN_CLASS_NAMES = listOf(
            "com.netease.mobsecurity.rjsb.watchman",
            "com.netease.mobsec.rjsb.watchman"
        )
        /** 网易盾初始化appId */
        private const val WATCHMAN_APP_ID = "YD00000558929251"
        /** 网易盾getToken参数 */
        private const val WATCHMAN_TOKEN_KEY = "30b0cdd23ed1144a0b78de049edc09824"
        private const val WATCHMAN_TOKEN_TIMEOUT = 500
        private const val WATCHMAN_TOKEN_TYPE = 2
    }

    init {
        try {
            hookSerialdata()
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * Hook NeteaseMusicUtils.serialdata - 拦截登录请求，填充checkToken
     * 当请求URL为登录接口且checkToken为空时，调用watchman获取token并替换
     */
    private fun hookSerialdata() {
        val utilsClass = findClassIfExists("com.netease.cloudmusic.utils.NeteaseMusicUtils", context.classLoader) ?: run {
            LogUtils.w("$TAG: NeteaseMusicUtils类未找到")
            return
        }
        val serialdataMethod = findMethodIfExists(utilsClass, "serialdata", String::class.java, String::class.java) ?: run {
            LogUtils.w("$TAG: serialdata方法未找到")
            return
        }

        module.hook(serialdataMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val url = chain.getArg(0) as? String ?: return chain.proceed()
                    if (url !in LOGIN_URLS) return chain.proceed()

                    val data = chain.getArg(1) as? String ?: return chain.proceed()
                    if (!data.contains("\"checkToken\":\"\"")) return chain.proceed()

                    // 查找网易盾watchman类
                    val watchmanClass = WATCHMAN_CLASS_NAMES.firstNotNullOfOrNull { className ->
                        findClassIfExists(className, context.classLoader)
                    } ?: run {
                        LogUtils.w("$TAG: watchman类未找到，跳过checkToken填充")
                        return chain.proceed()
                    }

                    // 初始化watchman并获取token
                    val initMethod = watchmanClass.getDeclaredMethod("init", Context::class.java, String::class.java)
                    initMethod.invoke(null, context, WATCHMAN_APP_ID)

                    val getTokenMethod = watchmanClass.getDeclaredMethod(
                        "getToken", String::class.java, Integer.TYPE, Integer.TYPE
                    )
                    val checkToken = getTokenMethod.invoke(null, WATCHMAN_TOKEN_KEY, WATCHMAN_TOKEN_TIMEOUT, WATCHMAN_TOKEN_TYPE) as? String

                    if (checkToken != null) {
                        val newData = data.replace("\"checkToken\":\"\"", "\"checkToken\":\"$checkToken\"")
                        LogUtils.i("$TAG: 填充checkToken成功 - url=$url")
                        return chain.proceed(arrayOf(url, newData))
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookSerialdata 异常 - ${e.message}")
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookSerialdata 成功")
    }

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
