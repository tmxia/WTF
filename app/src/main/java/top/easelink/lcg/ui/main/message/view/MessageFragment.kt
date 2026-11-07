package top.easelink.lcg.ui.main.message.view

import android.graphics.Color
import android.os.Bundle
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
        super.onViewCreated(view, savedInstanceState)
        
        AccountManager.isLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn) {
                val loginHintFragment = childFragmentManager.findFragmentByTag(LoginHintFragment::class.java.simpleName)
                if (loginHintFragment != null) {
                    childFragmentManager.beginTransaction().remove(loginHintFragment).commitNow()
                }

                binding.messageTab.visibility = View.VISIBLE
                binding.messageViewPager.visibility = View.VISIBLE

                // 核心修复：在代码层强制设置文字颜色，无视一切主题
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
