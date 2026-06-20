package com.raincat.dolby_beta.view.proxy.configuration

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

/**
 * 代理端口配置
 */
class ProxyPortView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.proxy_port_title
        editView.keyListener = DigitsKeyListener.getInstance("0123456789")
        editView.filters = arrayOf(InputFilter.LengthFilter(5))
        setData(SettingHelper.getInstance().getProxyPort().toString(), SettingHelper.proxy_port_default.toString())

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.proxy_port_default.toString())
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setProxyPort(editView.text.toString())
            }
        })
    }
}
