/**
 * 打卡歌单URL输入 - 设置自助打卡的歌单URL
 */
package com.raincat.dolby_beta.view.sign

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.view.BaseDialogInputItem

class SignIdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogInputItem(context, attrs, defStyle) {

    init {
        title = SettingHelper.sign_id_title
        setData(SettingHelper.getInstance().getSignId(), "")

        defaultView.text = "清空"
        defaultView.setOnClickListener {
            editView.setText("")
        }

        editView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                SettingHelper.getInstance().setSignId(editView.text.toString())
            }
        })
    }
}
