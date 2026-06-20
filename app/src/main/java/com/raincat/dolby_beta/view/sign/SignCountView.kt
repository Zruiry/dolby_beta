/**
 * 打卡数量输入 - 设置期望打卡多少首歌
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

class SignCountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.sign_count_title
        editView.keyListener = DigitsKeyListener.getInstance("0123456789")
        editView.filters = arrayOf(InputFilter.LengthFilter(5))
        setData(SettingHelper.getInstance().getSignCount().toString(), SettingHelper.sign_count_default.toString())

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.sign_count_default.toString())
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setSignCount(editView.text.toString())
            }
        })
    }
}
