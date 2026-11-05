package top.easelink.lcg.ui.main.article.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import top.easelink.framework.customview.htmltextview.ClickablePreCodeSpan
import top.easelink.lcg.R
import top.easelink.lcg.utils.showMessage

internal class ClickablePreCodeSpanImpl : ClickablePreCodeSpan {

    constructor()

    private constructor(html: String?) {
        this.html = html
    }

    override fun newInstance(): ClickablePreCodeSpan {
        return ClickablePreCodeSpanImpl(getHtml())
    }

    override fun onClick(view: View) {
        html?.let { code ->
            try {
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Code", code)
                clipboard.setPrimaryClip(clip)
                showMessage("代码已复制到剪贴板")
            } catch (e: Exception) {
                showMessage(R.string.general_error)
            }
        } ?: run {
            showMessage(R.string.general_error)
        }
    }
}
