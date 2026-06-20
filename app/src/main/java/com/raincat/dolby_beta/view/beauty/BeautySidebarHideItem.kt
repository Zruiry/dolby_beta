/**
 * 侧边栏精简Item - 单个侧边栏Item的开关
 * 由 BeautySidebarHideView 的子页面动态创建
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem
import java.util.HashMap
import java.util.LinkedHashMap

class BeautySidebarHideItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    /**
     * 初始化侧边栏Item数据
     * @param sidebarMap 侧边栏枚举Map（key=标识, value=显示名称）
     * @param sidebarSettingMap 侧边栏设置Map（key=标识, value=是否隐藏）
     * @param sidebarKey 当前Item的标识
     */
    fun initData(
        sidebarMap: LinkedHashMap<String, String>,
        sidebarSettingMap: HashMap<String, Boolean>,
        sidebarKey: String
    ) {
        title = sidebarMap[sidebarKey] + "($sidebarKey)"
        key = sidebarKey
        setData(true, sidebarSettingMap[sidebarKey] ?: false)
        setOnClickListener {
            SettingHelper.getInstance().setSidebarSetting(key, !getCheckBoxStatus())
            setData(true, sidebarSettingMap[key] ?: false)
        }
    }
}
