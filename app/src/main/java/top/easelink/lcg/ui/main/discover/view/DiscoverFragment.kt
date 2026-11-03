package top.easelink.lcg.ui.main.discover.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.discover.viewmodel.DiscoverViewModel
import top.easelink.lcg.ui.main.forumnav.view.ForumNavigationFragment
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.model.OpenForumEvent

class DiscoverFragment : Fragment(), ControllableFragment {

    private val viewModel: DiscoverViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 创建原生的下拉刷新容器
        val swipeRefreshLayout = SwipeRefreshLayout(requireContext()).apply {
            setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary),
                ContextCompat.getColor(requireContext(), R.color.colorAccent),
                ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
            )
            setOnRefreshListener {
                viewModel.refreshOptions(requireContext())
            }
        }

        // 创建 Compose 视图
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val models by viewModel.aggregationModels.observeAsState(initial = emptyList())
                
                DiscoverScreenCompose(
                    models = models,
                    onForumClick = { title, url ->
                        EventBus.getDefault().post(OpenForumEvent(title, url, true))
                    },
                    onMoreForumsClick = {
                        EventBus.getDefault().post(ForumNavigationFragment::class.java)
                    },
                    onRankClick = { url ->
                        EventBus.getDefault().post(OpenArticleEvent(url))
                    }
                )
            }
        }

        // 把 Compose 塞进下拉刷新容器
        swipeRefreshLayout.addView(composeView)

        // 监听 isLoading，控制下拉刷新的旋转动画
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            swipeRefreshLayout.isRefreshing = isLoading
        }

        return swipeRefreshLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 强制每次进入页面都检查并拉取数据
        android.util.Log.d("Discover", "onViewCreated: 触发数据加载")
        viewModel.initOptions(requireContext())
    }
}
