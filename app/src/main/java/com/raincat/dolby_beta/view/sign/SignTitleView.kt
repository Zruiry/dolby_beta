/**
 * 签到设置标题 - 可动态设置标题文本
 */
package com.raincat.dolby_beta.view.sign

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.view.BaseDialogItem

class SignTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    /**
     * 设置标题文本
     * @param title 标题内容
     */
    fun setCustomTitle(title: String) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        this.title = title
        setData(false, false)
    }
}
