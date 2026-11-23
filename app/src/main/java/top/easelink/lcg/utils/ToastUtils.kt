package top.easelink.lcg.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import top.easelink.framework.utils.isOnMainThread
import top.easelink.lcg.appinit.LCGApp

// [ARCH-PHASE-9] 全局防抖变量
private var lastToastMsg: String? = null
private var lastToastTime: Long = 0L


private val mainScope = MainScope()

fun showMessage(msg: String) {
    if (isOnMainThread()) {
        Toast.makeText(LCGApp.context, msg, Toast.LENGTH_SHORT).show()
    } else {
        mainScope.launch {
            Toast.makeText(LCGApp.context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

fun showMessage(@StringRes resource: Int) {
    if (isOnMainThread()) {
        Toast.makeText(LCGApp.context, resource, Toast.LENGTH_SHORT).show()
    } else {
        mainScope.launch {
            Toast.makeText(LCGApp.context, resource, Toast.LENGTH_SHORT).show()
        }
    }
}

fun showMessage(context: Context, msg: String) {
    // [ARCH-PHASE-9] 2 秒内相同消息直接拦截
    if (msg.isBlank()) return
    val now = System.currentTimeMillis()
    if (msg == lastToastMsg && now - lastToastTime < 2000L) return
    lastToastMsg = msg
    lastToastTime = now

    if (isOnMainThread()) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    } else {
        mainScope.launch {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
