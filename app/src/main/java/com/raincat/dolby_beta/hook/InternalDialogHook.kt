/**
 * 内测与听歌识别弹窗Hook - 去掉内测邀请和听歌识别弹窗
 *
 * 功能：Hook MaterialDialogHelper.materialDialog，在MainActivity和IdentifyActivity中拦截弹窗
 * 仅在versionCode >= 138时生效
 *
 * 参考dev分支InternalDialogHook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class InternalDialogHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "InternalDialogHook"
    }

    init {
        if (versionCode >= 138) {
            try {
                hookMaterialDialog()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        } else {
            LogUtils.i("$TAG: versionCode=$versionCode < 138，跳过")
        }
    }

    /**
     * Hook MaterialDialogHelper.materialDialog - 拦截内测和听歌识别弹窗
     * 当调用者类名包含MainActivity或IdentifyActivity时，直接返回null
     */
    private fun hookMaterialDialog() {
        val dialogHelperClass = findClassIfExists(
            "com.netease.cloudmusic.ui.MaterialDiloagCommon\$MaterialDialogHelper",
            context.classLoader
        ) ?: run {
            LogUtils.w("$TAG: MaterialDialogHelper类未找到")
            return
        }

        val materialDialogMethods = dialogHelperClass.declaredMethods.filter { it.name == "materialDialog" }
        for (method in materialDialogMethods) {
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    try {
                        val arg0 = chain.getArg(0)
                        val callerName = arg0?.javaClass?.name ?: ""
                        if (callerName.contains("MainActivity") || callerName.contains("IdentifyActivity")) {
                            LogUtils.i("$TAG: 拦截弹窗 - caller=$callerName")
                            return null
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("$TAG: hookMaterialDialog 异常 - ${e.message}")
                    }
                    return chain.proceed()
                }
            })
        }
        LogUtils.i("$TAG: hookMaterialDialog 成功")
    }

    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}
