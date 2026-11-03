package top.easelink.lcg.ui.main.discover.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.discover.viewmodel.DiscoverViewModel
import top.easelink.lcg.ui.main.model.OpenForumEvent // 假设有跳转板块的事件
import org.greenrobot.eventbus.EventBus

class DiscoverFragment : Fragment(), ControllableFragment {

    private val viewModel: DiscoverViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        
        composeView.setContent {
            val models by viewModel.aggregationModels.observeAsState(initial = emptyList())
            
            DiscoverScreenCompose(
                models = models,
                onForumClick = { title, url ->
                    // 点击常用板块：跳转到板块帖子列表
                    // 这里需要根据 WTF 原有的逻辑进行跳转，比如发送 EventBus
                    // EventBus.getDefault().post(OpenForumEvent(title, url))
                },
                onRankClick = { url ->
                    // 点击热榜帖子：跳转到详情页
                    // EventBus.getDefault().post(OpenArticleEvent(url))
                }
            )
        }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (viewModel.aggregationModels.value.isNullOrEmpty()) {
            viewModel.initOptions(requireContext())
        }
    }
}
