/**
 * 精简侧边栏入口 - 点击打开侧边栏管理页面
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BeautySidebarHideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.beauty_sidebar_hide_title
        key = SettingHelper.beauty_sidebar_hide_key
        sub = SettingHelper.beauty_sidebar_hide_sub
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.sidebar_setting)
        }
    }
}
