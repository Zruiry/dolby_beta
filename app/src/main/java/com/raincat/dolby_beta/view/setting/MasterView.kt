/**
 * 主开关 - 模块总开关
 * 关闭后所有功能不生效，需重启网易云
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.Tools
import com.raincat.dolby_beta.view.BaseDialogItem

class MasterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.master_title
        key = SettingHelper.master_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !getCheckBoxStatus())
            sendBroadcast(SettingHelper.refresh_setting)
            Tools.showToastOnLooper(context, "打开/关闭此设置需重启网易云")
        }
    }
}
