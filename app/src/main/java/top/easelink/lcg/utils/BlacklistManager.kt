package top.easelink.lcg.utils

import android.content.Context
import top.easelink.lcg.appinit.LCGApp

object BlacklistManager {
    private const val SP_NAME = "blacklist_settings"
    private const val KEY_BLACKLIST = "blacklisted_uids"

    private val prefs by lazy {
        LCGApp.context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    fun isBlacklisted(uid: String): Boolean {
        return prefs.getStringSet(KEY_BLACKLIST, emptySet())?.contains(uid) == true
    }

    fun addToBlacklist(uid: String) {
        val set = prefs.getStringSet(KEY_BLACKLIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(uid)
        prefs.edit().putStringSet(KEY_BLACKLIST, set).apply()
    }

    fun removeFromBlacklist(uid: String) {
        val set = prefs.getStringSet(KEY_BLACKLIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(uid)
        prefs.edit().putStringSet(KEY_BLACKLIST, set).apply()
    }
}
