/**
 * 打卡开始位置输入 - 设置期望从第几首开始打卡
 */
package com.raincat.dolby_beta.view.sign

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

class SignStartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.sign_start_title
        editView.keyListener = DigitsKeyListener.getInstance("0123456789")
        editView.filters = arrayOf(InputFilter.LengthFilter(5))
        setData(SettingHelper.getInstance().getSignStart().toString(), SettingHelper.sign_start_default.toString())

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.sign_start_default.toString())
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setSignStart(editView.text.toString())
            }
        })
    }
}
