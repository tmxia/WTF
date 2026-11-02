package top.easelink.lcg.utils

import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL









fun getCookies(): Map<String, String> = LCGCookieJar.cookiesForUrl(SERVER_BASE_URL)


fun getCookiesFor(url: String): Map<String, String> = LCGCookieJar.cookiesForUrl(url)


@Suppress("UNUSED_PARAMETER")
fun updateCookies(cookieUrl: String, commit: Boolean) {
    val cookies = cookieUrl.split(";")
        .mapNotNull { entry ->
            val pair = entry.trim().split("=", limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank()) pair[0] to pair[1] else null
        }
        .toMap()
    LCGCookieJar.saveCookiesFromJsoup(SERVER_BASE_URL, cookies)
}


fun updateCookies(cookies: Map<String, String>) {
    LCGCookieJar.saveCookiesFromJsoup(SERVER_BASE_URL, cookies)
}


fun clearCookies() {
    LCGCookieJar.clearAll()
}


fun Map<String, String>.toHeaderString(): String =
    entries.joinToString("; ") { "${it.key}=${it.value}" }
