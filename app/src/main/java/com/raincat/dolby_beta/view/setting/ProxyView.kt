/**
 * 音源代理入口 - 点击打开音源代理设置页面
 * 布局和功能以本项目为准
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class ProxyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_title
        key = SettingHelper.proxy_key
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.proxy_setting)
        }
    }
}
