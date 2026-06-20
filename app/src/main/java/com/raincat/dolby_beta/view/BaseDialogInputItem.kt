/**
 * 基础输入对话框控件 - 带EditText的设置项
 *
 */
package com.raincat.dolby_beta.view

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.raincat.dolby_beta.utils.Tools

open class BaseDialogInputItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var item: BaseDialogItem? = null
    protected var contextInner: Context = context

    protected var titleView: TextView
    protected var defaultView: TextView
    protected var editView: EditText

    protected var title: String? = null
    protected var defaultTextValue: String? = null

    init {
        val padding = Tools.dp2px(context, 10f)
        setPadding(padding, 10, padding, 10)
        minimumHeight = Tools.dp2px(context, 40f)
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL
        addView(linearLayout)
        val layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        linearLayout.layoutParams = layoutParams

        titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.0f)
            setTextColor(Color.BLACK)
        }
        editView = EditText(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f)
            setTextColor(Color.BLACK)
            this@apply.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        linearLayout.addView(titleView)
        linearLayout.addView(editView)

        defaultView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.0f)
            setTextColor(Color.DKGRAY)
            text = "恢复默认"
        }
        addView(defaultView)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            titleView.setTextColor(Color.LTGRAY)
            editView.setTextColor(Color.LTGRAY)
            defaultView.setTextColor(Color.LTGRAY)
        } else {
            titleView.setTextColor(Color.BLACK)
            editView.setTextColor(Color.BLACK)
            defaultView.setTextColor(Color.DKGRAY)
        }
        defaultView.isEnabled = enabled
        editView.isEnabled = enabled
    }

    protected fun setData(text: String?, defaultText: String?) {
        this.defaultTextValue = defaultText
        if (!title.isNullOrEmpty())
            titleView.text = title
        if (TextUtils.isEmpty(text))
            editView.setText(defaultText)
        else
            editView.setText(text)
        editView.setSelection(editView.text.length)
    }

    /** 依附于某个item，当该item未勾选时，本item为不可选状态 */
    fun setBaseOnView(item: BaseDialogItem) {
        this.item = item
        refresh()
    }

    open fun refresh() {
        item?.let { setEnabled(it.getCheckBoxStatus()) }
    }
}
