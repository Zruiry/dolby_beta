/**
 * 移除播放页歌曲图谱
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BeautyKSongHideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.beauty_ksong_hide_title
        key = SettingHelper.beauty_ksong_hide_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !getCheckBoxStatus())
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
