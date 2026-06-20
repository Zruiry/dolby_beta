/**
 * 自助打卡 - 点击打开自助打卡对话框
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.helper.SignSongHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class SignSongSelfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.sign_self_title
        setData(false, false)

        setOnClickListener {
            SignSongHelper.showSelfSignDialog(context)
        }
    }
}
