@file:OptIn(ExperimentalMaterial3Api::class)
package top.easelink.lcg.ui.main.follow.view

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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewpager2.widget.ViewPager2
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment

class FollowFragment : TopFragment(), ControllableFragment {

    private var pager: ViewPager2? = null

    override fun isControllable(): Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (view as ComposeView).setContent {
            var selectedTab by remember { mutableIntStateOf(0) }

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    TopAppBar(
                        title = { Text("我的收听", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                        navigationIcon = {
                            IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                    
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = Color.White,
                        contentColor = Color(0xFFE53935),
                        divider = { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) },
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]).fillMaxWidth(),
                                height = 3.dp, color = Color(0xFFE53935)
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; pager?.setCurrentItem(0, true) },
                            text = { Text("广播动态", fontSize = 15.sp, color = if (selectedTab == 0) Color(0xFFE53935) else Color(0xFF888888), fontWeight = if (selectedTab == 0) FontWeight.Medium else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; pager?.setCurrentItem(1, true) },
                            text = { Text("我的收听", fontSize = 15.sp, color = if (selectedTab == 1) Color(0xFFE53935) else Color(0xFF888888), fontWeight = if (selectedTab == 1) FontWeight.Medium else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2; pager?.setCurrentItem(2, true) },
                            text = { Text("我的听众", fontSize = 15.sp, color = if (selectedTab == 2) Color(0xFFE53935) else Color(0xFF888888), fontWeight = if (selectedTab == 2) FontWeight.Medium else FontWeight.Normal) }
                        )
                    }
                }
                
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ViewPager2(context).apply {
                            
                            adapter = FollowViewPagerAdapter(requireActivity(), requireContext())
                            pager = this
                            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                                override fun onPageSelected(position: Int) {
                                    selectedTab = position
                                }
                            })
                        }
                    }
                )
            }
        }
    }
}
