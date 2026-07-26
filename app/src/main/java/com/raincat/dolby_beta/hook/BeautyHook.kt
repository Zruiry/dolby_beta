/**
 * 美化Hook集合 - 精简Tab功能
 *
 * 功能：
 * 1. 精简Tab（HideTab）- 仅保留"我的"与"首页"
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

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
            if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_tab_hide_key)) {
                hookHideTab()
            }
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
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

        // 通用特征匹配：查找返回 List 的 public 无参方法（获取Tab列表）
        // BottomTabManager 中有多个返回 List 的无参方法：
        // - h() 是 public，返回 List<BottomTabInfoVO>（Tab信息列表，需要hook的目标）
        // - f()/i()/l()/n() 是 private，返回 List<String>（tabCode字符串列表）
        // 通过 public 可见性过滤，精确匹配 h()，不依赖方法名
        val hMethod = bottomNavClass.declaredMethods.firstOrNull {
            java.lang.reflect.Modifier.isPublic(it.modifiers) &&
            it.parameterTypes.isEmpty() &&
            List::class.java.isAssignableFrom(it.returnType)
        } ?: run {
            LogUtils.w("$TAG: hookHideTab 未找到返回List的public无参方法")
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

        // Hook MainActivity 中决定默认Tab的方法 - 使默认选中"我的"Tab
        // Nc()中：if (Nf()/Of()) 选中mine(s4("mine"))，否则选中main(s4("main"))
        // 9.5.25: Nc() 调用 Nf()（返回Boolean）
        // 9.5.30: Nc() 改为调用 Of()（返回Boolean），Nf() 变为不同功能（返回boolean）
        // hookDefaultTab 内部通过返回类型 Boolean 自动匹配正确方法
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
                        // 通用特征匹配：查找 (s4|t4)(String): int 方法（tabCode转position）
                        val tabIndexMethod = mClass.declaredMethods.firstOrNull {
                            it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == String::class.java &&
                                it.returnType == Int::class.javaPrimitiveType
                        }
                        if (tabIndexMethod != null) {
                            tabIndexMethod.isAccessible = true
                            mineIndex = tabIndexMethod.invoke(null, "mine") as? Int ?: -1
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
     * Hook MainActivity 中决定默认Tab的方法 - 返回true使默认选中"我的"Tab
     *
     * 默认Tab选择逻辑（以9.5.25为例，其他版本方法名不同但逻辑一致）：
     *   if (intent.getIntExtra("SELECT_PAGE_INDEX", -1) == -1) {
     *       if (决定默认Tab的方法().booleanValue()) {
     *           intent.putExtra("SELECT_PAGE_INDEX", s4/t4("mine"));  // 选中我的
     *       } else {
     *           intent.putExtra("SELECT_PAGE_INDEX", s4/t4("main"));  // 选中首页
     *       }
     *   }
     *   Mc/Oc(intent);
     *
     * 版本差异（混淆方法名变化）：
     * - 9.5.25: Nf() 返回 Boolean，逻辑为 !m4.u() || av.T()
     * - 9.5.30: Of() 返回 Boolean，逻辑为 !l4.u() || av.T()
     * - 9.5.35: Rf() 返回 Boolean，逻辑为 !l4.u() || bv.T()
     *
     * 通用特征匹配方案：查找 MainActivity 中返回 java.lang.Boolean（包装类型）的无参方法。
     * 经反编译源码验证，9.5.25/9.5.30/9.5.35 中 MainActivity 仅有1个返回 Boolean 的无参方法，
     * 即决定默认Tab的方法。通过返回类型 Boolean（包装类型，非基本类型 boolean）自动区分：
     * - 9.5.25 的 Nf() 返回 Boolean → 匹配
     * - 9.5.30 的 Of() 返回 Boolean → 匹配；Nf() 返回 boolean → 不匹配
     * - 9.5.35 的 Rf() 返回 Boolean → 匹配
     *
     * Mc/Oc(Intent) 逻辑：
     *   int intExtra = wd()/yd() ? 0 : intent.getIntExtra("SELECT_PAGE_INDEX", -1);
     *   if (intExtra < this.mPagerAdapter.getLength() && intExtra >= 0) {
     *       setCurrentPage(intExtra, false);  // 设置ViewPager2的position
     *   }
     *
     * 方案：hook 目标方法返回true，使默认Tab选择方法中SELECT_PAGE_INDEX=s4/t4("mine")=0（过滤后索引）
     */
    private fun hookDefaultTab() {
        val mainActivityClass = findClassIfExists(
            "com.netease.cloudmusic.activity.MainActivity", context.classLoader
        ) ?: run {
            LogUtils.w("$TAG: hookDefaultTab 未找到 MainActivity")
            return
        }

        // 通用特征匹配：查找返回 java.lang.Boolean（包装类型）的无参方法
        // 经反编译源码验证，MainActivity 中仅有1个此特征的方法，即决定默认Tab的方法
        // 注意：必须区分 Boolean（包装类型）和 boolean（基本类型），后者是其他功能方法
        val targetMethod = mainActivityClass.declaredMethods.firstOrNull {
            it.parameterTypes.isEmpty() &&
            it.returnType == java.lang.Boolean::class.java
        } ?: run {
            LogUtils.w("$TAG: hookDefaultTab 未找到返回Boolean的无参方法")
            return
        }

        val methodName = targetMethod.name
        module.hook(targetMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                LogUtils.i("$TAG: hookDefaultTab $methodName() -> true")
                return true
            }
        })
        LogUtils.i("$TAG: hookDefaultTab 成功，hook 方法: $methodName")
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
                    // 通用特征匹配：查找 (p4|q4)(int): String 方法（position转tabCode）
                    val tabCodeMethod = mClass?.declaredMethods?.firstOrNull {
                        it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                            it.returnType == String::class.java
                    }
                    tabCodeMethod?.isAccessible = true
                    tabCodeMethod?.invoke(null, position) as? String
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
