/**
 * 美化设置标题
 */
package com.raincat.dolby_beta.view.beauty

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogItem

class BeautyTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        title = SettingHelper.beauty_title
        setData(false, false)
    }
}
