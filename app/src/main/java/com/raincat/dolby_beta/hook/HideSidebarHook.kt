/**
 * 侧边栏精简Hook
 *
 * 功能：Hook侧边栏数据加载方法，根据用户配置移除指定的侧边栏Item
 *
 * 适配高版本网易云：通过动态匹配SidebarEnum中的枚举项
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.model.SidebarEnum
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.Iterator

class HideSidebarHook(
    private val module: XposedModule,
    private val context: Context,
    private val versionCode: Int
) {
    companion object {
        private const val TAG = "HideSidebarHook"
    }

    init {
        // 仅在侧边栏精简开关开启时初始化
        if (SettingHelper.getInstance().getSetting(SettingHelper.beauty_sidebar_hide_key)) {
            try {
                hookSidebarItem()
                LogUtils.i("$TAG: 初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("$TAG: 初始化失败 - ${e.message}")
            }
        }
    }

    /**
     * Hook侧边栏数据加载方法 - 根据用户配置移除指定的侧边栏Item
     * 高版本网易云使用 MainDrawer 或类似类管理侧边栏Item
     */
    private fun hookSidebarItem() {
        // 尝试查找侧边栏适配器类（兼容不同版本网易云）
        val adapterClass = findClassIfExists("com.netease.cloudmusic.music.biz.sidebar.ui.MainDrawer", context.classLoader)
            ?: findClassIfExists("com.netease.cloudmusic.ui.MainDrawer", context.classLoader)
            ?: findClassIfExists("com.netease.cloudmusic.adapter.MainDrawerAdapter", context.classLoader)
            ?: run {
                LogUtils.w("$TAG: 未找到侧边栏类，hook失败")
                return
            }

        // 查找数据设置方法（接收List参数的方法）
        val dataMethods = adapterClass.declaredMethods.filter {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == java.util.List::class.java
        }

        for (method in dataMethods) {
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    try {
                        val argList = chain.getArg(0) as? MutableList<Any> ?: return chain.proceed()
                        val sidebarMap = SidebarEnum.getSidebarEnum()
                        val sidebarSettingMap = SettingHelper.getInstance().getSidebarSetting(sidebarMap)

                        // 更新SidebarEnum中的枚举项
                        SidebarEnum.setSidebarEnum(argList.toTypedArray())

                        // 遍历并移除用户选择隐藏的Item
                        val iterator = argList.iterator()
                        while (iterator.hasNext()) {
                            try {
                                val obj = iterator.next()
                                val enumString = obj.javaClass.getDeclaredMethod("getEnumType").invoke(obj)?.toString() ?: ""
                                if (!TextUtils.isEmpty(enumString) && enumString != "SETTING") {
                                    if (enumString == "GROUP") {
                                        val group = obj.javaClass.getDeclaredMethod("getGroup").invoke(obj) as Int
                                        if ((group == 1 && sidebarSettingMap["GROUP1"] == true) ||
                                            (group == 2 && sidebarSettingMap["GROUP2"] == true)
                                        ) {
                                            iterator.remove()
                                        }
                                    } else if (sidebarSettingMap[enumString] == true) {
                                        iterator.remove()
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略单个Item处理异常
                            }
                        }
                        // 使用修改后的参数列表执行原始方法
                        return chain.proceed(arrayOf(argList))
                    } catch (e: Throwable) {
                        LogUtils.e("$TAG: hookSidebarItem 异常 - ${e.message}")
                    }
                    return chain.proceed()
                }
            })
        }
        LogUtils.i("$TAG: hookSidebarItem 成功")
    }

    // ==================== 辅助方法 ====================

    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}
