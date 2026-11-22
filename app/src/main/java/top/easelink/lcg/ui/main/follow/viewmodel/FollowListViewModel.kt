package top.easelink.lcg.ui.main.follow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.follow.model.FollowInfo
import top.easelink.lcg.ui.main.follow.model.FollowResult

class FollowListViewModel : ViewModel() {
    private val _follows = MutableStateFlow<FollowResult?>(null)
    val follows: StateFlow<FollowResult?> = _follows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingForLoadMore = MutableStateFlow(false)
    val isLoadingForLoadMore: StateFlow<Boolean> = _isLoadingForLoadMore.asStateFlow()

    fun fetchData(url: String, isLoadMore: Boolean = false) {
        if (isLoadMore) {
            _isLoadingForLoadMore.value = true
        } else {
            _isLoading.value = true
        }

        viewModelScope.launch(IOPool) {
            try {
                val document = JsoupClient.sendGetRequestWithQuery(url)
                parseFollows(document)
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                if (isLoadMore) {
                    _isLoadingForLoadMore.value = false
                } else {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun parseFollows(doc: Document) {
        doc.apply {
            val followInfos = select("li.cl").map {
                val avatarUrl = it.selectFirst("img")?.attr("src").orEmpty()
                val username = it.getElementById("edit_avt")?.attr("title").orEmpty()
                val lastAction = it.selectFirst("p")?.text().orEmpty()
                val url = it.selectFirst("a[id^=a_followmod]")?.attr("href").orEmpty()

                var follower = 0
                var following = 0
                it.select("strong.xi2").takeIf { e -> e.size == 2 }?.let { e ->
                    follower = e[0].text().toIntOrNull() ?: 0
                    following = e[1].text().toIntOrNull() ?: 0
                }

                FollowInfo(
                    avatar = avatarUrl,
                    lastAction = lastAction,
                    username = username,
                    followerNum = follower,
                    followingNum = following,
                    followOrUnFollowUrl = url
                )
            }

            val nextPageUrl = selectFirst("a.nxt")?.attr("href")
            _follows.value = FollowResult(followInfos, nextPageUrl)
        }
    }
}
