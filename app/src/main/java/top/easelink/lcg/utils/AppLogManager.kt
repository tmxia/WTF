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

    fun addLog(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (!isCollecting) return
        if (logs.size >= MAX_LOG_SIZE) {
            logs.removeFirst()
        }
        val time = formatter.format(Date())
        var finalMsg = message
        if (throwable != null) {
            finalMsg += "\n" + Log.getStackTraceString(throwable)
        }
        logs.add(LogItem(time, priority, tag ?: "App", finalMsg))
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
