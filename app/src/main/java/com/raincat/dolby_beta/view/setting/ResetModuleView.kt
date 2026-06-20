/**
 * 重置模块 - 清除所有设置并提示重启
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class ResetModuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = "重置模块"
        sub = "模块出现问题可以尝试重置"
        setData(false, false)

        setOnClickListener {
            SettingHelper.getInstance().resetSetting()
            sendBroadcast(SettingHelper.refresh_setting)
            Toast.makeText(context, "重置完成，手动重启网易云生效", Toast.LENGTH_SHORT).show()
        }
    }
}
