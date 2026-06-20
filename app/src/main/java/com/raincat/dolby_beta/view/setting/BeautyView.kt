/**
 * 美化设置入口 - 点击打开美化设置页面
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BeautyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.beauty_title
        key = SettingHelper.beauty_key
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.beauty_setting)
        }
    }
}
