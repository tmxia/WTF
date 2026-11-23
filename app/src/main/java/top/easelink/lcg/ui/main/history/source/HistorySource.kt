package top.easelink.lcg.ui.main.history.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.easelink.lcg.ui.main.history.model.HistoryModel
import top.easelink.lcg.ui.main.source.local.ArticlesDatabase

object HistorySource {
    private val dao get() = ArticlesDatabase.getInstance().articlesDao()

    
    fun observeHistories(): Flow<List<HistoryModel>> =
        dao.getHistories().map { list ->
            list.map {
                HistoryModel(
                    url = it.url,
                    author = it.author,
                    title = it.title,
                    timeStamp = it.timestamp,
                )
            }
        }

    suspend fun clearHistories() = dao.deleteHistories()

    suspend fun deleteHistory(url: String) = dao.deleteHistory(url)
}
