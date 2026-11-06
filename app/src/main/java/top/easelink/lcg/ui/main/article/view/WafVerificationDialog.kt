package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import top.easelink.framework.topbase.TopDialog
import top.easelink.lcg.databinding.DialogWafVerifyBinding
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.setStatusBarPadding

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setStatusBarPadding()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener {
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
