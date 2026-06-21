/**
 * 关于 - 打开项目GitHub页面
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import com.raincat.dolby_beta.view.BaseDialogItem

class AboutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        title = "关于"
        setData(false, false)

        setOnClickListener {
            val uri = Uri.parse("https://github.com/nining377/dolby_beta")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }
    }
}
