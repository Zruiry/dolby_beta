package com.raincat.dolby_beta.view.proxy.configuration

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

/**
 * 代理配置标题
 */
class ProxyConfigurationTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        title = SettingHelper.proxy_configuration_title
        setData(false, false)
    }
}
