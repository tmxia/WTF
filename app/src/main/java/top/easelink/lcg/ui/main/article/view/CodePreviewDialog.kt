package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
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

        // 2. 初始化 Toolbar
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.code_toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { dismiss() }

        // 3. 绑定右上角的带水波纹复制按钮
        val btnCopy = view.findViewById<ImageView>(R.id.btn_copy_code)
        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Code", rawCode))
            Toast.makeText(requireContext(), "代码已复制", Toast.LENGTH_SHORT).show()
        }

        // 4. WebView 渲染高亮
        val webView = view.findViewById<WebView>(R.id.webview_code)
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // 转义防止 HTML 注入破坏渲染
        val safeCode = rawCode.replace("<", "&lt;").replace(">", "&gt;")
        
        // 5. 完全重写的现代化 CSS 模板
        val htmlTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <!-- 引入 GitHub 风格的浅色主题 -->
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 12px;
                        background-color: #F5F5F5;
                    }
                    .code-container {
                        background-color: #FFFFFF;
                        padding: 12px;
                        border-radius: 12px;
                        /* 关键修复：允许横向滚动，绝不强制换行 */
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    }
                    pre {
                        margin: 0;
                        font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
                        font-size: 13px;
                        line-height: 1.5;
                        /* 强制保持代码原始排版，不自动折行 */
                        white-space: pre;
                    }
                    /* 移除 hljs 默认背景，让白色卡片背景生效 */
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
