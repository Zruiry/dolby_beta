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
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import com.raincat.dolby_beta.view.BaseDialogInputItem
import com.raincat.dolby_beta.view.BaseDialogItem
import com.raincat.dolby_beta.view.proxy.*
import com.raincat.dolby_beta.view.proxy.configuration.*
import com.raincat.dolby_beta.view.setting.TitleView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 设置Hook - 长按"我的"弹出设置对话框
 */
class SettingHook(
    private val module: XposedModule,
    context: Context,
    versionCode: Int
) {

    private var dialogRoot: LinearLayout? = null
    private var dialogProxyRoot: LinearLayout? = null
    private var dialogScriptRoot: LinearLayout? = null
    private var dialogBeautyRoot: LinearLayout? = null
    private var dialogSidebarRoot: LinearLayout? = null
    /** 脚本启动命令显示TextView引用（用于refresh时更新） */
    private var scriptCommandText: TextView? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    /** 标记hook是否已成功应用 */
    private var hookApplied = false
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
                    val clazz = findClassIfExists(className, classLoader) ?: continue
                    // 检查内部类实现的接口中，是否有含 b(String):boolean 和 c(String):void 方法的接口
                    val tabHandlerInterface = findTabHandlerInterfaceFromInterfaces(clazz) ?: continue
                    val methodB = findBooleanStringMethod(clazz) ?: continue

                    module.hook(methodB).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val tabCode = chain.getArg(0) as? String
                            if ("mine" == tabCode) {
                                val activity = currentActivity
                                if (activity != null) {
                                    mainHandler.post { showSettingDialog(activity) }
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

    // ==================== 对话框相关方法 ====================

    private fun showSettingDialog(context: Context) {
        try {
            dialogRoot = BaseDialogItem(context)
            dialogRoot!!.orientation = LinearLayout.VERTICAL
            val scrollView = ScrollView(context)
            scrollView.overScrollMode = ScrollView.OVER_SCROLL_NEVER
            scrollView.isVerticalScrollBarEnabled = false
            scrollView.addView(dialogRoot)

        // 主设置页面布局（与dev分支一致）：总开关 → DEX缓存 → Hook警告 → 黑胶VIP → 一起听 → 修复评论 → 隐藏升级 → 签到 → 每日打卡 → 自助打卡 → 音源代理 → 美化 → 重置 → 关于
        val masterView = com.raincat.dolby_beta.view.setting.MasterView(context)
        val dexView = com.raincat.dolby_beta.view.setting.DexView(context)
        val warnView = com.raincat.dolby_beta.view.setting.WarnView(context)
        val blackView = com.raincat.dolby_beta.view.setting.BlackView(context)
        val listenView = com.raincat.dolby_beta.view.setting.ListenView(context)
        val fixCommentView = com.raincat.dolby_beta.view.setting.FixCommentView(context)
        val updateView = com.raincat.dolby_beta.view.setting.UpdateView(context)
        val signView = com.raincat.dolby_beta.view.setting.SignView(context)
        val signSongDailyView = com.raincat.dolby_beta.view.setting.SignSongDailyView(context)
        val signSongSelfView = com.raincat.dolby_beta.view.setting.SignSongSelfView(context)
        val proxyView = com.raincat.dolby_beta.view.setting.ProxyView(context)
        val beautyView = com.raincat.dolby_beta.view.setting.BeautyView(context)
        val resetModuleView = com.raincat.dolby_beta.view.setting.ResetModuleView(context)
        val aboutView = com.raincat.dolby_beta.view.setting.AboutView(context)

        // 依赖关系：DEX缓存、音源代理、美化 依赖总开关
        dexView.setBaseOnView(masterView)
        proxyView.setBaseOnView(masterView)
        beautyView.setBaseOnView(masterView)

        // 禁用一级菜单中不需要的功能项（仅保留总开关、DEX缓存、音源代理设置、美化设置、重置模块、关于）
        warnView.isEnabled = false
        blackView.isEnabled = false
        listenView.isEnabled = false
        fixCommentView.isEnabled = false
        updateView.isEnabled = false
        signView.isEnabled = false
        signSongDailyView.isEnabled = false
        signSongSelfView.isEnabled = false

        dialogRoot!!.addView(TitleView(context))
        dialogRoot!!.addView(masterView)
        dialogRoot!!.addView(dexView)
        dialogRoot!!.addView(warnView)
        dialogRoot!!.addView(blackView)
        dialogRoot!!.addView(listenView)
        dialogRoot!!.addView(fixCommentView)
        dialogRoot!!.addView(updateView)
        dialogRoot!!.addView(signView)
        dialogRoot!!.addView(signSongDailyView)
        dialogRoot!!.addView(signSongSelfView)
        dialogRoot!!.addView(proxyView)
        dialogRoot!!.addView(beautyView)
        dialogRoot!!.addView(resetModuleView)
        dialogRoot!!.addView(aboutView)

        registerBroadcastReceiver(context)

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton("确定") { _, _ -> }
            .setNegativeButton("重启网易云") { _, _ -> restartApplication(context) }
            .show()
        } catch (e: Throwable) {
            LogUtils.e("SettingHook: showSettingDialog异常 - ${LogUtils.getStackTraceString(e)}")
        }
    }

    private fun showProxyConfigurationDialog(context: Context) {
        dialogProxyRoot = BaseDialogItem(context)
        dialogProxyRoot!!.orientation = LinearLayout.VERTICAL
        val proxyHttpView = ProxyHttpView(context)
        val proxyPortView = ProxyPortView(context)

        dialogProxyRoot!!.addView(ProxyConfigurationTitleView(context))
        dialogProxyRoot!!.addView(proxyHttpView)
        dialogProxyRoot!!.addView(proxyPortView)

        AlertDialog.Builder(context)
            .setView(dialogProxyRoot)
            .setCancelable(true)
            .setPositiveButton("仅保存") { _, _ -> }
            .setNegativeButton("保存并重启") { _, _ -> restartApplication(context) }
            .show()
    }

    /**
     * 显示音源代理设置对话框
     * 布局和功能以本项目为准
     */
    private fun showProxyDialog(context: Context) {
        dialogProxyRoot = BaseDialogItem(context)
        dialogProxyRoot!!.orientation = LinearLayout.VERTICAL
        val scrollView = ScrollView(context)
        scrollView.overScrollMode = ScrollView.OVER_SCROLL_NEVER
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.addView(dialogProxyRoot)

        val proxyMasterView = ProxyMasterView(context)
        val proxyGrayView = ProxyGrayView(context)
        proxyGrayView.setBaseOnView(proxyMasterView)
        val proxyCoverView = ProxyCoverView(context)
        proxyCoverView.setBaseOnView(proxyMasterView)
        val scriptConfigurationView = ScriptConfigurationView(context)
        scriptConfigurationView.setBaseOnView(proxyMasterView)
        val proxyPriorityView = ProxyPriorityView(context)
        proxyPriorityView.setBaseOnView(proxyMasterView)
        val proxyFlacView = ProxyFlacView(context)
        proxyFlacView.setBaseOnView(proxyMasterView)
        val proxyServerView = ProxyServerView(context)
        proxyServerView.setBaseOnView(proxyMasterView)
        val proxyConfigurationView = ProxyConfigurationView(context)
        proxyConfigurationView.setBaseOnView(proxyMasterView)

        dialogProxyRoot!!.addView(ProxyTitleView(context))
        dialogProxyRoot!!.addView(proxyMasterView)
        dialogProxyRoot!!.addView(proxyCoverView)
        dialogProxyRoot!!.addView(scriptConfigurationView)
        // 不变灰放在脚本参数配置下面（与dev分支一致）
        dialogProxyRoot!!.addView(proxyGrayView)
        dialogProxyRoot!!.addView(proxyPriorityView)
        dialogProxyRoot!!.addView(proxyFlacView)
        dialogProxyRoot!!.addView(proxyServerView)
        dialogProxyRoot!!.addView(proxyConfigurationView)

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setCancelable(true)
            .setPositiveButton("仅保存") { _, _ -> }
            .setNegativeButton("保存并重启") { _, _ -> restartApplication(context) }
            .show()
    }

    /**
     * 显示美化设置对话框
     */
    private fun showBeautyDialog(context: Context) {
        dialogBeautyRoot = BaseDialogItem(context)
        dialogBeautyRoot!!.orientation = LinearLayout.VERTICAL
        val scrollView = ScrollView(context)
        scrollView.overScrollMode = ScrollView.OVER_SCROLL_NEVER
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.addView(dialogBeautyRoot)

        dialogBeautyRoot!!.addView(com.raincat.dolby_beta.view.beauty.BeautyTitleView(context))
        val beautyNightModeView = com.raincat.dolby_beta.view.beauty.BeautyNightModeView(context)
        val beautyTabHideView = com.raincat.dolby_beta.view.beauty.BeautyTabHideView(context)
        val beautyBannerHideView = com.raincat.dolby_beta.view.beauty.BeautyBannerHideView(context)
        val beautyBubbleHideView = com.raincat.dolby_beta.view.beauty.BeautyBubbleHideView(context)
        val beautyKSongHideView = com.raincat.dolby_beta.view.beauty.BeautyKSongHideView(context)
        val beautyBlackHideView = com.raincat.dolby_beta.view.beauty.BeautyBlackHideView(context)
        val beautyRotationView = com.raincat.dolby_beta.view.beauty.BeautyRotationView(context)
        val beautyCommentHotView = com.raincat.dolby_beta.view.beauty.BeautyCommentHotView(context)
        val playerBackgroundView = com.raincat.dolby_beta.view.beauty.PlayerBackgroundView(context)
        val beautySidebarHideView = com.raincat.dolby_beta.view.beauty.BeautySidebarHideView(context)

        // 禁用美化设置中不需要的功能项（仅保留精简Tab）
        beautyNightModeView.isEnabled = false
        beautyBannerHideView.isEnabled = false
        beautyBubbleHideView.isEnabled = false
        beautyKSongHideView.isEnabled = false
        beautyBlackHideView.isEnabled = false
        beautyRotationView.isEnabled = false
        beautyCommentHotView.isEnabled = false
        playerBackgroundView.isEnabled = false
        beautySidebarHideView.isEnabled = false

        dialogBeautyRoot!!.addView(beautyNightModeView)
        dialogBeautyRoot!!.addView(beautyTabHideView)
        dialogBeautyRoot!!.addView(beautyBannerHideView)
        dialogBeautyRoot!!.addView(beautyBubbleHideView)
        dialogBeautyRoot!!.addView(beautyKSongHideView)
        dialogBeautyRoot!!.addView(beautyBlackHideView)
        dialogBeautyRoot!!.addView(beautyRotationView)
        dialogBeautyRoot!!.addView(beautyCommentHotView)
        dialogBeautyRoot!!.addView(playerBackgroundView)
        dialogBeautyRoot!!.addView(beautySidebarHideView)

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setCancelable(true)
            .setPositiveButton("仅保存") { _, _ -> }
            .setNegativeButton("保存并重启") { _, _ -> restartApplication(context) }
            .show()
    }

    /**
     * 显示播放界面背景设置对话框
     */
    private fun showPlayerBackgroundDialog(context: Context) {
        dialogBeautyRoot = BaseDialogItem(context)
        dialogBeautyRoot!!.orientation = LinearLayout.VERTICAL

        dialogBeautyRoot!!.addView(com.raincat.dolby_beta.view.beauty.background.BackgroundTitleView(context))
        dialogBeautyRoot!!.addView(com.raincat.dolby_beta.view.beauty.background.BackgroundMasterView(context))
        dialogBeautyRoot!!.addView(com.raincat.dolby_beta.view.beauty.background.BackgroundPictureUrlView(context))
        dialogBeautyRoot!!.addView(com.raincat.dolby_beta.view.beauty.background.BackgroundBlurRadiusView(context))

        AlertDialog.Builder(context)
            .setView(dialogBeautyRoot)
            .setCancelable(true)
            .setPositiveButton("仅保存") { _, _ -> }
            .setNegativeButton("保存并重启") { _, _ -> restartApplication(context) }
            .show()
    }

    /**
     * 显示侧边栏精简设置对话框
     * 动态加载当前版本网易云侧边栏的所有Item
     */
    private fun showSidebarDialog(context: Context) {
        dialogSidebarRoot = BaseDialogItem(context)
        dialogSidebarRoot!!.orientation = LinearLayout.VERTICAL
        val scrollView = ScrollView(context)
        scrollView.overScrollMode = ScrollView.OVER_SCROLL_NEVER
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.addView(dialogSidebarRoot)

        val sidebarMap = com.raincat.dolby_beta.model.SidebarEnum.getSidebarEnum()
        val sidebarSettingMap = SettingHelper.getInstance().getSidebarSetting(sidebarMap)
        for (key in sidebarMap.keys) {
            val item = com.raincat.dolby_beta.view.beauty.BeautySidebarHideItem(context)
            item.initData(sidebarMap, sidebarSettingMap, key)
            dialogSidebarRoot!!.addView(item)
        }

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setCancelable(true)
            .setPositiveButton("确定") { _, _ -> }
            .show()
    }

    private fun showScriptConfigurationDialog(context: Context) {
        dialogScriptRoot = BaseDialogItem(context)
        dialogScriptRoot!!.orientation = LinearLayout.VERTICAL
        val proxyOriginalView = ProxyOriginalView(context)
        val proxyQqView = ProxyQqView(context)
        val proxyMiguView = ProxyMiguView(context)

        dialogScriptRoot!!.addView(ScriptConfigurationTitleView(context))
        dialogScriptRoot!!.addView(proxyOriginalView)
        dialogScriptRoot!!.addView(proxyQqView)
        dialogScriptRoot!!.addView(proxyMiguView)
        // 底部显示当前配置生成的脚本启动命令
        dialogScriptRoot!!.addView(createScriptCommandView(context))

        AlertDialog.Builder(context)
            .setView(dialogScriptRoot)
            .setCancelable(true)
            .setPositiveButton("仅保存") { _, _ -> }
            .setNegativeButton("保存并重启") { _, _ -> restartApplication(context) }
            .show()
    }

    /**
     * 创建脚本启动命令显示视图
     * 包含标题"当前启动命令"和命令文本，命令文本会随配置变化而刷新
     */
    private fun createScriptCommandView(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = Tools.dp2px(context, 10f)
            setPadding(padding, Tools.dp2px(context, 5f), padding, padding)
        }
        // 标题
        val titleView = TextView(context).apply {
            text = "当前启动命令"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.BLACK)
            setPadding(0, Tools.dp2px(context, 5f), 0, Tools.dp2px(context, 3f))
        }
        container.addView(titleView)
        // 命令文本
        scriptCommandText = TextView(context).apply {
            text = ScriptHelper.getScriptCommand()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.DKGRAY)
            maxLines = Int.MAX_VALUE
            setLineSpacing(2f, 1f)
        }
        container.addView(scriptCommandText)
        return container
    }

    private fun registerBroadcastReceiver(context: Context) {
        broadcastReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(SettingHelper.refresh_setting)
        intentFilter.addAction(SettingHelper.proxy_setting)
        intentFilter.addAction(SettingHelper.beauty_setting)
        intentFilter.addAction(SettingHelper.sidebar_setting)
        intentFilter.addAction(SettingHelper.background_setting)
        intentFilter.addAction(SettingHelper.proxy_configuration_setting)
        intentFilter.addAction(SettingHelper.script_configuration_setting)
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                try {
                    val action = intent.action
                    if (action == SettingHelper.refresh_setting) {
                        dialogRoot?.let { root ->
                            for (i in 0 until root.childCount) {
                                (root.getChildAt(i) as? BaseDialogItem)?.refresh()
                            }
                        }
                        dialogProxyRoot?.let { root ->
                            for (i in 0 until root.childCount) {
                                val child = root.getChildAt(i)
                                if (child is BaseDialogItem) child.refresh()
                                else if (child is BaseDialogInputItem) child.refresh()
                            }
                        }
                        dialogScriptRoot?.let { root ->
                            for (i in 0 until root.childCount) {
                                val child = root.getChildAt(i)
                                if (child is BaseDialogItem) child.refresh()
                                else if (child is BaseDialogInputItem) child.refresh()
                            }
                            // 刷新脚本启动命令显示
                            scriptCommandText?.text = ScriptHelper.getScriptCommand()
                        }
                        dialogBeautyRoot?.let { root ->
                            for (i in 0 until root.childCount) {
                                (root.getChildAt(i) as? BaseDialogItem)?.refresh()
                            }
                        }
                        dialogSidebarRoot?.let { root ->
                            for (i in 0 until root.childCount) {
                                (root.getChildAt(i) as? BaseDialogItem)?.refresh()
                            }
                        }
                        return
                    }
                    // 广播接收器的Context是Application Context，AlertDialog需要Activity Context才能显示窗口
                    // 否则抛BadTokenException: Unable to add window -- token null is not valid
                    val activityContext = currentActivity
                    if (activityContext == null) {
                        LogUtils.w("SettingHook: 无法获取Activity Context，跳过对话框显示 action=$action")
                        return
                    }
                    when (action) {
                        SettingHelper.proxy_setting -> showProxyDialog(activityContext)
                        SettingHelper.beauty_setting -> showBeautyDialog(activityContext)
                        SettingHelper.sidebar_setting -> showSidebarDialog(activityContext)
                        SettingHelper.background_setting -> showPlayerBackgroundDialog(activityContext)
                        SettingHelper.proxy_configuration_setting -> showProxyConfigurationDialog(activityContext)
                        SettingHelper.script_configuration_setting -> showScriptConfigurationDialog(activityContext)
                    }
                } catch (e: Throwable) {
                    LogUtils.e("SettingHook: 处理广播异常 action=${intent.action} - ${LogUtils.getStackTraceString(e)}")
                }
            }
        }
        // Android 13+（API 33+）动态注册广播接收器必须指定导出标志，否则抛SecurityException导致闪退
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter)
        }
    }

    private fun restartApplication(context: Context) {
        ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0")
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcessInfoList = activityManager.runningAppProcesses
        for (runningAppProcessInfo in runningAppProcessInfoList) {
            if (runningAppProcessInfo.processName.contains(":play")) {
                android.os.Process.killProcess(runningAppProcessInfo.pid)
            }
        }
        System.exit(0)
    }

    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}
