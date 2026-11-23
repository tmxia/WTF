package top.easelink.lcg.network

/**
 * 登录凭证 / 访客 cookie 判定的纯规则集合（不依赖 Android 与网络层，便于离线单测）。
 *
 * 吾安 Discuz cookiepre 形如 `htVC_2132_`，凭证为 `htVC_2132_auth`、`htVC_2132_saltkey`；
 * `wzws_sid`、百度统计、lastvisit/lastact 等访客 / 风控 / 埋点 cookie 不参与登录判定。
 */
object CredentialRules {

    /** 参与登录判定的凭证基名。 */
    const val CREDENTIAL_AUTH = "auth"
    const val CREDENTIAL_SALTKEY = "saltkey"

    /** 访客 / 风控 / 埋点 cookie 基名（精确匹配）。 */
    private val NON_CREDENTIAL_EXACT = setOf("wzws_sid", "hmaccount")

    /** 访客 / 埋点 cookie 名前缀。 */
    private val NON_CREDENTIAL_PREFIX = listOf("hm_lvt_", "hm_lpvt_")

    /** 访客 / 埋点 cookie 名后缀。 */
    private val NON_CREDENTIAL_SUFFIX = listOf("_lastvisit", "_lastact", "_viewid", "_lastip")

    /** 归一化 cookie 基名：去掉 Discuz cookiepre 前缀（如 `htVC_2132_auth` → `auth`）。 */
    fun baseName(name: String): String {
        val lower = name.lowercase()
        val idx = lower.lastIndexOf('_')
        return if (idx >= 0) lower.substring(idx + 1) else lower
    }

    /** 访客 / 风控 / 埋点类 cookie，不作为业务凭证回灌入库。 */
    fun isNonCredential(name: String): Boolean {
        val lower = name.lowercase()
        if (lower in NON_CREDENTIAL_EXACT) return true
        if (NON_CREDENTIAL_PREFIX.any { lower.startsWith(it) }) return true
        return NON_CREDENTIAL_SUFFIX.any { lower.endsWith(it) }
    }

    /** 登录凭证是否成对齐备：auth 与 saltkey 均存在且非空。 */
    fun hasLoginPair(auth: String?, saltkey: String?): Boolean =
        !auth.isNullOrBlank() && !saltkey.isNullOrBlank()
}
