package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import top.easelink.lcg.R

class CodePreviewDialog : DialogFragment() {

    private var rawCode: String = ""

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rawHtml = arguments?.getString(EXTRA_CODE) ?: return

        // 1. 剥离原有的 HTML 标签，还原纯文本代码
        rawCode = rawHtml.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()

        // 2. 初始化 Toolbar 与右上角复制按钮
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.code_toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { dismiss() }
        
        // 动态添加右上角“复制”按钮
        toolbar.menu.add(0, 1, 0, "复制").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Code", rawCode))
                Toast.makeText(requireContext(), "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        // 3. WebView 渲染高亮
        val webView = view.findViewById<WebView>(R.id.webview_code)
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // 4. 拼接高亮 HTML 模板 (利用 CDN 引入 highlight.js 和优雅的样式)
        // 为了防止 HTML 注入破坏渲染，需要将代码中的 < 和 > 进行转义
        val safeCode = rawCode.replace("<", "&lt;").replace(">", "&gt;")
        
        val htmlTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <!-- 引入 highlight.js 的浅色主题 -->
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-light.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 16px;
                        background-color: #F5F5F5; /* 整体页面背景灰 */
                    }
                    .code-container {
                        background-color: #FFFFFF; /* 代码块背景白 */
                        padding: 16px;
                        border-radius: 12px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.05);
                        overflow-x: auto; /* 支持横向滚动 */
                    }
                    pre {
                        margin: 0;
                        font-family: Consolas, "Courier New", monospace;
                        font-size: 14px;
                        line-height: 1.5;
                        white-space: pre-wrap; /* 自动换行 */
                        word-wrap: break-word;
                    }
                    /* 取消 hljs 默认的背景，避免出现突兀的色块 */
                    .hljs {
                        background: transparent !important;
                        padding: 0 !important;
                    }
                </style>
            </head>
            <body>
                <div class="code-container">
                    <pre><code class="hljs">$safeCode</code></pre>
                </div>
                <script>
                    // 自动识别语言并高亮
                    document.addEventListener('DOMContentLoaded', (event) => {
                        document.querySelectorAll('pre code').forEach((el) => {
                            hljs.highlightElement(el);
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)
    }
}
