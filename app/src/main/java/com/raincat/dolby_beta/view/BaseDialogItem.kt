/**
 * 基础对话框控件 - 带CheckBox的设置项
 *
 */
package com.raincat.dolby_beta.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.Tools

open class BaseDialogItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var item: BaseDialogItem? = null
    protected var contextInner: Context = context

    protected var checkBox: CheckBox
    protected var titleView: TextView
    protected var subView: TextView

    protected var title: String? = null
    protected var sub: String? = null
    protected var key: String = ""

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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.0f)
            setTextColor(Color.BLACK)
            visibility = GONE
        }
        subView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.0f)
            setTextColor(Color.DKGRAY)
            visibility = GONE
        }
        linearLayout.addView(titleView)
        linearLayout.addView(subView)

        checkBox = CheckBox(context).apply {
            isClickable = false
            visibility = GONE
        }
        addView(checkBox)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            titleView.setTextColor(Color.LTGRAY)
            subView.setTextColor(Color.LTGRAY)
        } else {
            titleView.setTextColor(Color.BLACK)
            subView.setTextColor(Color.DKGRAY)
        }
        checkBox.isEnabled = enabled
    }

    protected fun setData(showCheck: Boolean, check: Boolean) {
        if (!title.isNullOrEmpty()) {
            titleView.text = title
            titleView.visibility = VISIBLE
        }
        if (!sub.isNullOrEmpty()) {
            subView.text = sub
            subView.visibility = VISIBLE
        }
        if (showCheck) {
            checkBox.isChecked = check
            checkBox.visibility = VISIBLE
        }
    }

    /** 依附于某个item，当该item未勾选时，本item为不可选状态 */
    fun setBaseOnView(item: BaseDialogItem) {
        this.item = item
        refresh()
    }

    /** CheckBox是否勾选 - internal供子视图访问 */
    internal fun getCheckBoxStatus(): Boolean = checkBox.isChecked

    open fun refresh() {
        item?.let { setEnabled(it.getCheckBoxStatus()) }
        if (checkBox.visibility == VISIBLE)
            checkBox.isChecked = SettingHelper.getInstance().getSetting(key)
    }

    protected fun sendBroadcast(action: String) {
        contextInner.sendBroadcast(Intent(action))
    }
}
