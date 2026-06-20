/**
 * 音源代理设置标题视图 - 仅显示标题，不显示描述
 */
package com.raincat.dolby_beta.view.proxy

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.view.BaseDialogItem

class ProxyTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        title = "音源代理设置"
        sub = ""
        setData(false, false)
    }
}
