package top.easelink.lcg.ui.main.source.local

import androidx.lifecycle.LiveData
import androidx.room.*
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.HistoryEntity






@Dao
interface ArticlesDao {

    
    





    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertArticle(articleEntity: ArticleEntity)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllArticles(articleEntities: List<ArticleEntity>)

    




    @Query("SELECT * FROM articles")
    fun getArticles(): List<ArticleEntity>

    




    @Query("DELETE FROM articles WHERE id = :id")
    fun deleteArticleById(id: String): Int

    


    @Query("DELETE FROM articles")
    fun deleteArticles()

    





    @Query("SELECT COUNT(*) FROM articles WHERE id = :id")
    fun isArticleInFavorites(id: String): Int

    

    
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getHistories(): LiveData<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(vararg historyEntity: HistoryEntity)

    @Delete
    suspend fun deleteHistory(vararg historyEntity: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteHistory(url: String): Int

    @Query("DELETE FROM history")
    suspend fun deleteHistories()

    

}