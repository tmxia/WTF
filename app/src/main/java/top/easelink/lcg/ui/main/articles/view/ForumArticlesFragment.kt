package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.articles.viewmodel.ForumArticlesViewModel
import top.easelink.lcg.ui.main.source.model.Article

class ForumArticlesFragment : Fragment(), ControllableFragment {

    private val viewModel: ForumArticlesViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        
        composeView.setContent {
            // 先用假数据保证编译通过，看到完美界面
            val fakeArticles = listOf(
                Article(
                    title = "Electron 应用逆向分析实战：Thief 7.2.1 VIP 验证机制拆解",
                    author = "XiaoHai101",
                    date = "2026-11-2 15:06",
                    url = "https://www.52pojie.cn/thread-123456-1-1.html",
                    view = 703,
                    reply = 19,
                    origin = "『脱壳破解区』"
                ),
                Article(
                    title = "全程使用AI破解了一个浏览器的订阅",
                    author = "jsww123098",
                    date = "2026-11-2 12:53",
                    url = "https://www.52pojie.cn/thread-123457-1-1.html",
                    view = 2473,
                    reply = 71,
                    origin = "『编程语言区』"
                )
            )
            
            ArticlesListCompose(articles = fakeArticles) { url ->
                // 点击事件跳转
            }
        }
        return composeView
    }

    companion object {
        fun newInstance(title: String, url: String, showTab: Boolean = false): ForumArticlesFragment {
            val fragment = ForumArticlesFragment()
            val args = Bundle()
            args.putString("title", title)
            args.putString("url", url)
            args.putBoolean("showTab", showTab)
            fragment.arguments = args
            return fragment
        }
    }
}
