/**
 * 设置Hook - 长按底部导航栏"我的"按钮弹出代理设置对话框
 *
 * 长按事件完整调用链（基于反编译源码分析）：
 * 1. rm0.m.e() 给视图设置 OnLongClickListener
 *    回调中调用 rm0.m.n(handler, this$0, view)
 * 2. n() 调用 handler.b(this$0.getTabCode())
 *    handler 实际类型为 dl0.c$d (classes20.dex)
 * 3. dl0.c$d.b(tabCode) → parentTabLayoutHandler.b(tabCode)
 *    parentTabLayoutHandler 实际类型为 p$b (classes5.dex)
 * 4. p$b.b(tabCode) → p.k() → NavigationTabLayout.l(p)
 *
 * Hook策略（按优先级）：
 * 1. Hook p$b.b(String) - 底部Tab长按回调（最直接，直接接收tabCode参数）
 * 2. Hook dl0.c$d.b(String) - 单Tab容器长按回调（委托给p$b）
 *
 * 为什么不Hook接口方法dl0.h.b()：
 * Xposed hookMethod 对接口方法不生效，ART运行时调用的是具体实现类的方法，
 * 不会经过接口方法入口
 *
 * 为什么不使用视图注入方式：
 * rm0.m.e() 在 LiveData 数据变化时被重新调用，会覆盖监听器
 *
 * 使用Modern libxposed API 102（Hooker拦截器链）
 *
 */
package com.raincat.dolby_beta.hook

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.ui.isDarkTheme
import com.raincat.dolby_beta.ui.showSettingsDialog
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 设置Hook - 长按"我的"弹出 Compose 设置菜单
 */
