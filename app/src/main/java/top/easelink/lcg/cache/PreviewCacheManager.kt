package top.easelink.lcg.cache

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import java.io.File

object PreviewCacheManager: ICacheManager {
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val PREVIEW_CACHE_FOLDER = "${LCGApp.context.cacheDir}/preview_articles"
    private const val CONFIG_FILE_NAME = "PreviewCacheConfig"

    


    fun saveToDisk(url: String, content: String) {
        cacheScope.launch {
            checkDirs()
            val file = File(PREVIEW_CACHE_FOLDER, getCacheFileName(url))
            try {
                file.writeText(content)
            } catch (e: Exception) {
                Timber.e(e)
                file.delete()
            }
        }
    }

    


    fun findDocOrNull(url: String): Document? {
        val file: File? = findInDisk("$PREVIEW_CACHE_FOLDER/${getCacheFileName(url)}")
        if (file != null && file.exists()) {
            try {
                return Jsoup.parse(file, "utf-8")
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        return null
    }

    


    override suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        if (checkShouldCache()) {
            File(PREVIEW_CACHE_FOLDER).deleteRecursively()
            getConfigSp().edit { putLong(KEY_LAST_CLEAN_TIME, System.currentTimeMillis()) }
        }
        Unit
    }

    private fun checkDirs() {
        val parent = File(PREVIEW_CACHE_FOLDER)
        if (!parent.exists()) parent.mkdirs()
    }

    private fun findInDisk(url: String): File? {
        return if (isLocalFile(url)) {
            File(url)
        } else {
            null
        }
    }

    private fun isLocalFile(url: String): Boolean {
        return url.startsWith("/")
    }

    private fun getCacheFileName(url: String): String {
        return url.hashCode().toString()
    }

    



    private fun checkShouldCache(): Boolean {
        val last = getConfigSp().getLong(KEY_LAST_CLEAN_TIME, 0L)
        return System.currentTimeMillis() - last > CLEAN_INTERVAL_MS
    }

    private fun getConfigSp() =
        LCGApp.instance.getSharedPreferences(CONFIG_FILE_NAME, Context.MODE_PRIVATE)

    private const val KEY_LAST_CLEAN_TIME = "LastCleanTime"
    private const val CLEAN_INTERVAL_MS = 24L * 60 * 60 * 1000
}