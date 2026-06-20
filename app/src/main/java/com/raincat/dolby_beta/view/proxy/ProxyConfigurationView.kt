package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 代理配置入口
 */
class ProxyConfigurationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_configuration_title
        key = SettingHelper.proxy_configuration_key
        sub = SettingHelper.proxy_configuration_sub
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.proxy_configuration_setting)
        }
    }
}
