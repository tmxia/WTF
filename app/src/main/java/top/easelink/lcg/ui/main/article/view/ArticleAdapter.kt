package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import top.easelink.framework.base.BaseViewHolder
import top.easelink.framework.customview.htmltextview.DrawPreCodeSpan
import top.easelink.framework.customview.htmltextview.HtmlCoilImageGetter
import top.easelink.framework.utils.*
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo.isLoggedIn
import top.easelink.lcg.account.UserDataRepo.username
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.databinding.ItemLoadMoreViewBinding
import top.easelink.lcg.databinding.ItemPostViewBinding
import top.easelink.lcg.databinding.ItemReplyViewBinding
import top.easelink.lcg.databinding.LayoutSkeltonArticleBinding
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.model.OpenLargeImageViewEvent
import top.easelink.lcg.ui.main.model.ReplyPostEvent
import top.easelink.lcg.ui.main.model.ScreenCaptureEvent
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.profile.model.PopUpProfileInfo
import top.easelink.lcg.ui.profile.view.KEY_PROFILE_URL
import top.easelink.lcg.ui.profile.view.PopUpProfileDialog
import top.easelink.lcg.ui.profile.view.ProfileActivity
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.avatar.PlaceholderDrawable
import top.easelink.lcg.utils.avatar.getDefaultAvatar
import top.easelink.lcg.utils.copyContent
import top.easelink.lcg.utils.saveImageToGallery
import top.easelink.lcg.utils.showMessage
import top.easelink.lcg.utils.toTimeStamp
import java.util.*

