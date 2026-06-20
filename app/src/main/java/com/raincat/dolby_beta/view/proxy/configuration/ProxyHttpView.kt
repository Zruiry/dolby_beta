package com.raincat.dolby_beta.view.proxy.configuration

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

/**
 * HTTP代理配置
 */
class ProxyHttpView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.http_proxy_title
        editView.keyListener = DigitsKeyListener.getInstance("0123456789.qwertyuiopasdfghjklzxcvbnm")
        setData(SettingHelper.getInstance().getHttpProxy(), SettingHelper.http_proxy_default)

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.http_proxy_default)
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setHttpProxy(editView.text.toString())
            }
        })
    }
}
