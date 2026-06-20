/**
 * 图片URL输入 - 用于自定义播放界面背景图片
 */
package com.raincat.dolby_beta.view.beauty.background

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

class BackgroundPictureUrlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.background_url_title
        setData(SettingHelper.getInstance().getPictureUrl(), SettingHelper.background_url_default)

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.background_url_default)
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setPictureUrl(editView.text.toString())
            }
        })
    }
}
