package top.easelink.lcg.ui.profile.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.TopActivity
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.ui.profile.viewmodel.ProfileViewModel

class ProfileFragment : TopFragment() {
    private lateinit var mViewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                ProfileScreen(
                    onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(mContext as TopActivity)[ProfileViewModel::class.java]
    }

    companion object {
        private const val PROFILE_URL = "profile_url"

        @JvmStatic
        fun newInstance(profileUrl: String): ProfileFragment {
            return ProfileFragment().also {
                it.arguments = Bundle().apply {
                    putString(PROFILE_URL, profileUrl)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBackClick: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
             
    ) {
        
        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            TopAppBar(
                title = { 
                    Text(
                        text = "我的信息", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.Black
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0) 
            )
            
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFFE53935),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]).fillMaxWidth(),
                        height = 3.dp, color = Color(0xFFE53935)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("通知", fontSize = 15.sp, color = if (selectedTab == 0) Color(0xFFE53935) else Color(0xFF888888)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("私信", fontSize = 15.sp, color = if (selectedTab == 1) Color(0xFFE53935) else Color(0xFF888888)) }
                )
            }
        }

        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "目前没有内容可供展示", color = Color.Gray, fontSize = 14.sp)
        }
    }
}
