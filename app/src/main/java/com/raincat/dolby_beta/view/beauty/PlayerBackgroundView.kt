/**
 * 播放界面背景入口 - 点击打开背景设置页面
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class PlayerBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.background_title
        key = SettingHelper.background_key
        setData(false, false)

        setOnClickListener {
            sendBroadcast(SettingHelper.background_setting)
        }
    }
}
