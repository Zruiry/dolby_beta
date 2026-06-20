/**
 * 模块入口 - 基于Modern libxposed API 102
 *
 * 继承XposedModule，实现XposedModuleInterface的生命周期回调：
 * - onModuleLoaded: 模块加载时调用（Zygote阶段），用于获取模块路径
 * - onPackageReady: 包就绪时调用，获取真实ClassLoader
 *
 * 初始化时序（参考dev分支）：
 * - onPackageReady中hook Application.attachBaseContext的afterHook
 * - attachBaseContext阶段：初始化ProxyHook和启动脚本（此时OkHttpClient还未构建，
 *   hook addInterceptor能在cronet拦截器添加前生效）
 * - onPackageReady中hook Application.onCreate的afterHook
 * - onCreate阶段：初始化SettingHook、EAPIHook等（需要Application完全初始化）
 *
 * 注意：AndroidManifest注册的Application是com.netease.nis.wrapper.MyApplication，
 * 它通过Tinker委托给NeteaseMusicApplication，但系统只调用MyApplication的生命周期方法。
 * 因此hook MyApplication而非NeteaseMusicApplication。
 *
 */
package com.raincat.dolby_beta

import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class MainHook : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        // 启动分隔标记，便于在累积日志中定位每次启动（captureLog 场景）
        // 仅在主进程输出启动分隔标记，避免多进程重复输出（播放进程也会触发 onModuleLoaded）
        val processName = param.processName
        val isMainProcess = processName == null ||
            processName == "com.netease.cloudmusic" ||
            processName == "com.netease.cloudmusic.lite" ||
            processName == "com.hihonor.cloudmusic"
        ScriptHelper.modulePath = moduleApplicationInfo.sourceDir
        if (isMainProcess) {
            LogUtils.i("==================== dolby_beta 启动 ====================")
            LogUtils.i("MainHook: onModuleLoaded - 进程=$processName, isSystemServer=${param.isSystemServer}")
            LogUtils.i("MainHook: modulePath=${ScriptHelper.modulePath}")
        } else {
            LogUtils.i("MainHook: onModuleLoaded - 子进程跳过 - 进程=$processName")
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        val classLoader = param.classLoader

        LogUtils.i("MainHook: onPackageReady - 包名=$packageName, isFirstPackage=${param.isFirstPackage}")

        when (packageName) {
            "com.netease.cloudmusic" -> initHook(classLoader, packageName, false)
            "com.netease.cloudmusic.lite" -> initHook(classLoader, packageName, true)
            "com.hihonor.cloudmusic" -> initHook(classLoader, packageName, true)
        }
    }

    /**
     * hook Application的生命周期方法，在对应阶段初始化Hook
     *
     * 参考dev分支：
     * - attachBaseContext阶段：初始化ProxyHook和启动脚本（最早可获取Context的时机）
     * - onCreate阶段：初始化SettingHook、EAPIHook等（需要Application完全初始化）
     *
     * 注意：AndroidManifest注册的Application是com.netease.nis.wrapper.MyApplication，
     * 它通过Tinker委托给NeteaseMusicApplication，但系统只调用MyApplication的生命周期方法。
     * 因此hook MyApplication而非NeteaseMusicApplication。
     */
    private fun initHook(classLoader: ClassLoader, packageName: String, isOther: Boolean) {
        // 查找Application类
        // MyApplication是AndroidManifest注册的Application，系统会调用其生命周期方法
        // NeteaseMusicApplication是Tinker委托的Application，其生命周期方法不会被系统直接调用
        val appClassName = findApplicationClass(classLoader) ?: run {
            LogUtils.e("MainHook: 未找到Application类")
            return
        }

        val appClass = try {
            classLoader.loadClass(appClassName)
        } catch (e: Exception) {
            LogUtils.e("MainHook: Application类加载失败 - $appClassName, ${e.message}")
            return
        }

        LogUtils.i("MainHook: 找到Application类 - ${appClass.name}")

        // hook attachBaseContext：在Application最早生命周期初始化ProxyHook和启动脚本
        // 此时OkHttpClient还未构建，hook addInterceptor能在cronet拦截器添加前生效
        try {
            val attachMethod = appClass.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            hook(attachMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val context = chain.thisObject as android.content.Context
                        // 初始化文件日志（debug 版自动写入日志到文件，方便 adb pull 拉取分析）
                        LogUtils.init(context)
                        LogUtils.i("MainHook: attachBaseContext afterHook - context=${context.packageName}")
                        if (isOther) {
                            HookOther(this@MainHook, classLoader, packageName, context, true)
                        } else {
                            Hook(this@MainHook, classLoader, packageName, context, true)
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("MainHook: attachBaseContext初始化失败 - ${e.message}")
                    }
                    return result
                }
            })
            LogUtils.i("MainHook: 成功hook attachBaseContext")
        } catch (e: Exception) {
            LogUtils.e("MainHook: hook attachBaseContext失败 - ${e.message}")
        }

        // hook onCreate：在Application完全初始化后初始化SettingHook、EAPIHook等
        try {
            val onCreateMethod = appClass.getDeclaredMethod("onCreate")
            hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val context = chain.thisObject as android.content.Context
                        LogUtils.i("MainHook: onCreate afterHook - context=${context.packageName}")
                        if (isOther) {
                            HookOther(this@MainHook, classLoader, packageName, context, false)
                        } else {
                            Hook(this@MainHook, classLoader, packageName, context, false)
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("MainHook: onCreate初始化失败 - ${e.message}")
                    }
                    return result
                }
            })
            LogUtils.i("MainHook: 成功hook onCreate")
        } catch (e: Exception) {
            LogUtils.e("MainHook: hook onCreate失败 - ${e.message}")
        }
    }

    /**
     * 查找Application类
     * 优先查找MyApplication（AndroidManifest注册的Application），
     * 回退到NeteaseMusicApplication（Tinker委托的Application）
     */
    private fun findApplicationClass(classLoader: ClassLoader): String? {
        val candidates = arrayOf(
            "com.netease.nis.wrapper.MyApplication",  // 标准版AndroidManifest注册的Application
            "com.netease.cloudmusic.NeteaseMusicApplication",  // Tinker委托的Application
        )
        for (name in candidates) {
            try {
                classLoader.loadClass(name)
                return name
            } catch (_: ClassNotFoundException) {}
        }
        return null
    }
}
