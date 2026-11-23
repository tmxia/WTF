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
     * 是否持有有效 Discuz 登录凭证。
     * 有效会话必须 `auth` 与 `saltkey` 成对存在且非空；吾安 cookiepre 为 `htVC_2132_`，
     * 凭证形如 `htVC_2132_auth`、`htVC_2132_saltkey`。`wzws_sid`、百度统计、lastvisit/lastact
     * 等访客 / 风控 / 埋点 cookie 一律不参与登录判定。
     */
    fun hasLoginCredential(): Boolean {
        var hasAuth = false
        var hasSaltkey = false
        for ((name, value) in cookiesForUrl(SERVER_BASE_URL)) {
            if (value.isBlank()) continue
            when (credentialBaseName(name)) {
                "auth" -> hasAuth = true
                "saltkey" -> hasSaltkey = true
            }
        }
        return hasAuth && hasSaltkey
    }

    /** 兼容旧调用点：是否存在登录凭证 cookie。 */
    fun hasLoginCookie(): Boolean = hasLoginCredential()

    /** 归一化 cookie 基名：去掉 Discuz cookiepre 前缀（如 `htVC_2132_auth` → `auth`）。 */
    private fun credentialBaseName(name: String): String {
        val lower = name.lowercase()
        val idx = lower.lastIndexOf('_')
        return if (idx >= 0) lower.substring(idx + 1) else lower
    }

    /** 访客 / 风控 / 埋点类 cookie，不作为业务凭证回灌入库。 */
    private fun isNonCredentialCookie(name: String): Boolean {
        val lower = name.lowercase()
        if (lower == "wzws_sid" || lower == "hmaccount") return true
        return lower.startsWith("hm_lvt_") || lower.startsWith("hm_lpvt_") ||
            lower.endsWith("_lastvisit") || lower.endsWith("_lastact") ||
            lower.endsWith("_viewid") || lower.endsWith("_lastip")
    }

    fun cookiesForUrl(url: String): Map<String, String> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyMap()
        return jar.loadForRequest(httpUrl).associate { it.name to it.value }
    }

    fun saveCookiesFromJsoup(url: String, cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val list = cookies.mapNotNull { (name, value) ->
            if (isNonCredentialCookie(name)) return@mapNotNull null
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

                if (value.isBlank() || name.startsWith("$") || isNonCredentialCookie(name)) return@mapNotNull null
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
