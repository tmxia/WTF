package top.easelink.lcg.network

import android.webkit.CookieManager
import com.franmontiel.persistentcookiejar.ClearableCookieJar
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import androidx.core.content.edit












object LCGCookieJar {

    private const val LEGACY_MIGRATED_KEY = "lcg_cookie_migrated_v1"

    val jar: ClearableCookieJar by lazy {
        val persistor = SharedPrefsCookiePersistor(LCGApp.context)
        PersistentCookieJar(SetCookieCache(), persistor).also {
            migrateLegacyCookiesIfNeeded(it)
        }
    }

    
    fun cookiesForUrl(url: String): Map<String, String> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyMap()
        return jar.loadForRequest(httpUrl).associate { it.name to it.value }
    }

    



    fun saveCookiesFromJsoup(url: String, cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val list = cookies.mapNotNull { (name, value) ->
            buildCookie(httpUrl, name, value)
        }
        if (list.isNotEmpty()) jar.saveFromResponse(httpUrl, list)
    }

    



    fun syncFromWebView(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val raw = CookieManager.getInstance().getCookie(url) ?: return
        val list = raw.split(";")
            .mapNotNull { entry ->
                val pair = entry.trim().split("=", limit = 2)
                if (pair.size == 2 && pair[0].isNotBlank()) {
                    buildCookie(httpUrl, pair[0], pair[1])
                } else null
            }
        if (list.isNotEmpty()) jar.saveFromResponse(httpUrl, list)
    }

    



    fun syncToWebView() {
        val httpUrl = SERVER_BASE_URL.toHttpUrlOrNull() ?: return
        val cm = CookieManager.getInstance()
        jar.loadForRequest(httpUrl).forEach { c ->
            cm.setCookie(SERVER_BASE_URL, "${c.name}=${c.value}; path=${c.path}; domain=${c.domain}")
        }
        cm.flush()
    }

    
    fun clearAll() {
        jar.clear()
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
    }

    private fun buildCookie(httpUrl: HttpUrl, name: String, value: String): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(httpUrl.host)
            .path("/")
            .expiresAt(System.currentTimeMillis() + DEFAULT_COOKIE_TTL_MS)
            .build()
    }.onFailure { Timber.w(it, "build cookie failed: %s=%s", name, value) }.getOrNull()

    



    private fun migrateLegacyCookiesIfNeeded(target: ClearableCookieJar) {
        val userSp = SharedPreferencesHelper.getUserSp()
        if (userSp.getBoolean(LEGACY_MIGRATED_KEY, false)) return
        val legacy = SharedPreferencesHelper.getCookieSp().all
        if (legacy.isNotEmpty()) {
            val httpUrl = SERVER_BASE_URL.toHttpUrlOrNull()
            if (httpUrl != null) {
                val list = legacy.mapNotNull { (k, v) ->
                    val str = v?.toString() ?: return@mapNotNull null
                    buildCookie(httpUrl, k, str)
                }
                if (list.isNotEmpty()) {
                    target.saveFromResponse(httpUrl, list)
                    Timber.d("migrated %d legacy cookies into jar", list.size)
                }
            }
        }
        userSp.edit { putBoolean(LEGACY_MIGRATED_KEY, true) }
    }

    private const val DEFAULT_COOKIE_TTL_MS = 30L * 24 * 60 * 60 * 1000
}
