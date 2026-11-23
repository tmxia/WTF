package top.easelink.lcg.base







sealed class PageUiState<out T> {

    
    object Loading : PageUiState<Nothing>()

    
    data class Success<T>(val data: T) : PageUiState<T>()

    
    object Empty : PageUiState<Nothing>()

    
    data class Error(
        val throwable: Throwable? = null,
        val message: String? = null,
    ) : PageUiState<Nothing>()

    
    @Suppress("UNCHECKED_CAST")
    val dataOrNull: T?
        get() = (this as? Success<T>)?.data
}
