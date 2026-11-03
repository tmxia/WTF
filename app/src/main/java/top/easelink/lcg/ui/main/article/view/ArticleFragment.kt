package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel

class ArticleFragment : Fragment(), ControllableFragment {

    private val viewModel: ArticleViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        
        composeView.setContent {
            ArticleScreen(
                title = viewModel.title.value ?: "帖子详情",
                onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }
            ) {
                // 无缝桥接原生 RecyclerView，彻底交由 Compose 托管生命周期
                ComposeRecyclerViewBridge(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    fragment = this
                )
            }
        }
        return composeView
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.Black)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* 更多菜单 */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                // 一条极细的分隔线
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        }
    ) { paddingValues ->
        // 整个下层背景设置为浅灰，凸显白色卡片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            content()
        }
    }
}
