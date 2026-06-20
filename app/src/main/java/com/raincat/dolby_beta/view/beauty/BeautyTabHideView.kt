/**
 * 精简Tab - 首页仅保留"我的"与"发现"
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BeautyTabHideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.beauty_tab_hide_title
        sub = SettingHelper.beauty_tab_hide_sub
        key = SettingHelper.beauty_tab_hide_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !getCheckBoxStatus())
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
