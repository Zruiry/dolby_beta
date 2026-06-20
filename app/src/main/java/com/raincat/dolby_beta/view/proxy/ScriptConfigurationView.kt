package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 脚本参数配置入口视图
 */
class ScriptConfigurationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.script_configuration_title
        key = SettingHelper.script_configuration_key
        sub = SettingHelper.script_configuration_sub
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.script_configuration_setting)
        }
    }
}
