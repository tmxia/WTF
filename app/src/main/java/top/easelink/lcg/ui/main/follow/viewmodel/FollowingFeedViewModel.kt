package top.easelink.lcg.ui.main.follow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.follow.model.FeedInfo
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.WebsiteConstant.FOLLOW_FEED_QUERY
import java.util.Locale

class FollowingFeedViewModel : ViewModel() {
    private val _follows = MutableStateFlow<List<FeedInfo>>(emptyList())
    val follows: StateFlow<List<FeedInfo>> = _follows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<FeedInfo>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<FeedInfo>>> = _uiState.asStateFlow()

    private val _isLoadingForLoadMore = MutableStateFlow(false)
    val isLoadingForLoadMore: StateFlow<Boolean> = _isLoadingForLoadMore.asStateFlow()

    var pageNum = 1

    fun fetchData() {
        val url = String.format(Locale.US, FOLLOW_FEED_QUERY, 1, 1)
        _isLoading.value = true
        _uiState.value = PageUiState.Loading
        viewModelScope.launch(IOPool) {
            try {
                parseFeeds(JsoupClient.sendAjaxRequest(url))
                if (_follows.value.isNotEmpty()) {
                    _uiState.value = PageUiState.Success(_follows.value)
                } else {
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchMore(callBack: (Boolean) -> Unit) {
        _isLoadingForLoadMore.value = true
        val url = String.format(Locale.US, FOLLOW_FEED_QUERY, pageNum, 1)
        viewModelScope.launch(IOPool) {
            try {
                val success = parseFeeds(JsoupClient.sendAjaxRequest(url))
                callBack(success)
                if (success) {
                    pageNum += 1
                }
            } catch (e: Exception) {
                Timber.e(e)
                callBack(false)
            } finally {
                _isLoadingForLoadMore.value = false
            }
        }
    }

    private fun parseFeeds(xml: String): Boolean {
        if (xml == "false") return false

        val result = xml
            .substring(xml.indexOf("[CDATA[", 0, true) + 7)
            .removeSuffix("]]></root>")

        val doc = Jsoup.parse(result)
        val feedInfos = doc.select("li.cl").map {
            val avatarUrl = it.selectFirst("a.z > img")?.attr("src").orEmpty()
            var username = ""
            var dateTime = ""
            it.selectFirst("div.flw_author")?.let { author ->
                username = author.selectFirst("a")?.text().orEmpty()
                dateTime = author.selectFirst("span")?.text().orEmpty()
            }
            val title = it.selectFirst("h2")?.text().orEmpty()
            val articleUrl = it.selectFirst("h2 > a")?.attr("href").orEmpty()

            val followImages = it.getElementsByClass("flw_image")
                .select("ul > li")
                .mapNotNull { li ->
                    li.selectFirst("img")?.attr("src")
                }

            val content = it.selectFirst(".pbm")?.let { pbm ->
                pbm.select("div.flw_image").remove()
                pbm.select("img").remove()
                pbm.select("a.flw_readfull").remove()
                pbm.html()
            }.orEmpty()

            val forum = it.selectFirst("div.xg1 > a")?.text().orEmpty()
            val quote = it.selectFirst("div.flw_quotenote")?.text().orEmpty()

            FeedInfo(
                avatar = avatarUrl,
                username = username,
                dateTime = dateTime,
                title = title,
                articleUrl = articleUrl,
                content = content,
                forum = forum,
                quote = quote,
                images = followImages
            )
        }
        _follows.value = feedInfos
        return true
    }
}
