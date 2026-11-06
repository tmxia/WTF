package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import top.easelink.framework.topbase.TopDialog
import top.easelink.lcg.databinding.DialogWafVerifyBinding
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.utils.WebsiteConstant

class WafVerificationDialog : TopDialog() {

    private var _binding: DialogWafVerifyBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(url: String): WafVerificationDialog {
            val d = WafVerificationDialog()
            d.arguments = Bundle().apply { putString("url", url) }
            return d
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogWafVerifyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 核心修复：设置悬浮窗口大小和居中
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val displayMetrics = resources.displayMetrics
            // 宽度 90%，高度 75%
            val width = (displayMetrics.widthPixels * 0.9).toInt()
            val height = (displayMetrics.heightPixels * 0.75).toInt()
            setLayout(width, height)
            setGravity(Gravity.CENTER)
        }
        // 支持点击外部关闭
        dialog?.setCanceledOnTouchOutside(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener {
            JsoupClient.completeVerification(null)
            dismissDialog()
        }

        val targetUrl = arguments?.getString("url") ?: WebsiteConstant.SERVER_BASE_URL

        binding.btnComplete.setOnClickListener {
            val latestCookie = CookieManager.getInstance().getCookie(WebsiteConstant.SERVER_BASE_URL)
            JsoupClient.completeVerification(latestCookie)
            dismissDialog()
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        binding.webView.loadUrl(targetUrl)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webView.destroy()
        _binding = null
    }
}
