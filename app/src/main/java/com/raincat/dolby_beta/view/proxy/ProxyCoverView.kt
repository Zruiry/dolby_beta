package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.Tools
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 脚本释放
 */
class ProxyCoverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_cover_title
        sub = SettingHelper.proxy_cover_sub
        key = SettingHelper.proxy_cover_key
        setData(false, false)

        setOnClickListener {
            ScriptHelper.initScript(contextInner, true)
            if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)
                && !SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)
            ) {
                Tools.showToastOnLooper(contextInner, "操作成功，脚本即将重新启动")
            } else {
                Tools.showToastOnLooper(contextInner, "操作成功")
            }
            ScriptHelper.startScript()
        }
    }
}
