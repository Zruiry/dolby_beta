package com.raincat.dolby_beta.view.proxy.configuration

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 脚本参数配置标题视图
 */
class ScriptConfigurationTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        title = SettingHelper.script_configuration_title
        setData(false, false)
    }
}
