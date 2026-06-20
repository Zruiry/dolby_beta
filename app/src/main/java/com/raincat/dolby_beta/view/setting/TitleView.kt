/**
 * 标题视图
 *
 */
package com.raincat.dolby_beta.view.setting

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.raincat.dolby_beta.BuildConfig
import com.raincat.dolby_beta.view.BaseDialogItem

class TitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : BaseDialogItem(context, attrs, defStyle) {

    init {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        val paint: TextPaint = titleView.paint
        paint.isFakeBoldText = true

        title = "杜比大喇叭β v${BuildConfig.VERSION_NAME}"
        sub = "本模块仅供学习交流，严禁用于商业用途，请于24小时内删除。\n" +
                "注意：模块工作原理为音源替换而非破解，所以单曲付费与无版权歌曲有几率匹配错误，真心支持歌手请付费。"
        setData(false, false)
    }
}
