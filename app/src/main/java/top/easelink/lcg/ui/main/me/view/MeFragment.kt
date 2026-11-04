package top.easelink.lcg.ui.main.me.view

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.framework.utils.addFragmentInActivity
import top.easelink.lcg.R
import top.easelink.lcg.account.AccountManager
import top.easelink.lcg.ui.main.articles.view.FavoriteArticlesFragment
import top.easelink.lcg.ui.main.follow.view.FollowFragment
import top.easelink.lcg.ui.main.history.view.HistoryArticlesFragment
import top.easelink.lcg.ui.main.me.viewmodel.MeViewModel
import top.easelink.lcg.ui.main.model.OpenForumEvent
import top.easelink.lcg.ui.setting.view.SettingActivity
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.SignInManager
import top.easelink.lcg.utils.WebsiteConstant.MY_ARTICLES_QUERY
import java.util.Calendar

class MeFragment : TopFragment(), ControllableFragment {
    private lateinit var viewModel: MeViewModel
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    companion object {
        private var lastFetchInfoTime = 0L
        private const val MINIMUM_REQUEST_INTERVAL = 30_000
        private var lastShowLoginHintTime = 0L
        private const val MINIMUM_HINT_SHOW_INTERVAL = 120_000
    }

    override fun isControllable(): Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = SwipeRefreshLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setColorSchemeColors(ContextCompat.getColor(context, R.color.colorPrimary))
            setDistanceToTriggerSync(250)
            setSlingshotDistance(300)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }
        
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        scrollView.addView(composeView)
        root.addView(scrollView)
        swipeRefreshLayout = root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MeViewModel::class.java]

        val swipe = view as SwipeRefreshLayout
        val scroll = swipe.getChildAt(0) as ScrollView
        val compose = scroll.getChildAt(0) as ComposeView

        compose.setContent {
            MeScreenContent(
                viewModel = viewModel,
                onSettingClick = { startActivity(Intent(requireContext(), SettingActivity::class.java)) },
                onNavigate = { fragment -> showFragment(fragment) }
            )
        }

        setupSwipeRefresh()

        if (SystemClock.elapsedRealtime() - lastFetchInfoTime > MINIMUM_REQUEST_INTERVAL) {
            lastFetchInfoTime = SystemClock.elapsedRealtime()
            viewModel.fetchUserInfoDirect()
        }

        AccountManager.isLoggedIn.observe(viewLifecycleOwner) { loggedIn ->
            if (!loggedIn && isAdded && isVisible && SystemClock.elapsedRealtime() - MINIMUM_HINT_SHOW_INTERVAL > lastShowLoginHintTime) {
                lastShowLoginHintTime = SystemClock.elapsedRealtime()
                Toast.makeText(requireContext(), R.string.login_hint_message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout?.apply {
            setOnRefreshListener {
                viewModel.fetchUserInfoDirect()
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(1000)
                    isRefreshing = false
                }
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        activity?.supportFragmentManager?.let {
            addFragmentInActivity(it, fragment, R.id.clRootView)
        }
    }
}

@Composable
fun MeScreenContent(
    viewModel: MeViewModel,
    onSettingClick: () -> Unit,
    onNavigate: (Fragment) -> Unit
) {
    val isLoggedIn by AccountManager.isLoggedIn.observeAsState(initial = false)
    val userInfo by AccountManager.userInfo.observeAsState(initial = null)
    val context = LocalContext.current

    var signInText by remember { mutableStateOf("去签到") }
    var signInColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            signInText = "请先登录"
            signInColor = Color(0xFF999999)
            return@LaunchedEffect
        }
        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        if (lastTime >= todayStart) {
            signInText = "✅ 已签到"
            signInColor = Color(0xFF4CAF50)
        } else {
            signInText = "🔥 未签到，点我"
            signInColor = Color(0xFFE53935)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(userInfo?.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        placeholder = painterResource(id = R.drawable.ic_noavatar_middle_gray),
                        error = painterResource(id = R.drawable.ic_noavatar_middle_gray),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) userInfo?.userName ?: "" else "未登录",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isLoggedIn) userInfo?.groupInfo ?: "" else "请先登录",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            
                            if (isLoggedIn) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(signInColor)
                                        .clickable(enabled = !isSigningIn && signInText.contains("未签到")) {
                                            isSigningIn = true
                                            signInText = "签到中..."
                                            SignInManager.performSignIn(context) { status, msg ->
                                                isSigningIn = false
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                if (status == "SUCCESS" || status == "ALREADY_DONE") {
                                                    val sp = SharedPreferencesHelper.getUserSp()
                                                    sp.edit().putLong("sp_key_last_sign_in_at", System.currentTimeMillis()).apply()
                                                    signInText = "✅ 已签到"
                                                    signInColor = Color(0xFF4CAF50)
                                                } else {
                                                    signInText = "🔥 未签到，点我"
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = signInText,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSettingClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_setting_light),
                            contentDescription = "Settings",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(text = "设置", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow(icon = R.drawable.ic_coin, text = "吾爱币 : ${userInfo?.wuaiCoin ?: 0}")
                            InfoRow(icon = R.drawable.ic_anwser_rate, text = "采纳率 : ${userInfo?.answerRate ?: 0} %")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow(icon = R.drawable.ic_credit_v2, text = "积分 : ${userInfo?.credit ?: 0}")
                            InfoRow(icon = R.drawable.ic_heart, text = "热心值 : ${userInfo?.enthusiasticValue ?: 0} 点")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionIcon(R.drawable.ic_my_articles, "我的帖子", isLoggedIn) {
                    EventBus.getDefault().post(OpenForumEvent(context.getString(R.string.ic_my_articles), MY_ARTICLES_QUERY, false))
                }
                ActionIcon(R.drawable.ic_follow, "我的收听", isLoggedIn) {
                    onNavigate(FollowFragment())
                }
                ActionIcon(R.drawable.ic_favorite, "我的收藏", true) {
                    onNavigate(FavoriteArticlesFragment())
                }
                ActionIcon(R.drawable.ic_history, "阅读记录", true) {
                    onNavigate(HistoryArticlesFragment.newInstance())
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
fun ActionIcon(icon: Int, text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) Color.Black else Color.LightGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = text, fontSize = 12.sp, color = color)
    }
}
