package top.easelink.lcg.network

import org.jsoup.Connection
import org.jsoup.nodes.Document

interface ApiRequest {
    suspend fun sendGetRequestWithQuery(query: String): Document?

    suspend fun sendGetRequestWithUrl(url: String): Document?

    suspend fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Connection.Response
}
