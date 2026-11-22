package top.easelink.lcg.base

/**
 * 页面统一状态封装，供 ViewModel 以 StateFlow / LiveData 暴露。
 *
 * 现有页面仍沿用既有 LiveData 写法；新增页面或后续重构时优先使用本密封类，
 * 由 UI 层按 Loading / Success / Empty / Error 分支统一渲染。
 */
sealed class PageUiState<out T> {

    /** 加载中。 */
    object Loading : PageUiState<Nothing>()

    /** 加载成功并携带数据。 */
    data class Success<T>(val data: T) : PageUiState<T>()

    /** 数据为空。 */
    object Empty : PageUiState<Nothing>()

    /** 加载失败，附带异常与可读提示。 */
    data class Error(
        val throwable: Throwable? = null,
        val message: String? = null,
    ) : PageUiState<Nothing>()

    /** 当前成功态的数据，非成功态返回 `null`。 */
    @Suppress("UNCHECKED_CAST")
    val dataOrNull: T?
        get() = (this as? Success<T>)?.data
}
