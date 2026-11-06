package top.easelink.lcg.ui.main.article.view

import android.view.View
import androidx.fragment.app.FragmentActivity
import top.easelink.framework.customview.htmltextview.ClickablePreCodeSpan

internal class ClickablePreCodeSpanImpl : ClickablePreCodeSpan {

    constructor()

    private constructor(html: String?) {
        this.html = html
    }

    override fun newInstance(): ClickablePreCodeSpan {
        return ClickablePreCodeSpanImpl(getHtml())
    }

    override fun onClick(view: View) {
        val codeHtml = html ?: return
        val context = view.context
        if (context is FragmentActivity) {
            CodePreviewDialog.newInstance(codeHtml).show(context.supportFragmentManager, "CodePreview")
        }
    }
}
