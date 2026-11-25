package top.easelink.lcg.ui.webview.view

import top.easelink.lcg.utils.WebViewConfigurator

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.net.http.SslError
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import timber.log.Timber
import top.easelink.framework.customview.webview.HorizontalScrollDisableWebView
import top.easelink.framework.threadpool.CalcPool
import top.easelink.framework.threadpool.IOPool
import top.easelink.framework.threadpool.Main
import top.easelink.framework.utils.APP_USER_AGENT
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.account.UserDataRepo.updateUserInfo
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.service.web.HookInterface
import top.easelink.lcg.ui.main.MainActivity
import top.easelink.lcg.ui.main.me.viewmodel.MeProfileViewModel
import top.easelink.lcg.ui.webview.FORCE_ENABLE_JS_KEY
import top.easelink.lcg.ui.webview.OPEN_LOGIN_PAGE
import top.easelink.lcg.ui.webview.TITLE_KEY
import top.easelink.lcg.ui.webview.WebViewSecurityHelper
import top.easelink.lcg.utils.WebsiteConstant.EXTRA_TABLE_HTML
import top.easelink.lcg.utils.WebsiteConstant.LOGIN_QUERY
import top.easelink.lcg.utils.WebsiteConstant.QQ_LOGIN_URL
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.WebsiteConstant.URL_KEY
import top.easelink.lcg.utils.setStatusBarPadding
import top.easelink.lcg.utils.showMessage

class WebViewActivity : AppCompatActivity() {
    private lateinit var mWebView: WebView
    private lateinit var animationView: LottieAnimationView
    private lateinit var videoLayout: FrameLayout

    private var mUrl: String? = null
    private var mHtml: String? = null
    private var mForceEnableJs = true
    private var isOpenLoginEvent = false

