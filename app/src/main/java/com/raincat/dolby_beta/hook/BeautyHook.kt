/**
 * 美化Hook集合 - 包含所有美化相关的Hook
 *
 * 功能：
 * 1. 跟随系统夜间模式（NightMode）
 * 2. 精简Tab（HideTab）
 * 3. 移除Banner（HideBanner）
 * 4. 移除小红点（HideBubble）
 * 5. 黑胶停转（RotationStop）
 * 6. 评论区优先最热（CommentHot）
 * 7. 自定义播放界面背景（PlayerBackground）
 *
 * 适配高版本网易云：使用非混淆类名
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Pair
import android.view.View
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.json.JSONArray

class BeautyHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "BeautyHook"
    }

    init {
        try {
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_night_mode_key)) {
                hookNightMode()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_tab_hide_key)) {
                hookHideTab()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_banner_hide_key)) {
                hookHideBanner()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_bubble_hide_key)) {
                hookHideBubble()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_rotation_key)) {
                hookRotationStop()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_comment_hot_key)) {
                hookCommentHot()
            }
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_background_key)) {
                hookPlayerBackground()
            }
            // 播放页黑胶隐藏和音谱移除（两者都在PlayerActivity.onCreate中处理）
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_black_hide_key) ||
                SettingHelper.getInstance().getSetting(SettingHelper.beauty_ksong_hide_key)) {
                hookPlayerActivity()
            }
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    // ==================== 夜间模式 ====================

    /**
     * Hook MainActivity.onStart - 跟随系统深色模式切换夜间/日间模式
     */
    private fun hookNightMode() {
        val superActivityClass = findClassIfExists("com.netease.cloudmusic.activity.MainActivity", context.classLoader) ?: return
        val onStartMethod = findMethodIfExists(superActivityClass, "onStart") ?: return

        val resourceRouterClass = findClassIfExists("com.netease.cloudmusic.theme.core.ResourceRouter", context.classLoader) ?: return
        val themeAgentClass = findClassIfExists("com.netease.cloudmusic.theme.core.ThemeAgent", context.classLoader) ?: return
        val themeConfigClass = findClassIfExists("com.netease.cloudmusic.theme.core.ThemeConfig", context.classLoader) ?: return
        val themeInfoClass = findClassIfExists("com.netease.cloudmusic.theme.core.ThemeInfo", context.classLoader) ?: return

        module.hook(onStartMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val c = chain.thisObject as Context
                    val resourceRouter = resourceRouterClass.getDeclaredMethod("getInstance").invoke(null)
                    val isNight = resourceRouterClass.getDeclaredMethod("isNightTheme").invoke(resourceRouter) as Boolean
                    val nightModeFlags = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES && !isNight) {
                        // 切换到夜间模式
                        val themeAgent = themeAgentClass.getDeclaredMethod("getInstance").invoke(null)
                        val themeInfo = themeInfoClass.getDeclaredConstructor(Integer.TYPE).newInstance(-3)
                        themeAgentClass.getDeclaredMethod("switchTheme", Context::class.java, themeInfoClass, java.lang.Boolean.TYPE)
                            .invoke(themeAgent, c, themeInfo, true)
                    } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO && isNight) {
                        // 切换回日间模式
                        val themeAgent = themeAgentClass.getDeclaredMethod("getInstance").invoke(null)
                        val prevThemeInfo = themeConfigClass.getDeclaredMethod("getPrevThemeInfo").invoke(null) as Pair<Int, Boolean>
                        val themeInfo = themeInfoClass.getDeclaredConstructor(Integer.TYPE).newInstance(prevThemeInfo.first)
                        themeAgentClass.getDeclaredMethod("switchTheme", Context::class.java, themeInfoClass, java.lang.Boolean.TYPE)
                            .invoke(themeAgent, c, themeInfo, true)
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookNightMode 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookNightMode 成功")
    }

    // ==================== 精简Tab ====================

    /**
     * Hook dl0.g.h() - 精简底部Tab，仅保留"我的"与"首页"，并默认打开"我的"
     *
     * 高版本网易云通过 dl0.g.h() 返回 List<BottomTabInfoVO> 获取底部Tab列表
     * h()返回的BottomTabInfoVO列表会转换为NavigationTabUiState列表，同时用于：
     * 1. Tab UI显示（底部导航栏）
     * 2. ViewPager的Fragment创建（通过dl0.m.m(position)获取tabCode，再createFragment）
     *
     * 精简策略：
     * 1. 过滤返回的Tab列表，只保留 tabCode 为 "mine" 和 "main" 的项（保持原始顺序）
     * 2. 通过hook Nf()返回true，使默认选中"我的"（s4("mine")会返回过滤后的正确索引）
     */
    private fun hookHideTab() {
        // 通过特征匹配查找底部Tab管理类（规范1：禁止硬编码混淆类名）
        val bottomNavClass = com.raincat.dolby_beta.helper.ClassHelper.BottomTabManager.getClazz(context)
            ?: run {
                // 特征匹配失败，禁用精简Tab功能
                LogUtils.e("$TAG: hookHideTab 特征匹配未找到底部Tab管理类，禁用精简Tab功能")
                SettingHelper.getInstance().setSetting(SettingHelper.beauty_tab_hide_key, false)
                return
            }

        // 精确查找 h() 方法：无参数、返回 List
        // 注意：dl0.g 中有多个返回 List 的无参方法（i/n/l/j/f），
        // 必须精确匹配方法名 "h"，否则会 hook 到错误的方法
        val hMethod = bottomNavClass.declaredMethods.firstOrNull {
            it.name == "h" && it.parameterTypes.isEmpty()
        } ?: run {
            LogUtils.w("$TAG: hookHideTab 未找到 dl0.g.h() 方法")
            return
        }

        module.hook(hMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val result = chain.proceed() as? java.util.List<*> ?: return chain.proceed()
                    // 过滤Tab列表，只保留 "mine" 和 "main"，保持原始顺序
                    val filteredList = mutableListOf<Any>()
                    result.forEach { item ->
                        val tabCode = item?.javaClass?.getMethod("getTabCode")?.invoke(item) as? String
                        LogUtils.i("$TAG: hookHideTab 原始Tab tabCode=$tabCode")
                        if (tabCode == "mine" || tabCode == "main") {
                            filteredList.add(item)
                        }
                    }
                    LogUtils.i("$TAG: hookHideTab 过滤Tab列表，保留 ${filteredList.size} 个Tab")
                    return filteredList
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookHideTab 异常 - ${e.message}")
                    return chain.proceed()
                }
            }
        })
        LogUtils.i("$TAG: hookHideTab 成功")

        // Hook com.netease.cloudmusic.adapter.g.createFragment - 调试Fragment创建
        hookCreateFragment()

        // Hook MainActivity.Nf() - 使默认选中"我的"Tab
        // Nc()中：if (Nf()) 选中mine(s4("mine"))，否则选中main(s4("main"))
        // Nf()返回 !m4.u() || av.T()，正常情况下返回false，导致默认选中main（首页）
        // hook Nf()返回true，使默认选中mine（我的）
        // 注意：不需要hook s4()，因为hook dl0.g.h()过滤Tab列表后，
        // v()方法会调用u(b().h(), isForceBlackTheme)更新MainNavigationState，
        // s4()和p4()都基于已过滤的MainNavigationState.e()返回正确值
        hookDefaultTab()

        // Hook ViewPager2初始化 - 预设初始position为mine，避免先显示main再切换
        // ViewPager2初始化时默认position=0（main），Nc()后续切换到mine会导致空白闪烁
        // 在initNavigationAndViewPagerComponent执行后立即设置currentItem为mine索引
        hookViewPager2InitPosition()
    }

    /**
     * Hook ViewPager2.setAdapter - 在设置adapter前预设mPendingCurrentItem为mine索引
     *
     * ViewPager2.setAdapter 内部流程：
     *   1. mRecyclerView.setAdapter(adapter)
     *   2. mCurrentItem = 0  （重置为0）
     *   3. restorePendingState()  （如果 mPendingCurrentItem != -1，设置 mCurrentItem 并 scrollToPosition）
     *
     * 通过在 setAdapter 执行前设置 mPendingCurrentItem = mineIndex，
     * 让 restorePendingState 自动将 mCurrentItem 设为 mineIndex 并滚动到该位置，
     * ViewPager2 首次布局即显示 mine 页面，无需后续 setCurrentItem 切换，消除空白闪烁
     */
    private fun hookViewPager2InitPosition() {
        val vp2Class = findClassIfExists(
            "androidx.viewpager2.widget.ViewPager2", context.classLoader
        ) ?: run {
            LogUtils.w("$TAG: hookViewPager2InitPosition 未找到 ViewPager2 类")
            return
        }

        val setAdapterMethod = vp2Class.declaredMethods.firstOrNull {
            it.name == "setAdapter" && it.parameterTypes.size == 1
        } ?: run {
            LogUtils.w("$TAG: hookViewPager2InitPosition 未找到 setAdapter 方法")
            return
        }

        module.hook(setAdapterMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var mineIndex = -1
                try {
                    // 只处理 MainActivity 的 ViewPager2，跳过 React Native 等其他 ViewPager2 实例
                    // MainActivity 的 adapter 是 com.netease.cloudmusic.adapter.g
                    // React Native 的 adapter 是 com.reactnativepagerview.h
                    val adapter = chain.args[0]
                    val adapterClassName = adapter?.javaClass?.name ?: ""
                    if (!adapterClassName.startsWith("com.netease.cloudmusic.adapter.")) {
                        return chain.proceed()
                    }

                    // 通过反射获取 mine 的索引
                    val mClass = com.raincat.dolby_beta.helper.ClassHelper.TabIndexManager.getClazz(context)
                    if (mClass != null) {
                        val s4Method = mClass.declaredMethods.firstOrNull {
                            it.name == "s4" && it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == String::class.java
                        }
                        if (s4Method != null) {
                            s4Method.isAccessible = true
                            mineIndex = s4Method.invoke(null, "mine") as? Int ?: -1
                            // mineIndex >= 0 表示当前是 MainActivity 场景（Tab已过滤）
                            if (mineIndex >= 0) {
                                // 在 setAdapter 执行前设置 mPendingCurrentItem
                                // restorePendingState 会据此设置 mCurrentItem 并 scrollToPosition
                                val pendingField = vp2Class.getDeclaredField("mPendingCurrentItem")
                                pendingField.isAccessible = true
                                pendingField.setInt(chain.thisObject, mineIndex)
                                LogUtils.i("$TAG: hookViewPager2InitPosition 设置 mPendingCurrentItem=$mineIndex")
                            }
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookViewPager2InitPosition 异常 - ${e.javaClass.simpleName}: ${e.message}")
                }

                val result = chain.proceed()

                // setAdapter 后检查 mCurrentItem 是否正确设置
                if (mineIndex >= 0) {
                    try {
                        val currentItemField = vp2Class.getDeclaredField("mCurrentItem")
                        currentItemField.isAccessible = true
                        val currentItem = currentItemField.getInt(chain.thisObject)
                        LogUtils.i("$TAG: hookViewPager2InitPosition after setAdapter, mCurrentItem=$currentItem, expected=$mineIndex")

                        // 如果 mCurrentItem 仍然是 0，说明 restorePendingState 没有生效，手动设置
                        if (currentItem == 0 && mineIndex > 0) {
                            currentItemField.setInt(chain.thisObject, mineIndex)
                            LogUtils.i("$TAG: hookViewPager2InitPosition 手动设置 mCurrentItem=$mineIndex")
                        }

                        // 关键：通过 LinearLayoutManager.scrollToPositionWithOffset 精确设置初始位置
                        // scrollToPosition 只设置待处理请求，RecyclerView 首次布局时仍先显示 position 0
                        // scrollToPositionWithOffset 会在布局前设置精确偏移，RecyclerView 首次布局即从 mine 开始
                        val layoutManagerField = vp2Class.getDeclaredField("mLayoutManager")
                        layoutManagerField.isAccessible = true
                        val layoutManager = layoutManagerField.get(chain.thisObject)
                        val llmClass = findClassIfExists(
                            "androidx.recyclerview.widget.LinearLayoutManager", context.classLoader
                        )
                        if (layoutManager != null && llmClass != null) {
                            val scrollToPositionWithOffsetMethod = llmClass.getDeclaredMethod(
                                "scrollToPositionWithOffset", Integer.TYPE, Integer.TYPE
                            )
                            scrollToPositionWithOffsetMethod.isAccessible = true
                            scrollToPositionWithOffsetMethod.invoke(layoutManager, mineIndex, 0)
                            LogUtils.i("$TAG: hookViewPager2InitPosition scrollToPositionWithOffset($mineIndex, 0)")
                        }
                    } catch (e: Throwable) {
                        LogUtils.e("$TAG: hookViewPager2InitPosition afterHook 异常 - ${e.javaClass.simpleName}: ${e.message}")
                    }
                }

                return result
            }
        })
        LogUtils.i("$TAG: hookViewPager2InitPosition 成功")
    }

    /**
     * Hook MainActivity.Nf() - 返回true使默认选中"我的"Tab
     *
     * MainActivity.Nc() 逻辑：
     *   if (intent.getIntExtra("SELECT_PAGE_INDEX", -1) == -1) {
     *       if (Nf().booleanValue()) {
     *           intent.putExtra("SELECT_PAGE_INDEX", dl0.m.s4("mine"));  // 选中我的
     *       } else {
     *           intent.putExtra("SELECT_PAGE_INDEX", dl0.m.s4("main"));  // 选中首页
     *       }
     *   }
     *   Mc(intent);
     *
     * Mc(Intent) 逻辑：
     *   int intExtra = wd() ? 0 : intent.getIntExtra("SELECT_PAGE_INDEX", -1);
     *   if (intExtra < this.mPagerAdapter.getLength() && intExtra >= 0) {
     *       setCurrentPage(intExtra, false);  // 设置ViewPager2的position
     *   }
     *
     * 方案：hook Nf()返回true，使Nc()中SELECT_PAGE_INDEX=s4("mine")=0（过滤后索引）
     * 这样Mc方法正常执行，不会干扰子页面逻辑（SELECT_SUB_PAGE_INDEX等）
     */
    private fun hookDefaultTab() {
        val mainActivityClass = findClassIfExists(
            "com.netease.cloudmusic.activity.MainActivity", context.classLoader
        ) ?: run {
            LogUtils.w("$TAG: hookDefaultTab 未找到 MainActivity")
            return
        }

        val nfMethod = mainActivityClass.declaredMethods.firstOrNull {
            it.name == "Nf" && it.parameterTypes.isEmpty()
        } ?: run {
            LogUtils.w("$TAG: hookDefaultTab 未找到 Nf() 方法")
            return
        }

        module.hook(nfMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                LogUtils.i("$TAG: hookDefaultTab Nf() -> true")
                return true
            }
        })
        LogUtils.i("$TAG: hookDefaultTab 成功")
    }

    /**
     * Hook com.netease.cloudmusic.adapter.g.createFragment - 调试Fragment创建
     *
     * createFragment(int position) 通过 dl0.m.p4(position) 获取tabCode，再创建对应Fragment
     * 用于调试：打印position和tabCode，确认Fragment顺序是否正确
     */
    private fun hookCreateFragment() {
        // 通过特征匹配查找Fragment适配器类（规范1：禁止硬编码混淆类名）
        val adapterClass = com.raincat.dolby_beta.helper.ClassHelper.FragmentPagerAdapter.getClazz(context)
            ?: run {
                // 特征匹配失败，仅跳过调试Hook，不影响精简Tab主功能
                LogUtils.w("$TAG: hookCreateFragment 特征匹配未找到Fragment适配器类，跳过调试Hook")
                return
            }

        val createMethod = findMethodIfExists(adapterClass, "createFragment", Integer.TYPE) ?: run {
            LogUtils.w("$TAG: hookCreateFragment 未找到 createFragment 方法")
            return
        }

        module.hook(createMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val position = chain.args[0] as Int
                // 获取tabCode，确认position对应的Tab
                val tabCode = try {
                    val mClass = com.raincat.dolby_beta.helper.ClassHelper.TabIndexManager.getClazz(context)
                    val p4Method = mClass?.declaredMethods?.firstOrNull { it.name == "p4" }
                    p4Method?.isAccessible = true
                    p4Method?.invoke(null, position) as? String
                } catch (e: Throwable) {
                    "error: ${e.message}"
                }
                LogUtils.i("$TAG: hookCreateFragment position=$position tabCode=$tabCode")
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookCreateFragment 成功")

        // Hook ViewPager2.setCurrentItem - 调试ViewPager初始position
        hookViewPager2SetCurrentItem()
    }

    /**
     * Hook ViewPager2.setCurrentItem - 调试ViewPager初始position
     *
     * 打印setCurrentItem的position参数，确认ViewPager2初始显示的position
     */
    private fun hookViewPager2SetCurrentItem() {
        val viewPager2Class = findClassIfExists(
            "androidx.viewpager2.widget.ViewPager2", context.classLoader
        ) ?: run {
            LogUtils.w("$TAG: hookViewPager2SetCurrentItem 未找到 ViewPager2 类")
            return
        }

        val setCurrentItemMethod = viewPager2Class.declaredMethods.firstOrNull {
            it.name == "setCurrentItem" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == Integer.TYPE &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        } ?: run {
            LogUtils.w("$TAG: hookViewPager2SetCurrentItem 未找到 setCurrentItem(int, boolean) 方法")
            return
        }

        module.hook(setCurrentItemMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val position = chain.args[0] as Int
                val smoothScroll = chain.args[1] as Boolean
                LogUtils.i("$TAG: hookViewPager2SetCurrentItem position=$position smoothScroll=$smoothScroll")

                // 如果是 MainActivity 的 ViewPager2 且 position 等于 mCurrentItem，跳过执行
                // 避免 scrollToPosition 覆盖 scrollToPositionWithOffset 设置的精确初始偏移
                // scrollToPosition 会让 LinearLayoutManager 先从 position 0 布局再滚动到目标 position
                // 而 scrollToPositionWithOffset 会在布局前设置精确偏移，直接从目标 position 开始布局
                try {
                    val adapter = viewPager2Class.getDeclaredMethod("getAdapter").invoke(chain.thisObject)
                    val adapterClassName = adapter?.javaClass?.name ?: ""
                    if (adapterClassName.startsWith("com.netease.cloudmusic.adapter.")) {
                        val currentItemField = viewPager2Class.getDeclaredField("mCurrentItem")
                        currentItemField.isAccessible = true
                        val currentItem = currentItemField.getInt(chain.thisObject)
                        if (position == currentItem) {
                            LogUtils.i("$TAG: hookViewPager2SetCurrentItem 跳过, position=$position 等于 mCurrentItem=$currentItem")
                            return null
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookViewPager2SetCurrentItem 检查异常 - ${e.javaClass.simpleName}: ${e.message}")
                }

                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookViewPager2SetCurrentItem 成功")
    }

    // ==================== 移除Banner ====================

    /**
     * Hook MainBannerContainer.onAttachedToWindow - 隐藏Banner
     */
    private fun hookHideBanner() {
        val bannerClass = findClassIfExists("com.netease.cloudmusic.ui.MainBannerContainer", context.classLoader) ?: return
        val method = findMethodIfExists(bannerClass, "onAttachedToWindow") ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val view = chain.thisObject as View
                val layoutParams = view.layoutParams
                layoutParams.height = 1 // 改成0将导致无法下滑刷新
                view.layoutParams = layoutParams
                view.visibility = View.GONE
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookHideBanner 成功")
    }

    // ==================== 移除小红点 ====================

    /**
     * Hook View.setVisibility - 隐藏消息小红点
     */
    private fun hookHideBubble() {
        val bubbleClass = findClassIfExists("com.netease.cloudmusic.ui.MessageBubbleView", context.classLoader)
            ?: findClassIfExists("com.netease.cloudmusic.theme.ui.MessageBubbleView", context.classLoader)
            ?: return
        val viewClass = findClassIfExists("android.view.View", context.classLoader) ?: return
        val method = findMethodIfExists(viewClass, "setVisibility", Integer.TYPE) ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val thisObject = chain.thisObject
                // 仅对消息小红点View生效，其他View正常执行
                if (thisObject.javaClass == bubbleClass) {
                    return chain.proceed(arrayOf(View.GONE))
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookHideBubble 成功")
    }

    // ==================== 黑胶停转 ====================

    /**
     * Hook RotationRelativeLayout$AnimationHolder.prepareAnimation - 阻止黑胶转动动画
     *
     * AnimationHolder 和 prepareAnimation() 在各版本中均为明文，无需混淆名回退
     */
    private fun hookRotationStop() {
        val clazz = findClassIfExists(
            "com.netease.cloudmusic.ui.RotationRelativeLayout\$AnimationHolder", context.classLoader
        ) ?: return
        val method = findMethodIfExists(clazz, "prepareAnimation") ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = null
        })
        LogUtils.i("$TAG: hookRotationStop 成功")
    }

    // ==================== 评论区优先最热 ====================

    /**
     * Hook SortTypeList.parseList - 调整评论排序顺序，优先显示"最热"
     */
    private fun hookCommentHot() {
        val sortTypeListClass = findClassIfExists("com.netease.cloudmusic.module.comment2.meta.SortTypeList", context.classLoader)
            ?: findClassIfExists("com.netease.cloudmusic.music.biz.comment.meta.SortTypeList", context.classLoader)
            ?: return
        val method = findMethodIfExists(sortTypeListClass, "parseList", JSONArray::class.java) ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val array = chain.getArg(0) as? JSONArray
                    if (array != null && array.length() >= 3) {
                        val array2 = JSONArray()
                        array2.put(array.getJSONObject(1))
                        array2.put(array.getJSONObject(2))
                        array2.put(array.getJSONObject(0))
                        // 使用修改后的参数执行原始方法
                        return chain.proceed(arrayOf(array2))
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookCommentHot 异常 - ${e.message}")
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookCommentHot 成功")
    }

    // ==================== 自定义播放界面背景 ====================

    /**
     * Hook PlayerBackgroundImage.setBlurCover - 替换播放界面背景图片和模糊度
     *
     * PlayerBackgroundImage 和 setBlurCover() 在各版本中均为明文，无需混淆名回退
     */
    private fun hookPlayerBackground() {
        val clazz = findClassIfExists(
            "com.netease.cloudmusic.ui.PlayerBackgroundImage", context.classLoader
        ) ?: return
        val method = findMethodIfExists(
            clazz, "setBlurCover", String::class.java, String::class.java, Integer.TYPE
        ) ?: return

        module.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                // 替换背景图片URL和模糊度，保留原始第二参数（歌曲封面URL）
                val url = SettingHelper.getInstance().getPictureUrl()
                val originalCover = chain.getArg(1)
                val blur = SettingHelper.getInstance().getBackgroundBlur()
                return chain.proceed(arrayOf(url, originalCover, blur))
            }
        })
        LogUtils.i("$TAG: hookPlayerBackground 成功")
    }

    // ==================== 播放页黑胶隐藏和音谱移除 ====================

    /**
     * Hook PlayerActivity.onCreate - 隐藏黑胶唱片和音谱按钮
     *
     * 功能：
     * 1. 黑胶隐藏（beauty_black_hide_key）：隐藏PlayerDiscViewFlipper中的黑胶唱片，仅显示专辑封面
     * 2. 音谱移除（beauty_ksong_hide_key）：将音谱/铃声按钮的宽高设为0，使其不可见
     *
     * 参考dev分支PlayerActivityHook实现
     */
    private fun hookPlayerActivity() {
        val black = SettingHelper.getInstance().getSetting(SettingHelper.beauty_black_hide_key)
        val ksong = SettingHelper.getInstance().getSetting(SettingHelper.beauty_ksong_hide_key)
        if (!black && !ksong) return

        val playerActivityClass = findClassIfExists("com.netease.cloudmusic.activity.PlayerActivity", context.classLoader) ?: run {
            LogUtils.w("$TAG: hookPlayerActivity PlayerActivity类未找到")
            return
        }
        val onCreateMethod = findMethodIfExists(playerActivityClass, "onCreate", android.os.Bundle::class.java) ?: run {
            LogUtils.w("$TAG: hookPlayerActivity onCreate方法未找到")
            return
        }

        module.hook(onCreateMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                try {
                    val activity = chain.thisObject
                    var playerDiscViewFlipper: android.widget.ViewFlipper? = null

                    // 遍历PlayerActivity的所有字段，查找黑胶和音谱相关View
                    for (field in activity.javaClass.declaredFields) {
                        // 黑胶隐藏：查找PlayerDiscViewFlipper类型字段
                        if (black && field.type.name.contains("PlayerDiscViewFlipper")) {
                            field.isAccessible = true
                            playerDiscViewFlipper = field.get(activity) as? android.widget.ViewFlipper
                        }
                        // 音谱移除：查找ImageView类型字段，检查contentDescription是否为"音韵"或"铃声"
                        if (ksong && field.type.name.contains("ImageView")) {
                            field.isAccessible = true
                            val imageView = field.get(activity) as? android.widget.ImageView
                            if (imageView != null) {
                                val desc = imageView.contentDescription?.toString() ?: ""
                                if (desc.contains("音韵") || desc.contains("铃声")) {
                                    val layoutParams = imageView.layoutParams
                                    layoutParams.width = 0
                                    layoutParams.height = 0
                                    imageView.layoutParams = layoutParams
                                    // 同时隐藏父View
                                    (imageView.parent as? android.view.View)?.let { parent ->
                                        val parentParams = parent.layoutParams
                                        parentParams.width = 0
                                        parentParams.height = 0
                                        parent.layoutParams = parentParams
                                    }
                                    LogUtils.i("$TAG: hookPlayerActivity 隐藏音谱按钮")
                                }
                            }
                        }
                    }

                    // 黑胶隐藏：调整ViewFlipper中子View的布局
                    if (playerDiscViewFlipper != null) {
                        for (i in 0 until playerDiscViewFlipper.childCount) {
                            var coverView: android.view.View? = null
                            var imageView: android.view.View? = null
                            val rotationRelativeLayout = playerDiscViewFlipper.getChildAt(i) as android.widget.RelativeLayout
                            for (j in 0 until rotationRelativeLayout.childCount) {
                                val child = rotationRelativeLayout.getChildAt(j)
                                if (child.javaClass.name.contains("ImageView") &&
                                    child.javaClass.name.contains("android")) {
                                    coverView = child
                                } else {
                                    imageView = child
                                }
                            }
                            if (coverView != null && imageView != null) {
                                val coverViewF = coverView
                                val imageViewF = imageView
                                coverViewF.post {
                                    val layoutParams = imageViewF.layoutParams as android.widget.RelativeLayout.LayoutParams
                                    layoutParams.height = coverViewF.height
                                    layoutParams.width = coverViewF.width
                                    imageViewF.layoutParams = layoutParams
                                    coverViewF.visibility = android.view.View.INVISIBLE
                                }
                            }
                        }
                        LogUtils.i("$TAG: hookPlayerActivity 隐藏黑胶唱片")
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookPlayerActivity 异常 - ${e.message}")
                }
                return result
            }
        })
        LogUtils.i("$TAG: hookPlayerActivity 成功")
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
