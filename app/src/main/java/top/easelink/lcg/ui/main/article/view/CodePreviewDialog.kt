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
        // 使用透明主题，让卡片自己掌控视觉
        setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_code_preview, container, false)
    }

    override fun onStart() {
        super.onStart()
        // 配置悬浮窗样式：居中，宽度占屏 85%，点击外部关闭
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog?.setCanceledOnTouchOutside(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rawHtml = arguments?.getString(EXTRA_CODE) ?: return

        // 剥离 HTML 标签提取纯代码
        rawCode = rawHtml.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()

        view.findViewById<ImageView>(R.id.btn_close).setOnClickListener { dismiss() }

        view.findViewById<View>(R.id.btn_copy_code).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Code", rawCode))
            Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        val webView = view.findViewById<WebView>(R.id.webview_code)
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val safeCode = rawCode.replace("<", "&lt;").replace(">", "&gt;")

        // 核心重构：引入现代 IDE 风格的深色高亮主题
        val htmlTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <!-- 引入 Atom One Dark 高亮主题 -->
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: transparent;
                    }
                    .code-container {
                        /* 移除背景，融入卡片颜色 */
                        padding: 0 16px 16px 16px;
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch;
                    }
                    pre {
                        margin: 0;
                        font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
                        font-size: 13px;
                        line-height: 1.5;
                        white-space: pre;
                    }
                    /* 消除 hljs 背景 */
                    .hljs {
                        background: transparent !important;
                        padding: 0 !important;
                    }
                    /* 自定义滚动条样式 */
                    ::-webkit-scrollbar {
                        height: 6px;
                    }
                    ::-webkit-scrollbar-thumb {
                        background-color: rgba(255,255,255,0.2);
                        border-radius: 3px;
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
