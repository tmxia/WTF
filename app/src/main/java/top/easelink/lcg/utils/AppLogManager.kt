package top.easelink.lcg.utils

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class LogItem(
    val time: String,
    val level: Int,
    val tag: String,
    val message: String
)

object AppLogManager {
    private const val MAX_LOG_SIZE = 1000
    private val logs = LinkedList<LogItem>()
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    var isCollecting = false

    // 核心降噪：第三方 SDK 黑名单（可根据实际情况继续添加）
    private val IGNORED_TAGS = listOf(
        "UpgradeCommonTag", "RDelivery", "RDeliveryHelper", 
        "RDelivery_UpdateManager", "RDelivery_Reporter", "Bugly",
        "libc", "OpenGLRenderer", "EGL_emulation", "chatty"
    )

    fun addLog(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (!isCollecting) return

        val safeTag = tag ?: "App"
        
        // 过滤掉系统与第三方无用的噪音日志
        if (IGNORED_TAGS.any { safeTag.contains(it) }) {
            return
        }
        
        if (logs.size >= MAX_LOG_SIZE) {
            logs.removeFirst()
        }
        
        val time = formatter.format(Date())
        val threadName = Thread.currentThread().name
        var finalMsg = message
        if (throwable != null) {
            finalMsg += "\n" + Log.getStackTraceString(throwable)
        }
        
        // 在 tag 前加上线程标识，方便排错
        logs.add(LogItem(time, priority, "[$threadName] $safeTag", finalMsg))
    }

    fun getLogs(): List<LogItem> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }

    fun exportLogs(): String {
        val sb = StringBuilder()
        sb.append("=== WTF App Logs ===\n")
        logs.forEach {
            val levelStr = when (it.level) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "?"
            }
            sb.append("[${it.time}] $levelStr/${it.tag}: ${it.message}\n")
        }
        return sb.toString()
    }

    // 自定义 Timber Tree
    class LogTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            addLog(priority, tag, message, t)
        }
    }
}
