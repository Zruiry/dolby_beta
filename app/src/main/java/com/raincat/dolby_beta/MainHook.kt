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
 * Application类选择策略：
 * - 正常情况：AndroidManifest注册的是MyApplication，系统调用其生命周期方法
 * - SuperLyric兼容：SuperLyric会hook Instrumentation.newApplication将MyApplication
 *   替换为CloudMusicApplication，导致hook MyApplication失效
 * - 解决方案：同时hook所有候选Application类的生命周期方法，用标志位保证只初始化一次
 *
 */
package com.raincat.dolby_beta

import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class MainHook : XposedModule() {

    /** 标记attachBaseContext阶段是否已初始化（防止多个Application类hook重复触发） */
    private var attachBaseContextInitialized = false
    /** 标记onCreate阶段是否已初始化（防止多个Application类hook重复触发） */
    private var onCreateInitialized = false

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
     * 兼容策略：同时hook所有候选Application类的生命周期方法，用标志位保证只初始化一次。
     * 原因：SuperLyric等模块会hook Instrumentation.newApplication将MyApplication
     * 替换为CloudMusicApplication，导致仅hook MyApplication时回调不触发。
     *
     * 参考dev分支：
     * - attachBaseContext阶段：初始化ProxyHook和启动脚本（最早可获取Context的时机）
     * - onCreate阶段：初始化SettingHook、EAPIHook等（需要Application完全初始化）
     */
    private fun initHook(classLoader: ClassLoader, packageName: String, isOther: Boolean) {
        // 查找所有可用的Application类
        val appClassNames = findApplicationClasses(classLoader)
        if (appClassNames.isEmpty()) {
            LogUtils.e("MainHook: 未找到任何Application类")
            return
        }

        LogUtils.i("MainHook: 找到Application类 - ${appClassNames.joinToString()}")

        // 对每个候选Application类都hook attachBaseContext和onCreate
        // 使用标志位确保只初始化一次（实际运行时只有一个Application类会被系统实例化）
        for (appClassName in appClassNames) {
            val appClass = try {
                classLoader.loadClass(appClassName)
            } catch (e: Exception) {
                LogUtils.w("MainHook: Application类加载失败 - $appClassName, ${e.message}")
                continue
            }

            // hook attachBaseContext：在Application最早生命周期初始化ProxyHook和启动脚本
            try {
                val attachMethod = appClass.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
                hook(attachMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        // 标志位保证只初始化一次
                        if (attachBaseContextInitialized) return result
                        attachBaseContextInitialized = true
                        try {
                            val context = chain.thisObject as android.content.Context
                            // 初始化文件日志（debug 版自动写入日志到文件，方便 adb pull 拉取分析）
                            LogUtils.init(context)
                            LogUtils.i("MainHook: attachBaseContext afterHook - appClass=$appClassName, context=${context.packageName}")
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
                LogUtils.i("MainHook: 成功hook $appClassName.attachBaseContext")
            } catch (e: Exception) {
                LogUtils.w("MainHook: hook $appClassName.attachBaseContext失败 - ${e.message}")
            }

            // hook onCreate：在Application完全初始化后初始化SettingHook、EAPIHook等
            try {
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        // 标志位保证只初始化一次
                        if (onCreateInitialized) return result
                        onCreateInitialized = true
                        try {
                            val context = chain.thisObject as android.content.Context
                            LogUtils.i("MainHook: onCreate afterHook - appClass=$appClassName, context=${context.packageName}")
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
                LogUtils.i("MainHook: 成功hook $appClassName.onCreate")
            } catch (e: Exception) {
                LogUtils.w("MainHook: hook $appClassName.onCreate失败 - ${e.message}")
            }
        }
    }

    /**
     * 查找所有可用的Application类
     *
     * 候选列表说明：
     * - MyApplication：AndroidManifest注册的Application，正常情况下系统调用其生命周期方法
     * - CloudMusicApplication：SuperLyric通过hook Instrumentation.newApplication
     *   将MyApplication替换为CloudMusicApplication，此时MyApplication不会被实例化
     * - NeteaseMusicApplication：Tinker委托的Application，作为兜底候选
     *
     * @return 所有能成功加载的Application类名列表
     */
    private fun findApplicationClasses(classLoader: ClassLoader): List<String> {
        val candidates = arrayOf(
            "com.netease.nis.wrapper.MyApplication",          // AndroidManifest注册的Application
            "com.netease.cloudmusic.CloudMusicApplication",   // SuperLyric重定向后的Application
            "com.netease.cloudmusic.NeteaseMusicApplication", // Tinker委托的Application
        )
        val result = mutableListOf<String>()
        for (name in candidates) {
            try {
                classLoader.loadClass(name)
                result.add(name)
            } catch (_: ClassNotFoundException) {}
        }
        return result
    }
}
