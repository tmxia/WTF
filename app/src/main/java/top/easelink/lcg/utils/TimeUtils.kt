package top.easelink.lcg.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

fun toTimeStamp(date: String): Long = try {
    SimpleDateFormat.getDateTimeInstance().parse(date)?.time ?: System.currentTimeMillis()
} catch (e: ParseException) {
    System.currentTimeMillis()
}

fun String.toTimeStamp(): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd hh:mm", Locale.CHINA)
        .parse(this)
        ?.time
}.getOrNull()
