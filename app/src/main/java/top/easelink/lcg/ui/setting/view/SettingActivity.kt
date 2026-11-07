package top.easelink.lcg.ui.setting.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.TopActivity
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import androidx.lifecycle.lifecycleScope
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.databinding.ActivitySettingsBinding
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.ui.main.login.view.LoginHintDialog
import top.easelink.lcg.ui.main.logout.view.LogoutHintDialog
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.ui.main.model.SignInSuccessEvent
import top.easelink.lcg.ui.setting.viewmodel.SettingViewModel
import top.easelink.lcg.utils.AppLogManager
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.setStatusBarPadding
import top.easelink.lcg.utils.showMessage

class SettingActivity : TopActivity() {

    private lateinit var mViewModel: SettingViewModel
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mViewModel = ViewModelProvider(this)[SettingViewModel::class.java]

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        setUp()
    }

    private fun setUp() {
        setupToolBar()
        setupComponents()
        setupObserver()
        mViewModel.init()
    }

    private fun setupToolBar() {
        binding.toolbar.setStatusBarPadding()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupComponents() {
        binding.apply {
            updateAccountButton(UserDataRepo.isLoggedIn)

            checkUpdateBtn.setOnClickListener { showMessage(R.string.app_is_latest) }

            
            btnViewCookie.setOnClickListener {
                val cookies = top.easelink.lcg.network.LCGCookieJar.cookiesForUrl(top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL)
                if (cookies.isEmpty()) {
                    showMessage("本地暂无 Cookie 数据")
                    return@setOnClickListener
                }
                
                val cookieStr = cookies.entries.joinToString(";\n") { "${it.key} = ${it.value}" }
                val clipboard = this@SettingActivity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Cookie", cookieStr)
                clipboard.setPrimaryClip(clip)
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SettingActivity)
                    .setTitle("当前 Cookie (已复制)")
                    .setMessage(cookieStr)
                    .setPositiveButton("确定", null)
                    .show()
            }

            val batteryPrefs = getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            keepAliveSwitch.isChecked = if (batteryPrefs.contains("battery_state")) batteryPrefs.getBoolean("battery_state", true) else powerManager.isIgnoringBatteryOptimizations(packageName)
            keepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
                batteryPrefs.edit().putBoolean("battery_state", isChecked).apply()
                if (isChecked) {
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }) }
                        catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                } else {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }

            val logPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isDebugEnabled = logPrefs.getBoolean("debug_mode_enabled", false)
            debugModeSwitch.isChecked = isDebugEnabled
            openLogPageBtn.visibility = if (isDebugEnabled) View.VISIBLE else View.GONE
            logDivider.visibility = if (isDebugEnabled) View.VISIBLE else View.GONE
            AppLogManager.isCollecting = isDebugEnabled

            debugModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                logPrefs.edit().putBoolean("debug_mode_enabled", isChecked).apply()
                openLogPageBtn.visibility = if (isChecked) View.VISIBLE else View.GONE
                logDivider.visibility = if (isChecked) View.VISIBLE else View.GONE
                AppLogManager.isCollecting = isChecked
            }

            openLogPageBtn.setOnClickListener { startActivity(Intent(this@SettingActivity, LogActivity::class.java)) }
            autoSignSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean -> mViewModel.scheduleJob(isChecked) }
            syncFavoritesSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean -> mViewModel.setSyncFavorite(isChecked) }

            searchEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { AppConfig.defaultSearchEngine = position }
            }
            showSearchResultInWebview.setOnCheckedChangeListener { _, isChecked -> AppConfig.searchResultShowInWebView = isChecked }
            showArticleInWebview.setOnCheckedChangeListener { _, isChecked -> AppConfig.articleShowInWebView = isChecked }
            articleHandlePreTag.setOnCheckedChangeListener { _, isChecked -> AppConfig.articleHandlePreTag = isChecked }
            showRecommendFlag.setOnCheckedChangeListener { _, isChecked -> AppConfig.articleShowRecommendFlag = isChecked }
        }
    }

    private fun setupObserver() {
        top.easelink.lcg.account.AccountManager.isLoggedIn.observe(this) { updateAccountButton(it) }
        mViewModel.apply {
            syncFavoriteEnable.observe(this@SettingActivity) { binding.syncFavoritesSwitch.isChecked = (UserDataRepo.isLoggedIn && it) }
            autoSignInEnable.observe(this@SettingActivity) { binding.autoSignSwitch.isChecked = (UserDataRepo.isLoggedIn && it) }
            searchEngineSelected.observe(this@SettingActivity) { binding.searchEngineSpinner.setSelection(it, true) }
            openSearchResultInWebView.observe(this@SettingActivity) { binding.showSearchResultInWebview.isChecked = it }
            openArticleInWebView.observe(this@SettingActivity) { binding.showArticleInWebview.isChecked = it }
            showRecommendFlag.observe(this@SettingActivity) { binding.showRecommendFlag.isChecked = it }
            handlePreTagInArticle.observe(this@SettingActivity) { binding.articleHandlePreTag.isChecked = it }
        }
    }

    private fun updateAccountButton(isLoggedIn: Boolean) {
        binding.btnViewCookie.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        
        if (!isLoggedIn) {
            with(binding) {
                syncFavoritesSwitch.isEnabled = false
                autoSignSwitch.isEnabled = false
                accountBtn.text = getString(R.string.login_btn)
                accountBtn.setOnClickListener { tryLocalCookieLoginFirst() }
            }
        } else {
            binding.accountBtn.text = String.format(getString(R.string.logout_confirm_message), UserDataRepo.username)
            binding.accountBtn.setOnClickListener { tryLogout() }
        }
    }

    private fun tryLogout() {
        LogoutHintDialog(positive = { UserDataRepo.clearAll(); showMessage(R.string.clear_cookie); finish() })
            .show(supportFragmentManager, LogoutHintDialog::class.java.simpleName)
    }

    private fun tryLocalCookieLoginFirst() {
        // 只有在用户点击登录时，才会触发这个校验流程
        val cookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn")
        if (cookies.isEmpty()) {
            showLoginOptionsDialog()
            return
        }

        showMessage("正在验证本地登录凭证...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userInfo = UserInfoRepo.requestUserInfo()
                withContext(Dispatchers.Main) {
                    if (userInfo != null && !userInfo.userName.isNullOrBlank()) {
                        UserDataRepo.updateUserInfo(userInfo)
                        UserDataRepo.isLoggedIn = true
                        showMessage("本地登录成功！欢迎回来，${userInfo.userName}")
                        updateAccountButton(true)
                    } else {
                        // 校验失败不要清 CK，弹窗让用户重登
                        showMessage("本地登录已失效，请重新登录")
                        showLoginOptionsDialog()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("网络异常，无法验证凭证")
                }
            }
        }
    }

    private fun showLoginOptionsDialog() {
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 16f * density
            setCardBackgroundColor(android.graphics.Color.WHITE)
            strokeWidth = 0
            elevation = 0f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())
        }

        val title = TextView(this).apply {
            text = "选择登录方式"
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(padding, 0, padding, (16 * density).toInt())
        }
        layout.addView(title)

        val item1 = TextView(this).apply {
            text = "跳转网页登录"
            textSize = 15f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(padding, (14 * density).toInt(), padding, (14 * density).toInt())
            val value = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            setBackgroundResource(value.resourceId)
            setOnClickListener {
                dialog.dismiss()
                LoginHintDialog().show(supportFragmentManager, null)
            }
        }
        layout.addView(item1)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
        }
        layout.addView(divider)

        val item2 = TextView(this).apply {
            text = "输入 Cookie 登录"
            textSize = 15f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(padding, (14 * density).toInt(), padding, (14 * density).toInt())
            val value = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            setBackgroundResource(value.resourceId)
            setOnClickListener {
                dialog.dismiss()
                showManualCookieDialog()
            }
        }
        layout.addView(item2)

        card.addView(layout)
        dialog.setContentView(card)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout((280 * density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showManualCookieDialog() {
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val p = (12 * density).toInt()
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 16f * density
            setCardBackgroundColor(android.graphics.Color.WHITE)
            strokeWidth = 0
            elevation = 0f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, (20 * density).toInt(), padding, (12 * density).toInt())
        }

        val title = TextView(this).apply {
            text = "手动输入 Cookie"
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        layout.addView(title)

        val tip = TextView(this).apply {
            text = "请粘贴在浏览器 (F12) 中获取的完整 Cookie"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        layout.addView(tip)

        val input = EditText(this).apply {
            hint = "key1=value1; key2=value2"
            textSize = 13f
            setSingleLine(false)
            maxLines = 5
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(p, p, p, p)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F5F5F5"))
                cornerRadius = 8f * density
            }
            background = bg
        }
        layout.addView(input)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        val btnCancel = TextView(this).apply {
            text = "取消"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener { dialog.dismiss() }
        }

        val btnConfirm = TextView(this).apply {
            text = "验证并登录"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#E53935"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener {
                val cookieStr = input.text.toString().trim()
                if (cookieStr.isNotEmpty()) {
                    dialog.dismiss()
                    performCookieLogin(cookieStr)
                } else {
                    showMessage("Cookie 不能为空")
                }
            }
        }

        btnLayout.addView(btnCancel)
        btnLayout.addView(btnConfirm)
        layout.addView(btnLayout)

        card.addView(layout)
        dialog.setContentView(card)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun performCookieLogin(cookieString: String) {
        val cookieMap = mutableMapOf<String, String>()
        cookieString.split(";").forEach { pair ->
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) cookieMap[parts[0]] = parts[1]
        }
        if (cookieMap.isEmpty()) { showMessage("Cookie 格式错误，请检查后重试"); return }

        LCGCookieJar.saveCookiesFromJsoup("https://www.52pojie.cn", cookieMap)

        showMessage("正在验证 Cookie...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userInfo = UserInfoRepo.requestUserInfo()
                withContext(Dispatchers.Main) {
                    if (userInfo != null && !userInfo.userName.isNullOrBlank()) {
                        UserDataRepo.updateUserInfo(userInfo)
                        UserDataRepo.isLoggedIn = true
                        SharedPreferencesHelper.getUserSp().edit { putLong("sp_key_last_sign_in_at", 0L) }
                        showMessage("登录成功！欢迎回来，${userInfo.userName}")
                        updateAccountButton(true)
                        EventBus.getDefault().post(SignInSuccessEvent())
                    } else {
                        LCGCookieJar.clearAll()
                        showMessage("Cookie 无效或已过期，请重新获取")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LCGCookieJar.clearAll()
                    showMessage("网络异常或 Cookie 无效")
                }
            }
        }
    }
}
