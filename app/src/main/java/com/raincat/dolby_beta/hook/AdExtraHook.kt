/**
 * 广告移除增强Hook - 替换广告JSON数据为空对象
 *
 * 功能：Hook广告方法，将JSONObject参数替换为空对象，使广告数据失效
 * 仅在黑胶VIP开关开启时生效
 *
 * 参考dev分支AdExtraHook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.json.JSONObject

class AdExtraHook(
    private val module: XposedModule,
    private val context: android.content.Context
) {
    companion object {
        private const val TAG = "AdExtraHook"
    }

    init {
        if (SettingHelper.getInstance().isEnable(SettingHelper.black_key)) {
            try {
                hookAdMethod()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        }
    }

    /**
     * Hook广告方法 - 将JSONObject参数替换为空对象
     * 遍历方法参数，找到JSONObject类型的参数，替换为空对象
     */
    private fun hookAdMethod() {
        val methods = ClassHelper.Ad.getAdMethod(context) ?: run {
            LogUtils.w("$TAG: 未找到广告方法")
            return
        }

        for (method in methods) {
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    try {
                        val args = chain.args
                        for (i in args.indices) {
                            if (args[i] is JSONObject) {
                                args[i] = JSONObject()
                                LogUtils.i("$TAG: 替换广告JSON - method=${method.name}")
                                return chain.proceed(args.toTypedArray())
                            }
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("$TAG: hookAdMethod 异常 - ${e.message}")
                    }
                    return chain.proceed()
                }
            })
        }
        LogUtils.i("$TAG: hookAdMethod 成功 - ${methods.size}个方法")
    }
}
