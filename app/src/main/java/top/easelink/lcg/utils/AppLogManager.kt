package top.easelink.lcg.utils

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class LogItem(
    val time: String,
    val level: Int,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

object AppLogManager {
    private const val MAX_LOG_SIZE = 1000
    private val logs = LinkedList<LogItem>()
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    var isCollecting = false

    private val IGNORED_TAGS = listOf(
        "UpgradeCommonTag", "RDelivery", "RDeliveryHelper", 
        "RDelivery_UpdateManager", "RDelivery_Reporter", "Bugly",
        "libc", "OpenGLRenderer", "EGL_emulation", "chatty"
    )

    fun addLog(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (!isCollecting) return
        val safeTag = tag ?: "App"
        if (IGNORED_TAGS.any { safeTag.contains(it) }) return
        
        if (logs.size >= MAX_LOG_SIZE) {
            logs.removeFirst()
        }
        val time = formatter.format(Date())
        val threadName = Thread.currentThread().name
        val stackTraceStr = if (throwable != null) Log.getStackTraceString(throwable) else null
        
        logs.add(LogItem(time, priority, "[$threadName] $safeTag", message, stackTraceStr))
    }

    fun getLogs(): List<LogItem> = logs.toList()
    fun clearLogs() = logs.clear()
    
    
    fun removeLog(time: String) {
        logs.removeAll { it.time == time }
    }

    fun exportLogs(): String {
        val sb = StringBuilder()
        sb.append("=== WTF App Logs ===\n")
        logs.forEach {
            val levelStr = when (it.level) { Log.VERBOSE -> "V"; Log.DEBUG -> "D"; Log.INFO -> "I"; Log.WARN -> "W"; Log.ERROR -> "E"; else -> "?" }
            sb.append("[${it.time}] $levelStr/${it.tag}: ${it.message}\n")
            it.stackTrace?.let { st -> sb.append("$st\n") }
        }
        return sb.toString()
    }

    class LogTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            addLog(priority, tag, message, t)
        }
    }
}
