package top.easelink.lcg.ui.main.follow.view

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import top.easelink.lcg.ui.main.articles.view.FavoriteArticlesFragment

class FollowPagerAdapter(fm: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fm, lifecycle) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FollowingContentFragment() // 广播动态
            1 -> FollowListFragment()        // 我的收听
            else -> FollowerListFragment()   // 我的听众
        }
    }
}