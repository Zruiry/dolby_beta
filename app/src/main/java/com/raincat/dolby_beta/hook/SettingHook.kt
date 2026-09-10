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
import com.raincat.dolby_beta.ui.isDarkTheme
import com.raincat.dolby_beta.ui.showSettingsDialog
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * 设置Hook - 长按"我的"弹出 Compose 设置菜单
 */
class SettingHook(
    private val module: XposedModule,
    context: Context
) {

    companion object {
        /** 旧版 Tab 长按回调内部类特征：类名纯字母（如 9.5.81 的 theme.ui.p$b） */
        private val TAB_HANDLER_PATTERN_LEGACY = Pattern.compile(
            "^com\\.netease\\.cloudmusic\\.theme\\.ui\\.[a-z]{1,3}\\$[a-z]{1,3}$"
        )
        /** 新版特征：类名可含数字，并兼容 theme.ui.tab 子包（如 9.5.90 的 theme.ui.f0$b） */
        private val TAB_HANDLER_PATTERN_EXTENDED = Pattern.compile(
            "^com\\.netease\\.cloudmusic\\.theme\\.ui(\\.[a-z][a-z0-9]{0,3})?\\.[a-z][a-z0-9]{0,3}\\$[a-z][a-z0-9]{0,3}$"
        )
    }

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
     *
     * 匹配失败只记录日志，不得改写设置项：网易云升级后单个 Hook 点失配属于常态，
     * 若在此关闭总开关会导致 EAPIHook/ProxyHook/CdnHook 等功能整体失效
     */
    private fun tryHook(classLoader: ClassLoader) {
        if (hookApplied) return

        // 特征匹配：遍历查找实现Tab长按回调接口的类
        if (tryHookByFeature(classLoader)) return

        LogUtils.e("SettingHook: 特征匹配未找到Tab长按回调类，设置入口不可用")
    }

    /**
     * 特征匹配动态查找Tab长按回调类（完全使用特征匹配，不依赖混淆类名）
     *
     * 策略：通过正则匹配遍历 com.netease.cloudmusic.theme.ui 包（明文包名）下的内部类，
     * 通过反射获取内部类实现的接口，检查接口是否含 Tab 回调方法。
     * 依赖 DEX 缓存进行类名筛选，再通过 ClassLoader 加载并验证特征。
     *
     * 两级匹配，历史版本先用旧特征与旧判定、命中即止：
     * - 旧特征（9.5.81 及以前）：类名纯字母，如 com.netease.cloudmusic.theme.ui.p$b
     * - 新特征（9.5.90 起）：类名可含数字，如 com.netease.cloudmusic.theme.ui.f0$b，并兼容 theme.ui.tab 子包
     */
    private fun tryHookByFeature(classLoader: ClassLoader): Boolean {
        // 旧版路径：类名特征与判定条件全部沿用历史实现，历史版本（9.5.81 及以前）行为与改动前完全一致
        if (tryHookByPattern(classLoader, TAB_HANDLER_PATTERN_LEGACY, true)) return true
        // 新版路径：放宽类名特征（允许数字类名与 tab 子包），并排除 Kotlin 接口默认实现
        return tryHookByPattern(classLoader, TAB_HANDLER_PATTERN_EXTENDED, false)
    }

    /**
     * 按指定类名特征遍历并 hook Tab 长按回调
     *
     * @param pattern 内部类名特征正则
     * @param legacyCriteria 是否使用历史版本的判定条件（true 时走旧逻辑，保证旧版本匹配结果不变）
     * @return 是否成功 hook
     */
    private fun tryHookByPattern(
        classLoader: ClassLoader,
        pattern: java.util.regex.Pattern,
        legacyCriteria: Boolean
    ): Boolean {
        try {
            val classList = ClassHelper.getFilteredClasses(pattern, null)
            LogUtils.i("SettingHook: 特征匹配扫描 com.netease.cloudmusic.theme.ui 内部类，共 ${classList.size} 个")

            for (className in classList) {
                try {
                    val clazz = ClassHelper.findClassIfExists(className, classLoader) ?: continue
                    val tabHandlerInterface = if (legacyCriteria) {
                        findLegacyTabHandlerInterface(clazz)
                    } else {
                        findTabHandlerInterface(clazz)
                    } ?: continue
                    val longPressMethod = if (legacyCriteria) {
                        findLegacyLongPressMethod(clazz)
                    } else {
                        findLongPressMethod(clazz, tabHandlerInterface)
                    } ?: continue

                    module.hook(longPressMethod).intercept(object : XposedInterface.Hooker {
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
                    LogUtils.i("SettingHook: [特征匹配] 成功hook $className.${longPressMethod.name}(String)! 接口=${tabHandlerInterface.name}")
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
     * 历史版本的Tab长按回调接口判定（历史实现原样保留，勿修改）
     *
     * 特征：接口中含名为 b 的 (String):boolean 方法与名为 c 的 (String):void 方法
     */
    private fun findLegacyTabHandlerInterface(clazz: Class<*>): Class<*>? {
        for (interfaceClazz in clazz.interfaces) {
            try {
                val hasMethodB = interfaceClazz.declaredMethods.any { m ->
                    m.returnType == Boolean::class.javaPrimitiveType
                            && m.parameterTypes.size == 1
                            && m.parameterTypes[0] == String::class.java
                            && m.name == "b"
                }
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
     * 历史版本的长按回调方法定位（历史实现原样保留，勿修改）
     * 取实现类中第一个 (String):boolean 方法
     */
    private fun findLegacyLongPressMethod(clazz: Class<*>): Method? =
        clazz.declaredMethods.firstOrNull { method ->
            method.returnType == Boolean::class.javaPrimitiveType
                    && method.parameterTypes.size == 1
                    && method.parameterTypes[0] == String::class.java
        }

    /**
     * 从类实现的接口中查找Tab长按回调接口
     * 特征：接口中含 (String):boolean 与 (String):void 方法
     *
     * 不校验方法名：接口方法名随版本混淆（9.5.81 为 b/c，9.5.90 为 b/c/d 等多个方法）
     *
     * @param clazz 待检查的类
     * @return 符合特征的接口类，或 null
     */
    private fun findTabHandlerInterface(clazz: Class<*>): Class<*>? {
        for (interfaceClazz in clazz.interfaces) {
            try {
                val hasBooleanString = interfaceClazz.declaredMethods.any { m ->
                    m.returnType == Boolean::class.javaPrimitiveType
                            && m.parameterTypes.size == 1
                            && m.parameterTypes[0] == String::class.java
                }
                val hasVoidString = interfaceClazz.declaredMethods.any { m ->
                    m.returnType == Void::class.javaPrimitiveType
                            && m.parameterTypes.size == 1
                            && m.parameterTypes[0] == String::class.java
                }
                if (hasBooleanString && hasVoidString) return interfaceClazz
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 在实现类中查找Tab长按回调方法
     *
     * 分两级定位，保证历史版本的匹配结果不变：
     * 1. 历史版本长按回调方法名固定为 `b`，优先按方法名匹配（同签名方法多于1个时避免选错）
     * 2. 未命中再按签名通用匹配，但排除 Kotlin 接口默认实现
     *
     * 为什么要排除默认实现：9.5.90 的接口除长按回调外还新增了带默认实现的
     * (String):boolean 方法（自定义logo等），两者签名相同、仅方法体不同，
     * 仅凭签名无法区分，若误 hook 默认实现会失效或引入误触发
     *
     * @param clazz 实现类
     * @param handlerInterface 长按回调接口
     * @return 长按回调方法，或 null
     */
    private fun findLongPressMethod(clazz: Class<*>, handlerInterface: Class<*>): Method? {
        val booleanStringMethods = handlerInterface.declaredMethods.filter { m ->
            m.returnType == Boolean::class.javaPrimitiveType
                    && m.parameterTypes.size == 1
                    && m.parameterTypes[0] == String::class.java
        }

        val legacyMethod = booleanStringMethods.firstOrNull { it.name == "b" }
            ?.let { candidate -> findOverride(clazz, candidate) }
        if (legacyMethod != null) return legacyMethod

        for (candidate in booleanStringMethods.filter { !hasKotlinDefaultBody(handlerInterface, it) }) {
            val override = findOverride(clazz, candidate)
            if (override != null) return override
        }
        // 兜底：候选全被判为默认实现时（判定可能误判），仍按签名尝试匹配覆写
        return booleanStringMethods.firstNotNullOfOrNull { findOverride(clazz, it) }
    }

    /** 在实现类中查找接口方法的覆写（按方法名与参数类型匹配） */
    private fun findOverride(clazz: Class<*>, interfaceMethod: Method): Method? =
        clazz.declaredMethods.firstOrNull { m ->
            m.name == interfaceMethod.name && m.parameterTypes.contentEquals(interfaceMethod.parameterTypes)
        }

    /**
     * 判断接口方法是否为 Kotlin 接口默认实现
     *
     * 判定依据（任一成立即为默认实现）：
     * 1. 方法非 abstract（Kotlin -Xjvm-default=all 模式下默认方法带方法体）
     * 2. 接口所在的静态实现持有类（如 ph0.i$a）中存在同签名静态方法
     *    （Kotlin 旧模式会为带默认实现的方法生成静态转发方法，签名首个参数为接口类型）
     */
    private fun hasKotlinDefaultBody(handlerInterface: Class<*>, interfaceMethod: Method): Boolean {
        if (!Modifier.isAbstract(interfaceMethod.modifiers)) return true
        return handlerInterface.declaredClasses.any { nested ->
            nested.declaredMethods.any { m ->
                Modifier.isStatic(m.modifiers)
                        && m.returnType == interfaceMethod.returnType
                        && m.parameterTypes.size == interfaceMethod.parameterTypes.size + 1
                        && m.parameterTypes[0] == handlerInterface
                        && m.parameterTypes.copyOfRange(1, m.parameterTypes.size)
                            .contentEquals(interfaceMethod.parameterTypes)
            }
        }
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
