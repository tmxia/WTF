package top.easelink.framework.utils

import top.easelink.framework.BuildConfig

inline fun <T> debugDo(action: () -> T?): T? = if (BuildConfig.DEBUG) {
    action.invoke()
} else {
    null
}
