package top.easelink.lcg.ui.main.article.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.source.model.PreviewPost
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource

class PostPreviewViewModel : ViewModel() {
    private val _author = MutableStateFlow("")
    val author: StateFlow<String> = _author.asStateFlow()

    private val _avatar = MutableStateFlow("")
    val avatar: StateFlow<String> = _avatar.asStateFlow()

    private val _date = MutableStateFlow("")
    val date: StateFlow<String> = _date.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _loadingResult = MutableStateFlow(R.string.preview_loading)
    val loadingResult: StateFlow<Int> = _loadingResult.asStateFlow()

    private var currentQuery: String? = null
    private var loadJob: Job? = null

    fun initUrl(query: String) {
        currentQuery = query
        loadJob?.cancel()

        
        
        val prepared = PostPreviewLauncher.consumePrepared(query)
        if (prepared != null) {
            bindPost(prepared)
            return
        }

        _loadingResult.value = R.string.preview_loading
        loadJob = viewModelScope.launch {
            try {
                val post = withContext(Dispatchers.IO) {
                    ArticlesRemoteDataSource.getPostPreview(query)
                }

                post?.let {
                    bindPost(it)
                } ?: run {
                    _loadingResult.value = R.string.preview_fail_info
                }
            } catch (_: Exception) {
                _loadingResult.value = R.string.preview_fail_info
            }
        }
    }

    private fun bindPost(post: PreviewPost) {
        _author.value = post.author
        _avatar.value = post.avatar
        _date.value = post.date
        _content.value = post.content
        _loadingResult.value = -1
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}
