package top.easelink.lcg.ui.main.message.view

import android.graphics.Color
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayoutMediator
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.framework.utils.addFragmentInFragment
import top.easelink.lcg.R
import top.easelink.lcg.account.AccountManager
import top.easelink.lcg.databinding.FragmentMessageBinding
import top.easelink.lcg.ui.main.login.view.LoginHintFragment
import top.easelink.lcg.utils.setStatusBarPadding

class MessageFragment : TopFragment(), ControllableFragment {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun isControllable(): Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 核心修复：强制让 Toolbar 避开状态栏，防止被 MainActivity 的白色背景遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = systemBars.top)
            insets
        }
        super.onViewCreated(view, savedInstanceState)

        // 核心适配 1：像“我的帖子”一样处理沉浸式状态栏
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        AccountManager.isLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn) {
                val loginHintFragment = childFragmentManager.findFragmentByTag(LoginHintFragment::class.java.simpleName)
                if (loginHintFragment != null) {
                    childFragmentManager.beginTransaction().remove(loginHintFragment).commitNow()
                }

                binding.messageTab.visibility = View.VISIBLE
                binding.messageViewPager.visibility = View.VISIBLE

                // 强制文字颜色
                binding.messageTab.setTabTextColors(Color.parseColor("#666666"), Color.parseColor("#000000"))
                binding.messageTab.setSelectedTabIndicatorColor(Color.parseColor("#E53935"))

                if (binding.messageViewPager.adapter == null) {
                    binding.messageViewPager.adapter = MessageViewPagerAdapter(
                        requireActivity(),
                        requireContext()
                    )

                    TabLayoutMediator(binding.messageTab, binding.messageViewPager) { tab, position ->
                        tab.text = when (position) {
                            0 -> getString(R.string.tab_title_notification)
                            1 -> getString(R.string.tab_title_private_message)
                            else -> ""
                        }
                    }.attach()
                }
            } else {
                binding.messageTab.visibility = View.GONE
                binding.messageViewPager.visibility = View.GONE

                childFragmentManager.findFragmentByTag(LoginHintFragment::class.java.simpleName)?.let {
                    childFragmentManager.beginTransaction().remove(it).commitNow()
                }

                addFragmentInFragment(
                    fragmentManager = childFragmentManager,
                    fragment = LoginHintFragment(),
                    frameId = R.id.root
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
