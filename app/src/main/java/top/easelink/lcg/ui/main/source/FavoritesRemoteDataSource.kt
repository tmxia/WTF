package top.easelink.lcg.ui.main.source

interface FavoritesRemoteDataSource {
    suspend fun addFavorites(threadId: String, formHash: String): Boolean

    suspend fun removeFavorites(threadId: String, formHash: String): Boolean
}
