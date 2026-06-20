/**
 * 高斯模糊度输入 - 用于设置播放界面背景模糊程度
 */
package com.raincat.dolby_beta.view.beauty.background

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

class BackgroundBlurRadiusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.background_blur_title
        setData(SettingHelper.getInstance().getBackgroundBlur().toString(), SettingHelper.background_blur_default.toString())

        defaultView.setOnClickListener {
            editView.setText(SettingHelper.background_blur_default.toString())
            editView.setSelection(editView.text.length)
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setBackgroundBlur(editView.text.toString())
            }
        })
    }
}
