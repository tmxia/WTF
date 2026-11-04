package top.easelink.lcg.ui.setting.view

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.CompoundButton
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.TopActivity
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.databinding.ActivitySettingsBinding
import top.easelink.lcg.ui.main.login.view.LoginHintDialog
import top.easelink.lcg.ui.main.logout.view.LogoutHintDialog
import top.easelink.lcg.ui.setting.viewmodel.SettingViewModel
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
            override fun handleOnBackPressed() {
                finish()
            }
        })

        setUp()
    }

    override fun onResume() {
        super.onResume()
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
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupComponents() {
        
        updateAccountButton(UserDataRepo.isLoggedIn)

        binding.apply {

            // 后台保活逻辑
            val prefs = getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            keepAliveSwitch.isChecked = if (prefs.contains("battery_state")) {
                prefs.getBoolean("battery_state", true)
            } else {
                powerManager.isIgnoringBatteryOptimizations(packageName)
            }

            keepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("battery_state", isChecked).apply()
                if (isChecked) {
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                } else {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    showMessage("请在系统设置中允许电池优化，以彻底关闭保活。")
                }
            }

            checkUpdateBtn.setOnClickListener {
                showMessage(R.string.app_is_latest)
            }

            syncFavoritesSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                mViewModel.scheduleJob(isChecked)
            }

            autoSignSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                mViewModel.setSyncFavorite(isChecked)
            }

            searchEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    AppConfig.defaultSearchEngine = position
                }
            }

            showSearchResultInWebview.setOnCheckedChangeListener { _, isChecked ->
                AppConfig.searchResultShowInWebView = isChecked
            }

            showArticleInWebview.setOnCheckedChangeListener { _, isChecked ->
                AppConfig.articleShowInWebView = isChecked
            }

            articleHandlePreTag.setOnCheckedChangeListener { _, isChecked ->
                AppConfig.articleHandlePreTag = isChecked
            }

            showRecommendFlag.setOnCheckedChangeListener { _, isChecked ->
                AppConfig.articleShowRecommendFlag = isChecked
            }
        }
    }

    private fun setupObserver() {
        
        top.easelink.lcg.account.AccountManager.isLoggedIn.observe(this) { isLoggedIn ->
            updateAccountButton(isLoggedIn)
        }
        
        
        mViewModel.apply {
            syncFavoriteEnable.observe(this@SettingActivity) { isEnabled ->
                binding.syncFavoritesSwitch.isChecked = (UserDataRepo.isLoggedIn && isEnabled)
            }

            autoSignInEnable.observe(this@SettingActivity) { isEnabled ->
                binding.autoSignSwitch.isChecked = (UserDataRepo.isLoggedIn && isEnabled)
            }

            searchEngineSelected.observe(this@SettingActivity) { position ->
                binding.searchEngineSpinner.setSelection(position, true)
            }

            openSearchResultInWebView.observe(this@SettingActivity) { isChecked ->
                binding.showSearchResultInWebview.isChecked = isChecked
            }

            openArticleInWebView.observe(this@SettingActivity) { isChecked ->
                binding.showArticleInWebview.isChecked = isChecked
            }

            showRecommendFlag.observe(this@SettingActivity) { isChecked ->
                binding.showRecommendFlag.isChecked = isChecked
            }

            handlePreTagInArticle.observe(this@SettingActivity) { isChecked ->
                binding.articleHandlePreTag.isChecked = isChecked
            }
        }
    }
    
    private fun updateAccountButton(isLoggedIn: Boolean) {
        if (!isLoggedIn) {
            with(binding) {
                syncFavoritesSwitch.isEnabled = false
                autoSignSwitch.isEnabled = false
                accountBtn.text = getString(R.string.login_btn)
                accountBtn.setOnClickListener {
                    LoginHintDialog().show(supportFragmentManager, null)
                }
            }
        } else {
            binding.accountBtn.text =
                String.format(getString(R.string.logout_confirm_message), UserDataRepo.username)
            binding.accountBtn.setOnClickListener {
                tryLogout()
            }
        }
    }

    private fun tryLogout() {
        LogoutHintDialog(
            positive = {
                UserDataRepo.clearAll()
                showMessage(R.string.clear_cookie)
                finish()
            }
        ).show(supportFragmentManager, LogoutHintDialog::class.java.simpleName)
    }
}