class ArticleAdapter(
    private val mListener: ArticleAdapterListener,
    private val mFragment: Fragment,
    private val onItemClick: ((Post) -> Unit)? = null
) : RecyclerView.Adapter<BaseViewHolder>() {

    private val mPostList: MutableList<Post> = ArrayList()

    



        private var hasMoreItem: Boolean = false
    var isFiltering: Boolean = false
    var searchKeyword: String = ""



    
        fun setData(posts: List<Post>, hasMore: Boolean = false, forceRefresh: Boolean = false) {
        // 核心双重防闪：如果长度一致、状态一致，且没有被强制要求刷新
        if (!forceRefresh && mPostList.size == posts.size && this.hasMoreItem == hasMore) {
            var isSame = true
            for (i in posts.indices) {
                // 只要 URL 没变，我们就认为楼层没变，哪怕时间或内容有微小变化也不刷新
                if (mPostList[i].replyUrl != posts[i].replyUrl) {
                    isSame = false
                    break
                }
            }
            if (isSame) {
                return
            }
        }
        
        mPostList.clear()
        mPostList.addAll(posts)
        this.hasMoreItem = hasMore
        notifyDataSetChanged()
    }

    
    private fun getRealPost(position: Int): Post? {
        
        return mPostList.getOrNull(position)
    }

    override fun getItemCount() = when {
        mPostList.isEmpty() -> 0 
        hasMoreItem -> mPostList.size + 1
        else -> mPostList.size
    }

    override fun getItemViewType(position: Int) = when {
        mPostList.isEmpty() -> VIEW_TYPE_EMPTY
        // 核心修复：如果处于过滤状态，全部用回复卡片渲染，防止首条被当成楼主导致高度坍塌或崩溃
        isFiltering -> VIEW_TYPE_REPLY
        position == 0 -> VIEW_TYPE_POST
        position == mPostList.size -> VIEW_TYPE_LOAD_MORE
        else -> VIEW_TYPE_REPLY
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_POST -> {
                val binding = ItemPostViewBinding.inflate(inflater, parent, false)
                PostViewHolder(binding)
            }
            VIEW_TYPE_REPLY -> {
                val binding = ItemReplyViewBinding.inflate(inflater, parent, false)
                ReplyViewHolder(binding)
            }
            VIEW_TYPE_LOAD_MORE -> {
                val binding = ItemLoadMoreViewBinding.inflate(inflater, parent, false)
                LoadMoreViewHolder(binding)
            }
            else -> {
                val binding = LayoutSkeltonArticleBinding.inflate(inflater, parent, false)
                PostEmptyViewHolder(binding.root)
            }
        }
    }

    
    @SuppressLint("NotifyDataSetChanged")
    fun setItems(postList: List<Post>) {
        mPostList.clear()
        mPostList.addAll(postList)
        notifyDataSetChanged()
    }

    
    fun appendItems(newPosts: List<Post>) {
        if (newPosts.isEmpty()) return
        val start = mPostList.size
        mPostList.addAll(newPosts)
        notifyItemRangeInserted(start, newPosts.size)
    }

    
    fun insertItem(position: Int, post: Post) {
        val safePos = position.coerceIn(0, mPostList.size)
        mPostList.add(safePos, post)
        notifyItemInserted(safePos)
    }

    
    fun setHasMore(hasMore: Boolean) {
        if (hasMoreItem == hasMore) return
        val wasShowing = hasMoreItem
        hasMoreItem = hasMore
        
        when {
            hasMore && !wasShowing && mPostList.isNotEmpty() -> notifyItemInserted(mPostList.size)
            !hasMore && wasShowing && mPostList.isNotEmpty() -> notifyItemRemoved(mPostList.size)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearItems() {
        mPostList.clear()
        hasMoreItem = false
        notifyDataSetChanged()
    }

    inner class PostViewHolder internal constructor(
        private val binding: ItemPostViewBinding
    ) : BaseViewHolder(binding.root), View.OnClickListener {

        private var post: Post? = null
        private val htmlHttpImageGetter: Html.ImageGetter = HtmlCoilImageGetter(binding.root.context, binding.contentTextView, mFragment)

        override fun onBind(position: Int) {
            val p = mPostList.getOrNull(position) ?: return
            post = p
            with(binding) {
                try {
                    authorTextView.text = p.author
                    dateTextView.text = getDateDiff(p.date)
                    postAvatar.setOnClickListener { _ ->
                        mFragment.runCatching { parentFragmentManager }
                            .getOrNull()
                            ?.let {
                                val location = IntArray(2)
                                postAvatar.getLocationInWindow(location)
                                val popUpInfo = PopUpProfileInfo(
                                    location[0],
                                    location[1],
                                    p.avatar,
                                    p.author,
                                    p.extraInfo,
                                    p.followInfo,
                                    p.profileUrl
                                )
                                PopUpProfileDialog.newInstance(popUpInfo).show(
                                    it,
                                    PopUpProfileDialog::class.java.simpleName
                                )
                            }
                            ?: root.context.startActivity(
                                Intent(root.context, ProfileActivity::class.java).also {
                                    it.putExtra(KEY_PROFILE_URL, p.profileUrl)
                                })
                    }
                    postAvatar.load(p.avatar) {
                        crossfade(false)
                        lifecycle(mFragment)
                        transformations(RoundedCornersTransformation(4.dpToPx(root.context)))
                        error(R.drawable.ic_noavatar_middle_gray)
                    }
                    contentTextView.run {
                        requestFocus()
                        // 核心修复：点击正文文字直接触发跳转
                        setOnClickListener { if (onItemClick != null) onItemClick.invoke(p) }
                        if (AppConfig.articleHandlePreTag) {
                            setClickablePreCodeSpan(ClickablePreCodeSpanImpl())
                            setDrawPreCodeSpan(DrawPreCodeSpan().apply {
                                tableLinkText = context.getString(R.string.tap_for_code)
                            })
                        } else {
                            setClickablePreCodeSpan(null)
                            setDrawPreCodeSpan(null)
                        }
                        setImageTagClickListener { _: Context, imageUrl: String, _: Int ->
                            EventBus.getDefault().post(OpenLargeImageViewEvent(imageUrl))
                        }
                        setOnLinkTagClickListener { c: Context?, url: String ->
                            if (url.startsWith(SERVER_BASE_URL + "thread")) {
                                EventBus.getDefault()
                                    .post(OpenArticleEvent(url.substring(SERVER_BASE_URL.length)))
                            } else {
                                WebViewActivity.startWebViewWith(url, c)
                            }
                        }
                        // 核心修复：在搜索/过滤状态下，只提取纯文本摘要，避免复杂 HTML 导致测量塌陷不显示
                        if (isFiltering) {
                            val plainText = org.jsoup.Jsoup.parse(p.content ?: "").text()
                            var snippet = plainText
                            
                            // 截取关键字附近的内容（前后各 40 字）
                            if (searchKeyword.isNotBlank() && plainText.contains(searchKeyword, ignoreCase = true)) {
                                val index = plainText.indexOf(searchKeyword, ignoreCase = true)
                                val start = maxOf(0, index - 40)
                                val end = minOf(plainText.length, index + searchKeyword.length + 40)
                                snippet = plainText.substring(start, end)
                                if (start > 0) snippet = "...$snippet"
                                if (end < plainText.length) snippet = "$snippet..."
                            } else if (plainText.length > 150) {
                                snippet = plainText.substring(0, 150) + "..."
                            }
                            setHtml(highlightKeywords(snippet, searchKeyword), htmlHttpImageGetter)
                        } else {
                            // 正常状态下，渲染完整 HTML
                            setHtml(highlightKeywords(p.content, searchKeyword), htmlHttpImageGetter)
                        }
                    }
                    postBtnCapture.visibility = View.VISIBLE
                    postBtnCapture.setOnClickListener(this@PostViewHolder)
                    if (isLoggedIn) {
                        postBtnGroup.visibility = View.VISIBLE
                        if (TextUtils.isEmpty(p.replyUrl)) {
                            postBtnReply.visibility = View.GONE
                        } else {
                            postBtnReply.visibility = View.VISIBLE
                            postBtnReply.setOnClickListener(this@PostViewHolder)
                        }
                        if (TextUtils.isEmpty(p.replyAddUrl)) {
                            postBtnThumbUp.visibility = View.GONE
                        } else {
                            postBtnThumbUp.visibility = View.VISIBLE
                            postBtnThumbUp.setOnClickListener(this@PostViewHolder)
                        }
                        postBtnCopy.setOnClickListener(this@PostViewHolder)
                    } else {
                        postBtnGroup.visibility = View.GONE
                    }
                    
                    contentTextView.setOnLongClickListener { true }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        override fun onClick(v: View) {
            post?.let { p ->
                when (v.id) {
                    R.id.post_btn_reply -> p.replyUrl?.let {
                        EventBus.getDefault().post(ReplyPostEvent(it, p.author))
                    }
                    R.id.post_btn_copy -> if (copyContent(p.content, p.author)) {
                        showMessage(R.string.copy_succeed)
                    } else {
                        showMessage(R.string.copy_failed)
                    }
                    R.id.post_btn_thumb_up -> p.replyAddUrl?.let {
                        mListener.replyAdd(it)
                    }
                    R.id.post_btn_capture -> {
                        convertViewToBitmap(itemView, Bitmap.Config.ARGB_8888)?.let {
                            val path = saveImageToGallery(it, System.currentTimeMillis().toString())
                            EventBus.getDefault().post(ScreenCaptureEvent(path))
                        } ?: showMessage(R.string.general_error)
                    }
                    else -> {
                        
                    }
                }
            }
        }
    }

    inner class ReplyViewHolder internal constructor(
        private val binding: ItemReplyViewBinding
    ) : BaseViewHolder(binding.root), View.OnClickListener {

        private var post: Post? = null
        private val htmlHttpImageGetter: Html.ImageGetter by lazy {
            HtmlCoilImageGetter(binding.root.context, binding.replyContentTextView, mFragment)
        }

        @SuppressLint("SetTextI18n")
        override fun onBind(position: Int) {
            post = getRealPost(position)
            post?.let { p ->
                with(binding) {
                    try {
                        
                        
                        if (isLoggedIn && username.isNotEmpty() && p.author == username) {
                            replyCard.strokeColor = ContextCompat.getColor(root.context, R.color.orange)
                            replyCard.strokeWidth = dp2px(root.context, 1f).toInt()
                        } else {
                            replyCard.strokeWidth = 0
                        }
                        replyPosition.text = "#$position"
                        replyAuthorTextView.text = p.author
                        replyDateTextView.text = getDateDiff(p.date)
                        replyAvatar.setOnClickListener { _ ->
                            mFragment.runCatching { parentFragmentManager }
                                .getOrNull()
                                ?.let {
                                    val location = IntArray(2)
                                    replyAvatar.getLocationInWindow(location)
                                    val popUpInfo = PopUpProfileInfo(
                                        location[0],
                                        location[1],
                                        p.avatar,
                                        p.author,
                                        p.extraInfo,
                                        p.followInfo,
                                        p.profileUrl
                                    )
                                    PopUpProfileDialog.newInstance(popUpInfo).show(
                                        it,
                                        PopUpProfileDialog::class.java.simpleName
                                    )
                                } ?: root.context.startActivity(
                                Intent(root.context, ProfileActivity::class.java).also {
                                    it.putExtra(KEY_PROFILE_URL, p.profileUrl)
                                })
                        }
                        replyAvatar.setImageDrawable(null)
                        replyAvatar.load(p.avatar) {
                            crossfade(false)
                            transformations(RoundedCornersTransformation(6.dpToPx(root.context)))
                            placeholder(PlaceholderDrawable)
                            error(getDefaultAvatar(p.avatar))
                        }
                        replyContentTextView.run {
                        setOnClickListener { onItemClick?.invoke(p) }
                            if (AppConfig.articleHandlePreTag) {
                                setClickablePreCodeSpan(ClickablePreCodeSpanImpl())
                                val drawTableLinkSpan = DrawPreCodeSpan()
                                    .also {
                                        it.tableLinkText = context.getString(R.string.tap_for_code)
                                    }
                                setDrawPreCodeSpan(drawTableLinkSpan)
                            } else {
                                setClickablePreCodeSpan(null)
                                setDrawPreCodeSpan(null)
                            }
                            setImageTagClickListener { _: Context, imageUrl: String, _: Int ->
                                EventBus.getDefault().post(OpenLargeImageViewEvent(imageUrl))
                            }
                            setOnLinkTagClickListener { c: Context?, url: String ->
                                if (url.startsWith(SERVER_BASE_URL + "thread")) {
                                    EventBus.getDefault()
                                        .post(OpenArticleEvent(url.substring(SERVER_BASE_URL.length)))
                                } else {
                                    WebViewActivity.startWebViewWith(url, c)
                                }
                            }
                            // 核心修复：在搜索/过滤状态下，只提取纯文本摘要，避免复杂 HTML 导致测量塌陷不显示
                        if (isFiltering) {
                            val plainText = org.jsoup.Jsoup.parse(p.content ?: "").text()
                            var snippet = plainText
                            
                            // 截取关键字附近的内容（前后各 40 字）
                            if (searchKeyword.isNotBlank() && plainText.contains(searchKeyword, ignoreCase = true)) {
                                val index = plainText.indexOf(searchKeyword, ignoreCase = true)
                                val start = maxOf(0, index - 40)
                                val end = minOf(plainText.length, index + searchKeyword.length + 40)
                                snippet = plainText.substring(start, end)
                                if (start > 0) snippet = "...$snippet"
                                if (end < plainText.length) snippet = "$snippet..."
                            } else if (plainText.length > 150) {
                                snippet = plainText.substring(0, 150) + "..."
                            }
                            setHtml(highlightKeywords(snippet, searchKeyword), htmlHttpImageGetter)
                        } else {
                            // 正常状态下，渲染完整 HTML
                            setHtml(highlightKeywords(p.content, searchKeyword), htmlHttpImageGetter)
                        }
                        }
                        if (isLoggedIn) {
                            replyBtnGroup.visibility = View.VISIBLE
                            if (TextUtils.isEmpty(p.replyUrl)) {
                                replyBtnReply.visibility = View.GONE
                            } else {
                                replyBtnReply.visibility = View.VISIBLE
                                replyBtnReply.setOnClickListener(this@ReplyViewHolder)
                            }
                            if (TextUtils.isEmpty(p.replyAddUrl)) {
                                replyBtnThumbUp.visibility = View.GONE
                            } else {
                                replyBtnThumbUp.visibility = View.VISIBLE
                                replyBtnThumbUp.setOnClickListener(this@ReplyViewHolder)
                            }
                            replyBtnCopy.setOnClickListener(this@ReplyViewHolder)
                        } else {
                            replyBtnGroup.visibility = View.GONE
                        }
                        
                        replyContentTextView.setOnLongClickListener { true }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }
        }

        override fun onClick(v: View) {
            post?.let { p ->
                when (v.id) {
                    R.id.reply_btn_reply -> p.replyUrl?.let {
                        EventBus.getDefault().post(ReplyPostEvent(it, p.author))
                    }
                    R.id.reply_btn_copy -> if (copyContent(p.content, p.author)) {
                        showMessage(R.string.copy_succeed)
                    } else {
                        showMessage(R.string.copy_failed)
                    }
                    R.id.reply_btn_thumb_up -> p.replyAddUrl?.let {
                        mListener.replyAdd(it)
                    }
                    else -> {
                        
                    }
                }
            }
        }
    }

    fun getDateDiff(date: String): String {
        val tips = "发表于 "
        return date.replace(tips, "").toTimeStamp()?.let {
            val diff = System.currentTimeMillis() - it
            when (val minutesDiff = diff / 1000 / 60) {
                in 0..1 -> return "${tips}1 分钟前"
                in 1..60 -> return "${tips}${minutesDiff} 分钟前"
                in 60..24 * 60 -> {
                    val hoursDiff = diff / 1000 / 60 / 60
                    return "${tips}${hoursDiff} 小时前"
                }
                in 24 * 60..7 * 24 * 60 -> {
                    val dayDiff = diff / 1000 / 60 / 60 / 24
                    return "${tips}${dayDiff} 天前"
                }
                else -> {
                    date
                }
            }
        } ?: date
    }

    class PostEmptyViewHolder internal constructor(view: View?) :
        BaseViewHolder(view) {
        override fun onBind(position: Int) {}
    }

    inner class LoadMoreViewHolder internal constructor(private val binding: ItemLoadMoreViewBinding) :
        BaseViewHolder(binding.root) {

        override fun onBind(position: Int) {
            
            
            binding.root.visibility = View.VISIBLE
            mListener.fetchArticlePost(ArticleAdapterListener.FETCH_POST_MORE, null)
        }
    }

    override fun onViewRecycled(holder: BaseViewHolder) {
        super.onViewRecycled(holder)
    }

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_REPLY = 1
        private const val VIEW_TYPE_POST = 2
        private const val VIEW_TYPE_LOAD_MORE = 3
    }

    /**
     * 安全高亮关键字：只替换纯文本节点，不破坏 HTML 标签
     */
    private fun highlightKeywords(html: String?, keyword: String): String {
        if (html.isNullOrBlank() || keyword.isBlank()) return html ?: ""
        val safeKeyword = Regex.escape(keyword)
        val tagRegex = Regex("(<[^>]+>)")
        // 按标签拆分
        val parts = html.split(tagRegex)
        return parts.joinToString("") { part ->
            if (part.startsWith("<") && part.endsWith(">")) {
                part // 是 HTML 标签，原样返回
            } else {
                // 是纯文本，忽略大小写替换为红色加粗的 <font> 标签
                part.replace(Regex(safeKeyword, RegexOption.IGNORE_CASE), "<font color='#E53935'><b>$0</b></font>")
            }
        }
    }

}