    /** 登录完成只处理一次，避免 onPageCommitVisible / onPageFinished / JS 钩子三路重复触发。 */
    private val loginHandled = java.util.concurrent.atomic.AtomicBoolean(false)
    private lateinit var profileViewModel: MeProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileViewModel = ViewModelProvider(this)[MeProfileViewModel::class.java]
        initData()
        initContentView()
        initActionBar()
        initWebView()
        initBackPressHandler()
    }

    private fun initBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mWebView.canGoBack()) {
                    mWebView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_VIEW == intent.action) {
            intent.data?.toString()?.let { url ->
                mWebView.loadUrl(url)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mWebView.onPause()
    }

    override fun onResume() {
        super.onResume()
        mWebView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        (mWebView.parent as? ViewGroup)?.removeView(mWebView)
        mWebView.destroy()
    }

    private fun initContentView() {
        setContentView(R.layout.activity_web_view)
        mWebView = findViewById(R.id.web_view)
        animationView = findViewById(R.id.searching_file)
        videoLayout = findViewById(R.id.container)
    }

    private fun openInSystemBrowser(url: String) {
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(this)
        }
    }

    private fun initWebView() {
        mWebView.webViewClient = webViewClient
        mWebView.webChromeClient = InnerChromeClient()
        mWebView.setDownloadListener { url, _, _, _, _ ->
            openInSystemBrowser(url)
        }

        isOpenLoginEvent = intent.getBooleanExtra(OPEN_LOGIN_PAGE, false)
        mForceEnableJs = intent.getBooleanExtra(FORCE_ENABLE_JS_KEY, false)

        val url = mUrl
        val html = mHtml
        when {
            !url.isNullOrEmpty() -> {
                updateWebViewSettingsRemote()
                if (isOpenLoginEvent) {
                    mWebView.removeJavascriptInterface(HOOK_NAME)
                    mWebView.addJavascriptInterface(WebViewHook(), HOOK_NAME)
                }

                try {
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(mWebView, true)
                        
                        Timber.d("跳过清理 wzws_sid，保留 WAF 会话上下文")
                        
                        LCGCookieJar.syncToWebView()
                    }
                    Timber.d("已同步持久化 Cookie 到 WebView")
                } catch (e: Exception) {
                    Timber.e(e, "同步 Cookie 到 WebView 失败")
                }

                mWebView.loadUrl(url)
            }

            !html.isNullOrEmpty() -> {
                updateWebViewSettingsLocal()
                mWebView.loadDataWithBaseURL("", html, "text/html", "UTF-8", "")
            }

            else -> {
                Timber.w("WebViewActivity 缺少 URL 与 HTML 参数，直接结束")
                finish()
            }
        }
    }

    inner class WebViewHook : HookInterface {
        @JavascriptInterface
        override fun processHtml(html: String?) {
            if (html.isNullOrBlank() || !isOpenLoginEvent) return
            lifecycleScope.launch(CalcPool + CoroutineExceptionHandler { _, e ->
                Timber.e(e)
            }) {
                // 这里只负责把站点提示（如登录失败原因）透出给用户，
                // 登录是否完成由 Cookie 凭证判定，见 tryFinishLogin()。
                runCatching {
                    Jsoup.parse(html).getElementById("messagetext")?.text()
                }.getOrNull()?.takeIf { it.isNotBlank() }?.let { text ->
                    withContext(Main) { showMessage(text) }
                }
                val currentUrl = withContext(Main) { mWebView.url }
                withContext(Main) { tryFinishLogin(currentUrl) }
            }
        }
    }

    /**
     * 登录完成判定统一入口。
     *
     * 旧实现依赖页面上是否存在 `div.avt`（头像）来判断登录成功，站点改版/重定向到中转页时
     * 经常命中不了，于是“网页里已经登录成功、App 却停在原地不返回”。
     * 现在以 Cookie 凭证（auth + saltkey 成对）为唯一判据：只要有凭证，就代表会话真的拿到了。
     */
    private fun tryFinishLogin(url: String?) {
        if (!isOpenLoginEvent || loginHandled.get()) return

        val currentUrl = url ?: mWebView.url ?: SERVER_BASE_URL
        LCGCookieJar.syncFromWebView(currentUrl)
        LCGCookieJar.syncFromWebView(SERVER_BASE_URL)

        if (!LCGCookieJar.hasLoginCredential()) {
            // 还没登录完成（登录页 / 错误页 / 中转页），继续等待后续页面事件
            return
        }
        if (!loginHandled.compareAndSet(false, true)) return

        Timber.d("WebView 登录完成，回读到登录凭证，返回主界面。url=$currentUrl")
        finishLoginSuccess()
    }

    private fun finishLoginSuccess() {
        UserDataRepo.isLoggedIn = true
        runCatching {
            top.easelink.lcg.utils.SignInManager.checkSignInStatus(this) { isSigned ->
                Timber.d("登录后刷新签到状态: $isSigned")
            }
        }
        showMessage(R.string.login_successfully)

        lifecycleScope.launch {
            // 拉取资料失败绝不能阻断“回到主界面”：旧实现在这里没有兜底，
            // 一旦请求抛异常（盾拦截 / 网络异常），后面的 startActivity + finish 就不会执行，
            // 用户明明已经登录成功，却卡在网页里出不来。
            val info = runCatching {
                withContext(IOPool) { profileViewModel.fetchRemoteUserInfo() }
            }.onFailure {
                Timber.e(it, "登录后拉取资料失败，登录态已生效，直接返回主界面")
            }.getOrNull()
            info?.let(::updateUserInfo)

            delay(600)
            runCatching {
                startActivity(Intent(this@WebViewActivity, MainActivity::class.java))
                finish()
            }.onFailure { Timber.e(it, "登录完成后返回主界面失败") }
        }
    }

    private fun initData() {
        intent.data?.let { uri ->
            if (uri.scheme == "lcg") {
                mUrl = uri.toString().replace("lcg:", SERVER_BASE_URL)
                return
            }
            mUrl = uri.toString()
        }
        mUrl = mUrl ?: intent.getStringExtra(URL_KEY)
        mHtml = intent.getStringExtra(EXTRA_TABLE_HTML)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.webview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        (item.icon as? Animatable)?.start()
        return when (item.itemId) {
            R.id.action_share -> {
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)))
                true
            }

            R.id.action_open_in_webview -> {
                mWebView.url?.let(::openInSystemBrowser) ?: showMessage(R.string.general_error)
                true
            }

            android.R.id.home -> {
                finish()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private val shareIntent: Intent
        get() = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_template, mWebView.title, mWebView.url))
            putExtra(Intent.EXTRA_SUBJECT, mWebView.title)
        }

    private fun initActionBar() {
        findViewById<Toolbar>(R.id.web_view_toolbar)?.let { toolbar ->
            toolbar.setStatusBarPadding()
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setHomeButtonEnabled(true)
                setDisplayShowHomeEnabled(true)
                intent.getIntExtra(TITLE_KEY, 0).takeIf { it != 0 }?.let(::setTitle)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }

            else -> {
            }
        }
    }

    private val webViewClient: WebViewClient
        get() = InnerWebViewClient()

    private fun setLoading(isLoading: Boolean) {
        animationView.visibility = if (isLoading) View.VISIBLE else View.GONE
        mWebView.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun updateWebViewSettingsLocal() {
        // [ARCH-PHASE-5] 统一配置
        WebViewConfigurator.setupStandardWebView(mWebView)
        mWebView.settings.apply {
            WebViewSecurityHelper.applyHardening(this)
            if (mWebView is HorizontalScrollDisableWebView) {
                (mWebView as HorizontalScrollDisableWebView).setScrollEnable(true)
            }
            javaScriptEnabled = mForceEnableJs
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            blockNetworkImage = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            defaultTextEncodingName = "UTF-8"
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun updateWebViewSettingsRemote() {
        mWebView.settings.apply {
            WebViewSecurityHelper.applyHardening(this)
            
            userAgentString = APP_USER_AGENT
            javaScriptEnabled = mForceEnableJs
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            defaultTextEncodingName = "UTF-8"
            builtInZoomControls = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            blockNetworkImage = true
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private inner class InnerChromeClient : WebChromeClient() {
        private var mCustomViewCallback: CustomViewCallback? = null
        private var mCustomView: View? = null

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (mCustomView != null) {
                callback.onCustomViewHidden()
                return
            }
            mCustomView = view.apply { visibility = View.VISIBLE }
            mCustomViewCallback = callback
            videoLayout.addView(view)
            videoLayout.bringToFront()

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        override fun onHideCustomView() {
            mCustomView?.let {
                it.visibility = View.GONE
                videoLayout.removeView(it)
                mCustomView = null
                mCustomViewCallback?.onCustomViewHidden()

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private inner class InnerWebViewClient : WebViewClient() {
        override fun onPageCommitVisible(view: WebView, url: String) {
            setLoading(false)

            LCGCookieJar.syncFromWebView(url)
            if (isOpenLoginEvent) {
                view.loadUrl("javascript:$HOOK_NAME.processHtml(document.documentElement.outerHTML);")
                tryFinishLogin(url)
            }
            view.settings.blockNetworkImage = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            setLoading(false)
            if (isOpenLoginEvent) {
                // 登录页 POST 后跳转的落地页往往在 onPageCommitVisible 时 Cookie 尚未落盘，
                // onPageFinished 再判定一次，避免“已登录但 App 不返回”。
                syncLoginCookies(url)
                tryFinishLogin(url)
            }
        }

        private fun syncLoginCookies(url: String?) {
            LCGCookieJar.syncFromWebView(url ?: SERVER_BASE_URL)
            LCGCookieJar.syncFromWebView(SERVER_BASE_URL)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            view.settings.blockNetworkImage = true
            setLoading(true)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when {
                url.startsWith("wtloginmqq://") -> {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    true
                }

                url.startsWith("bdnetdisk") -> {
                    showMessage(R.string.baidu_net_disk_not_support)
                    true
                }

                WebViewSecurityHelper.isAllowedUrl(url) -> false

                url.startsWith("http://") || url.startsWith("https://") -> {
                    openInSystemBrowser(url)
                    true
                }

                else -> false
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            Timber.e("WebView 渲染进程退出，priority=${detail?.rendererPriorityAtExit()}")
            view.destroy()
            finish()
            return true
        }
    }

    companion object {
        private const val HOOK_NAME = "hook"

        fun startWebViewWith(url: String, context: Context?) {
            Intent(context ?: LCGApp.context, WebViewActivity::class.java).apply {
                putExtra(URL_KEY, url)
                putExtra(FORCE_ENABLE_JS_KEY, true)
            }.also { (context ?: LCGApp.context).startActivity(it) }
        }

        fun openLoginPage(context: Context) {
            Intent(context, WebViewActivity::class.java).apply {
                putExtra(URL_KEY, SERVER_BASE_URL + LOGIN_QUERY)
                putExtra(FORCE_ENABLE_JS_KEY, true)
                putExtra(OPEN_LOGIN_PAGE, true)
            }.also { context.startActivity(it) }
        }

        fun openQQLoginPage(context: Context) {
            Intent(context, WebViewActivity::class.java).apply {
                putExtra(URL_KEY, QQ_LOGIN_URL)
                putExtra(FORCE_ENABLE_JS_KEY, true)
                putExtra(OPEN_LOGIN_PAGE, true)
            }.also { context.startActivity(it) }
        }
    }
}
