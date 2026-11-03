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
import top.easelink.lcg.ui.main.article.view.PostPreviewDialog
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.forumnav.view.ForumNavigationFragment
import top.easelink.lcg.ui.main.model.OpenForumEvent

class DiscoverFragment : Fragment(), ControllableFragment {

    private val viewModel: DiscoverViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        
        var isListAtTop = true
        val swipeRefreshLayout = SwipeRefreshLayout(requireContext()).apply {
            
            setDistanceToTriggerSync(300)
            
            setSlingshotDistance(350)
            setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary),
                ContextCompat.getColor(requireContext(), R.color.colorAccent),
                ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
            )
            
            setOnChildScrollUpCallback { _, _ -> !isListAtTop }
            setOnRefreshListener {
                viewModel.refreshOptions(requireContext())
            }
        }

        
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
                ,
                onRankLongClick = { url ->
                    PostPreviewDialog.newInstance(url)
                        .show(parentFragmentManager, PostPreviewDialog.TAG)
                }
            )
            }
        }

        
        swipeRefreshLayout.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            swipeRefreshLayout.isRefreshing = isLoading
        }

        return swipeRefreshLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.initOptions(requireContext())
    }
}
