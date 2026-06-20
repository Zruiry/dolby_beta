package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 无损开关
 */
class ProxyFlacView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_flac_title
        sub = SettingHelper.proxy_flac_sub
        key = SettingHelper.proxy_flac_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked)
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
