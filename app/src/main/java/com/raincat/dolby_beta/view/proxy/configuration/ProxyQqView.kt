package com.raincat.dolby_beta.view.proxy.configuration

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

/**
 * QQ Cookie配置视图
 */
class ProxyQqView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.qq_cookie_title
        setData(SettingHelper.getInstance().getQqCookie(), SettingHelper.qq_cookie_default)

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.qq_cookie_default)
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setQqCookie(editView.text.toString())
            }
        })
    }
}
