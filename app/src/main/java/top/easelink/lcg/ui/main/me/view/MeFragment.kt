package top.easelink.lcg.ui.main.me.view

import android.content.Intent
import android.widget.Toast
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.SignInManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import coil.Coil
import coil.load
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import org.greenrobot.eventbus.EventBus
import androidx.lifecycle.lifecycleScope
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.framework.utils.addFragmentInActivity
import top.easelink.framework.utils.addFragmentInFragment
import top.easelink.framework.utils.dpToPx
import top.easelink.lcg.R
import top.easelink.lcg.account.AccountManager
import top.easelink.lcg.databinding.FragmentMeBinding
import top.easelink.lcg.ui.main.articles.view.FavoriteArticlesFragment
import top.easelink.lcg.ui.main.follow.view.FollowFragment
import top.easelink.lcg.ui.main.history.view.HistoryArticlesFragment
import top.easelink.lcg.ui.main.me.viewmodel.MeViewModel
import top.easelink.lcg.ui.main.model.OpenForumEvent
import top.easelink.lcg.ui.setting.view.SettingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.easelink.lcg.utils.WebsiteConstant.MY_ARTICLES_QUERY
import top.easelink.lcg.utils.avatar.PlaceholderDrawable
import top.easelink.lcg.utils.avatar.getDefaultAvatar
import top.easelink.lcg.utils.showMessage

class MeFragment : TopFragment(), ControllableFragment {

    private lateinit var viewModel: MeViewModel
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private var lastFetchInfoTime = 0L
        private const val MINIMUM_REQUEST_INTERVAL = 30_000 

        private var lastShowLoginHintTime = 0L
        private const val MINIMUM_HINT_SHOW_INTERVAL = 120_000 
    }

    override fun isControllable(): Boolean {
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MeViewModel::class.java]
        binding.settingBtn.setOnClickListener {
            startActivity(Intent(context, SettingActivity::class.java))
        }
        binding.btnSignInState.setOnClickListener { performManualSignIn() }
        updateIconButtons()
        registerObservers()
        setupSwipeRefresh()
        if (SystemClock.elapsedRealtime() - lastFetchInfoTime > MINIMUM_REQUEST_INTERVAL) {
            lastFetchInfoTime = SystemClock.elapsedRealtime()
            viewModel.fetchUserInfoDirect()
        }
    }

    private fun registerObservers() {
        AccountManager.isLoggedIn.observe(viewLifecycleOwner) { loggedIn ->
            updateViewState(loggedIn)
            updateSignInButton()
            if (!loggedIn
                && isAdded
                && isVisible
                && SystemClock.elapsedRealtime() - MINIMUM_HINT_SHOW_INTERVAL > lastShowLoginHintTime
            ) {
                lastShowLoginHintTime = SystemClock.elapsedRealtime()
                showMessage(R.string.login_hint_message)
            }
        }
        AccountManager.userInfo.observe(viewLifecycleOwner) { info ->
            binding.meUserName.text = info.userName
            binding.meUserGroup.text = info.groupInfo
            binding.meWuaicoin.text = info.wuaiCoin
            binding.meAnwserRate.text = info.answerRate
            binding.meCredit.text = info.credit
            binding.meEnthusiastic.text = info.enthusiasticValue
            // 论坛原生图片签到逻辑已屏蔽，改为右上角本地状态按钮
            info.avatarUrl?.let {
                binding.meUserAvatar.load(it) {
                    placeholder(PlaceholderDrawable)
                    lifecycle(viewLifecycleOwner)
                    transformations(RoundedCornersTransformation(4.dpToPx(binding.meUserAvatar.context)))
                    error(getDefaultAvatar(it))
                }
            }
        }
    }


    private fun updateSignInButton() {
        if (!AccountManager.isLoggedIn.value!!) {
            binding.btnSignInState.text = "请先登录"
            binding.btnSignInState.setTextColor(android.graphics.Color.parseColor("#999999"))
            binding.btnSignInState.isEnabled = false
            return
        }

        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        if (lastTime >= todayStart) {
            binding.btnSignInState.text = "✅ 今日已签到"
            binding.btnSignInState.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // 绿色
            binding.btnSignInState.isEnabled = false
        } else {
            binding.btnSignInState.text = "🔥 立即签到"
            binding.btnSignInState.setTextColor(android.graphics.Color.parseColor("#E53935")) // 红色
            binding.btnSignInState.isEnabled = true
        }
    }

    private fun performManualSignIn() {
        binding.btnSignInState.isEnabled = false
        binding.btnSignInState.text = "签到中..."
        SignInManager.performSignIn(requireContext()) { success, msg ->
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    val sp = SharedPreferencesHelper.getUserSp()
                    sp.edit().putLong("sp_key_last_sign_in_at", System.currentTimeMillis()).apply()
                }
                updateSignInButton()
            }
        }
    }

    private fun updateIconButtons() {
        binding.cardviewMeNotifications.iconNotifications.apply {
            root.setOnClickListener {
                showFragment(FavoriteArticlesFragment())
                badge.visibility = View.GONE
            }
            btnIcon.setImageResource(R.drawable.ic_favorite)
            tvIcon.setText(R.string.ic_favorite)
        }
        binding.cardviewMeNotifications.iconMyArticles.apply {
            root.setOnClickListener {
                EventBus
                    .getDefault()
                    .post(
                        OpenForumEvent(
                            getString(R.string.ic_my_articles), MY_ARTICLES_QUERY, false
                        )
                    )
            }
            btnIcon.setImageResource(R.drawable.ic_my_articles)
            tvIcon.setText(R.string.ic_my_articles)
        }
        binding.cardviewMeNotifications.iconFollow.apply {
            root.setOnClickListener {
                showFragment(FollowFragment())
            }
            btnIcon.setImageResource(R.drawable.ic_follow)
            tvIcon.setText(R.string.ic_follow)
        }
        binding.cardviewMeNotifications.iconHistory.apply {
            root.setOnClickListener {
                showFragment(HistoryArticlesFragment.newInstance())
            }
            btnIcon.setImageResource(R.drawable.ic_history)
            tvIcon.setText(R.string.ic_history)
        }
    }

    private fun updateViewState(loggedIn: Boolean) {
        if (loggedIn) {
            binding.iconGroup.visibility = View.VISIBLE
        } else {
            binding.iconGroup.visibility = View.GONE
        }

        val colorTab = if (loggedIn) {
            ContextCompat.getColor(mContext, R.color.black_effective)
        } else {
            ContextCompat.getColor(mContext, R.color.semi_gray)
        }
        binding.cardviewMeNotifications.iconFollow.apply {
            root.isEnabled = loggedIn
            btnIcon.setColorFilter(colorTab)
            tvIcon.setTextColor(colorTab)
        }
        binding.cardviewMeNotifications.iconMyArticles.apply {
            root.isEnabled = loggedIn
            btnIcon.setColorFilter(colorTab)
            tvIcon.setTextColor(colorTab)
        }
    }

    private fun showFragment(fragment: Fragment) {
        activity?.supportFragmentManager?.let {
            addFragmentInActivity(
                it,
                fragment,
                R.id.clRootView
            )
        }
    }

    private fun showChildFragment(fragment: Fragment) {
        addFragmentInFragment(childFragmentManager, fragment, R.id.child_root)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.apply {
            setColorSchemeColors(ContextCompat.getColor(context, R.color.pojie_logo))
            setOnRefreshListener {
                
                viewModel.fetchUserInfoDirect()
                
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(1000)
                    isRefreshing = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}