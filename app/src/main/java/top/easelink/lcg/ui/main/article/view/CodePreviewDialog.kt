package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
        setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_code_preview, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            
            setDimAmount(0.6f)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            val displayMetrics = resources.displayMetrics
            
            
            
            val params = attributes
            params.width = (displayMetrics.widthPixels * 0.9).toInt()
            params.height = (displayMetrics.heightPixels * 0.65).toInt()
            attributes = params
            
            setGravity(android.view.Gravity.CENTER)
        }
        dialog?.setCanceledOnTouchOutside(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        
        view.findViewById<View>(R.id.dialog_root).setOnClickListener {
            dismiss()
        }

        val rawHtml = arguments?.getString(EXTRA_CODE) ?: return

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
            Toast.makeText(requireContext(), "代码已复制", Toast.LENGTH_SHORT).show()
        }

        val webView = view.findViewById<WebView>(R.id.webview_code)
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(Color.parseColor("#F8F9FA"))

        val safeCode = rawCode.replace("<", "&lt;").replace(">", "&gt;")

        val htmlTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: #F8F9FA;
                    }
                    .code-container {
                        padding: 12px;
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch;
                    }
                    pre {
                        margin: 0;
                        font-family: 'JetBrains Mono', Consolas, monospace;
                        font-size: 13px;
                        line-height: 1.5;
                        white-space: pre;
                    }
                    .hljs {
                        background: transparent !important;
                        padding: 0 !important;
                    }
                    /* 彻底隐藏所有滚动条 */
                    ::-webkit-scrollbar {
                        display: none;
                        width: 0;
                        height: 0;
                        color: transparent;
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
