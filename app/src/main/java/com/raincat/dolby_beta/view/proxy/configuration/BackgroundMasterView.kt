/**
 * 播放界面背景开关
 */
package com.raincat.dolby_beta.view.beauty.background

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BackgroundMasterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.beauty_background_title
        key = SettingHelper.beauty_background_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !getCheckBoxStatus())
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
