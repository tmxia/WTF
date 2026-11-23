package top.easelink.lcg.ui.search.source

import kotlinx.coroutines.delay
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.search.model.BaiduSearchResults
import top.easelink.lcg.ui.search.model.RequestTooOftenException
import top.easelink.lcg.utils.showMessage

object BaiduSearchService {
    suspend fun doSearchRequest(requestUrl: String, retryTime: Int): BaiduSearchResults = try {
        if (retryTime <= 3) {
            doSearchRequest(requestUrl)
        } else {
            BaiduSearchResults(emptyList())
        }
    } catch (e: RequestTooOftenException) {
        delay(1000)
        doSearchRequest(requestUrl, retryTime + 1)
    }

    @Throws(RequestTooOftenException::class)
    suspend fun doSearchRequest(requestUrl: String): BaiduSearchResults {
        try {
            val doc = JsoupClient.sendGetRequestWithUrl(requestUrl)
            val results = parseBaiduResults(doc)
            if (results.baiduSearchResultList.isEmpty()) {
                return BaiduSearchResults(emptyList())
            }
            return results
        } catch (e: RequestTooOftenException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e)
            showMessage(R.string.error)
        }
        return BaiduSearchResults(emptyList())
    }
}
