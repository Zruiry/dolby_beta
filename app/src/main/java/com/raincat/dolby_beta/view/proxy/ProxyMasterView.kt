package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 代理总开关
 */
class ProxyMasterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_master_title
        key = SettingHelper.proxy_master_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked)
            ScriptHelper.initScript(contextInner, false)
            ScriptHelper.startScript()
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
