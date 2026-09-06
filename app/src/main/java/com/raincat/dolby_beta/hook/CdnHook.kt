/**
 * 绕过CDN责任链拦截器检测
 * 使用Modern libxposed API 102（Hooker拦截器链）
 *
 */
package com.raincat.dolby_beta.hook

import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * CDN拦截器Hook - 绕过CDN责任链检测
 * 直接返回第3个参数，跳过原始方法执行
 */
class CdnHook(module: XposedModule, versionCode: Int) {

    init {
        if (versionCode >= 138) {
            // 获取拦截器方法列表，可能为null（目标类未找到时），需要判空避免崩溃
            val methodList = ClassHelper.HttpInterceptor.getMethodList()
            if (!methodList.isNullOrEmpty()) {
                for (m in methodList) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            // 直接返回第3个参数，跳过原始方法执行
                            return chain.getArg(2)
                        }
                    })
                }
                LogUtils.i("CdnHook: 成功注册CDN拦截器hooks")
            } else {
                LogUtils.w("CdnHook: 拦截器方法列表为空，跳过hook")
            }
        }
    }
}
