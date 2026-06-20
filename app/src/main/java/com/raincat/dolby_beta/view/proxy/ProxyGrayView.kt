/**
 * 不变灰开关 - Hook MusicInfo.hasCopyRight() 返回 true，使无版权歌曲不变灰
 * 注意：仅影响显示效果，与是否能播放无关，会导致无音源歌曲无法播放且无法自动跳过
 */
package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class ProxyGrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_gray_title
        sub = SettingHelper.proxy_gray_sub
        key = SettingHelper.proxy_gray_key
        setData(true, SettingHelper.getInstance().getSetting(key))

        setOnClickListener {
            SettingHelper.getInstance().setSetting(key, !getCheckBoxStatus())
            sendBroadcast(SettingHelper.refresh_setting)
        }
    }
}
