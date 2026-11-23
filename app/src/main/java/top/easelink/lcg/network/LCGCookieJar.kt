package top.easelink.lcg.network

import android.webkit.CookieManager
import androidx.core.content.edit
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

object LCGCookieJar {
    private const val LEGACY_MIGRATED_KEY = "lcg_cookie_migrated_v1"

    val jar: ClearableCookieJar by lazy {
        val persistor = SharedPrefsCookiePersistor(LCGApp.context)
        PersistentCookieJar(SetCookieCache(), persistor).also {
            migrateLegacyCookiesIfNeeded(it)
        }
    }

    /**
     * 是否持有 Discuz 登录凭证。
     * 吾安 cookiepre 为 `htVC_2132_`，凭证 cookie 名为 `htVC_2132_auth`（辅以 `htVC_2132_saltkey`）；
     * 不能只按裸 `auth` 匹配，需兼容带前缀的凭证名。判定以非空 auth 为准。
     */
    fun hasLoginCredential(): Boolean = cookiesForUrl(SERVER_BASE_URL)
        .any { (name, value) -> value.isNotBlank() && isAuthCookieName(name) }

    /** 兼容旧调用点：是否存在登录凭证 cookie。 */
    fun hasLoginCookie(): Boolean = hasLoginCredential()

    private fun isAuthCookieName(name: String): Boolean {
        val lower = name.lowercase()
        if (lower == "auth") return true
        // 形如 htVC_2132_auth / xxx_auth
        return lower.endsWith("_auth") || lower.substringAfterLast('_') == "auth"
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
                if (pair.size != 2 || pair[0].isBlank()) return@mapNotNull null
                val name = pair[0].trim()
                val value = pair[1].trim()
                // 跳过空值与属性项（path/domain/expires 等不应作为业务 cookie 入库）
                if (value.isBlank() || name.startsWith("$")) return@mapNotNull null
                buildCookie(httpUrl, name, value)
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
