package top.easelink.lcg.ui.main.article.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import top.easelink.lcg.R
import java.util.regex.Pattern

class CodePreviewDialog : DialogFragment() {

    companion object {
        private const val EXTRA_CODE = "extra_code"
        fun newInstance(code: String): CodePreviewDialog {
            val d = CodePreviewDialog()
            d.arguments = Bundle().apply { putString(EXTRA_CODE, code) }
            return d
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_code_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rawHtml = arguments?.getString(EXTRA_CODE) ?: return
        
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.code_toolbar)
        // 修复：使用系统自带的标准返回箭头
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { dismiss() }

        // 1. 清理 HTML 标签并提取纯代码
        var cleanCode = rawHtml.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()

        // 2. 语法高亮处理
        val spannable = SpannableStringBuilder(cleanCode)
        applyHighlight(spannable, 0, cleanCode)

        val tvCode = view.findViewById<TextView>(R.id.tv_code_content)
        tvCode.text = spannable

        // 3. 复制按钮
        view.findViewById<View>(R.id.btn_copy_code).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Code", cleanCode))
            Toast.makeText(requireContext(), "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyHighlight(spannable: SpannableStringBuilder, start: Int, code: String) {
        val keywordColor = Color.parseColor("#0000FF")
        val stringColor = Color.parseColor("#A31515")
        val commentColor = Color.parseColor("#008000")
        val numberColor = Color.parseColor("#098658")

        val keywords = arrayOf("abstract","actual","annotation","as","assert","associate","async","await","break","by","catch","class","companion","const","constructor","continue","crossinline","data","delegate","delete","do","dynamic","else","enum","expect","external","false","field","file","final","finally","fixed","for","fun","get","if","import","in","infix","init","inline","inner","instanceof","interface","internal","is","lambda","lateinit","let","local","null","object","open","operator","out","override","package","param","private","property","protected","public","receiver","reified","return","sealed","set","super","suspend","tailrec","this","throw","true","try","typealias","typeof","val","value","var","vararg","when","where","while","with","void","int","char","float","double","boolean","long","short","byte","String","new","static","final","def","from")
        val kwRegex = StringBuilder("\\b(")
        keywords.forEachIndexed { index, s ->
            kwRegex.append(s)
            if (index < keywords.size - 1) kwRegex.append("|")
        }
        kwRegex.append(")\\b")

        val kwMatcher = Pattern.compile(kwRegex.toString()).matcher(code)
        while (kwMatcher.find()) {
            spannable.setSpan(ForegroundColorSpan(keywordColor), start + kwMatcher.start(), start + kwMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val strMatcher = Pattern.compile("(\".*?\"|'.*?')").matcher(code)
        while (strMatcher.find()) {
            spannable.setSpan(ForegroundColorSpan(stringColor), start + strMatcher.start(), start + strMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val commentMatcher = Pattern.compile("(//.*|/\\*[\\s\\S]*?\\*/|#.*)").matcher(code)
        while (commentMatcher.find()) {
            spannable.setSpan(ForegroundColorSpan(commentColor), start + commentMatcher.start(), start + commentMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val numMatcher = Pattern.compile("\\b\\d+(\\.\\d+)?\\b").matcher(code)
        while (numMatcher.find()) {
            spannable.setSpan(ForegroundColorSpan(numberColor), start + numMatcher.start(), start + numMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