class SettingHook(
    private val module: XposedModule,
    context: Context
) {

    /** 标记hook是否已成功应用 */
    private var hookApplied = false
    /** 设置菜单是否正在显示（防止重复弹出） */
    @Volatile
    private var dialogShowing = false
    /** 主线程Handler */
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        LogUtils.d("SettingHook: 初始化，入口方式=长按底部'我的'按钮")

        // 注册Activity生命周期回调，在onResume时尝试hook
        if (context is android.app.Application) {
            registerLifecycleCallbacks(context)
        }

        // 立即尝试hook（Application.onCreate时可能dex已加载）
        tryHook(context.classLoader)
    }

    /**
     * 注册ActivityLifecycleCallbacks
     * 在MainActivity.onResume时尝试hook（此时dex一定已加载）
     */
    private fun registerLifecycleCallbacks(app: android.app.Application) {
        app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (!hookApplied && activity.javaClass.name == "com.netease.cloudmusic.activity.MainActivity") {
                    // 延迟执行，确保所有类已加载
                    mainHandler.postDelayed({ tryHook(activity.classLoader) }, 1000)
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * 通过特征匹配Hook长按事件
     * 匹配失败时禁用设置入口功能
     */
    private fun tryHook(classLoader: ClassLoader) {
        if (hookApplied) return

        // 特征匹配：遍历查找实现Tab长按回调接口的类
        if (tryHookByFeature(classLoader)) return

        // 特征匹配失败，禁用设置入口
        LogUtils.e("SettingHook: 特征匹配未找到Tab长按回调类，禁用设置入口功能")
        SettingHelper.getInstance().setSetting(SettingHelper.master_key, false)
    }

    /**
     * 特征匹配动态查找Tab长按回调类（完全使用特征匹配，不依赖混淆类名）
     *
     * 策略：通过正则匹配遍历 com.netease.cloudmusic.theme.ui 包（明文包名）下的内部类，
     * 通过反射获取内部类实现的接口，检查接口是否含 b(String):boolean 和 c(String):void 方法。
     * 依赖 DEX 缓存进行类名筛选，再通过 ClassLoader 加载并验证特征。
     */
    private fun tryHookByFeature(classLoader: ClassLoader): Boolean {
        try {
            // 正则匹配 com.netease.cloudmusic.theme.ui 包下的内部类
            val pattern = java.util.regex.Pattern.compile(
                "^com\\.netease\\.cloudmusic\\.theme\\.ui\\.[a-z]{1,3}\\$[a-z]{1,3}$"
            )
            val classList = com.raincat.dolby_beta.helper.ClassHelper.getFilteredClasses(pattern, null)
            LogUtils.i("SettingHook: 特征匹配扫描 com.netease.cloudmusic.theme.ui 内部类，共 ${classList.size} 个")

            for (className in classList) {
                try {
                    val clazz = ClassHelper.findClassIfExists(className, classLoader) ?: continue
                    // 检查内部类实现的接口中，是否有含 b(String):boolean 和 c(String):void 方法的接口
                    val tabHandlerInterface = findTabHandlerInterfaceFromInterfaces(clazz) ?: continue
                    val methodB = findBooleanStringMethod(clazz) ?: continue

                    module.hook(methodB).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val tabCode = chain.getArg(0) as? String
                            if ("mine" == tabCode) {
                                val activity = currentActivity
                                if (activity != null) {
                                    mainHandler.post { showSettingsMenu(activity) }
                                    LogUtils.i("SettingHook: [特征匹配-$className] 长按'我的'成功!")
                                }
                                return true
                            }
                            return chain.proceed()
                        }
                    })

                    hookApplied = true
                    LogUtils.i("SettingHook: [特征匹配] 成功hook $className.b(String)! 接口=${tabHandlerInterface.name}")
                    return true
                } catch (_: Exception) {}
            }
            return false
        } catch (e: Throwable) {
            LogUtils.e("SettingHook: 特征匹配hook失败 - ${e.message}")
            return false
        }
    }

    /**
     * 从类实现的接口中查找Tab长按回调接口
     * 特征：接口中含 b(String):boolean 和 c(String):void 方法
     *
     * @param clazz 待检查的类
     * @return 符合特征的接口类，或 null
     */
    private fun findTabHandlerInterfaceFromInterfaces(clazz: Class<*>): Class<*>? {
        for (interfaceClazz in clazz.interfaces) {
            try {
                // 检查接口是否含 b(String):boolean 方法
                val hasMethodB = interfaceClazz.declaredMethods.any { m ->
                    m.returnType == Boolean::class.javaPrimitiveType
                            && m.parameterTypes.size == 1
                            && m.parameterTypes[0] == String::class.java
                            && m.name == "b"
                }
                // 检查接口是否含 c(String):void 方法
                val hasMethodC = interfaceClazz.declaredMethods.any { m ->
                    m.returnType == Void::class.javaPrimitiveType
                            && m.parameterTypes.size == 1
                            && m.parameterTypes[0] == String::class.java
                            && m.name == "c"
                }
                if (hasMethodB && hasMethodC) return interfaceClazz
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 在类中查找 boolean b(String) 方法
     * 用于定位 dl0.h 接口的 b(String tabCode) 长按回调实现
     */
    private fun findBooleanStringMethod(clazz: Class<*>): Method? {
        for (method in clazz.declaredMethods) {
            if (method.returnType == Boolean::class.javaPrimitiveType
                && method.parameterTypes.size == 1
                && method.parameterTypes[0] == String::class.java
            ) {
                return method
            }
        }
        return null
    }

    /**
     * 通过反射获取当前最顶层的Activity
     */
    private val currentActivity: Activity?
        get() {
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val at = atClass.getMethod("currentActivityThread").invoke(null)
                val activitiesField: Field = atClass.getDeclaredField("mActivities")
                activitiesField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val activities = activitiesField.get(at) as? Map<Any, Any> ?: return null
                for (record in activities.values) {
                    val activityField: Field = record.javaClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    val a = activityField.get(record) as? Activity
                    if (a != null && !a.isFinishing && !a.isDestroyed) return a
                }
            } catch (_: Throwable) {}
            return null
        }

    // ==================== 设置菜单弹出 ====================

    /** 弹出 Compose 设置菜单（长按"我的"时调用） */
    private fun showSettingsMenu(activity: Activity) {
        if (dialogShowing) return
        dialogShowing = true
        try {
            showSettingsDialog(activity, Runnable { dialogShowing = false }, isDarkTheme(activity))
            LogUtils.i("SettingHook: Compose 设置菜单已弹出")
        } catch (e: Throwable) {
            dialogShowing = false
            LogUtils.e("SettingHook: showSettingsDialog 异常 - ${LogUtils.getStackTraceString(e)}")
        }
    }
}
