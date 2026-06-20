/**
 * 基础文本控件 - 用于对话框中的标题/分隔文本
 *
 * 继承自 AppCompatTextView，统一设置内边距、字号和颜色。
 */
package com.raincat.dolby_beta.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import com.raincat.dolby_beta.utils.Tools

/**
 * 基础文本对话框控件
 * 用于显示标题或说明文字，默认不可见（GONE），需手动设置文本后显示
 */
open class BaseDialogTextItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    init {
        val padding = Tools.dp2px(context, 10f)
        setPadding(padding, 8, padding, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.BLACK)
        visibility = GONE
    }
}
