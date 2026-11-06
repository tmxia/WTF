package top.easelink.lcg.ui.main.article.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Bitmap
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

    /**
     * 核心修复：获取目标 Post 在当前 UI 列表中的真实 position
     * 兼容倒序、过滤、楼主置顶等所有复杂情况
     */
    fun getPositionForPost(target: Post): Int {
        return mPostList.indexOfFirst { 
            it === target || (it.replyUrl != null && it.replyUrl == target.replyUrl) 
        }
    }
    private var hasMoreItem: Boolean = false
    var isFiltering: Boolean = false
    var searchKeyword: String = ""

    fun setData(posts: List<Post>, hasMore: Boolean = false, forceRefresh: Boolean = false) {
        if (!forceRefresh && mPostList.size == posts.size && this.hasMoreItem == hasMore) {
            var isSame = true
            for (i in posts.indices) {
                if (mPostList[i].replyUrl != posts[i].replyUrl) {
                    isSame = false
                    break
                }
            }
            if (isSame) return
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
        // 核心修复：必须优先判断是否到底部，否则搜索模式下底部的占位符会被当成空卡片渲染
        position == mPostList.size -> VIEW_TYPE_LOAD_MORE
        isFiltering -> VIEW_TYPE_REPLY
        position == 0 -> VIEW_TYPE_POST
        else -> VIEW_TYPE_REPLY
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_POST -> PostViewHolder(ItemPostViewBinding.inflate(inflater, parent, false))
            VIEW_TYPE_REPLY -> ReplyViewHolder(ItemReplyViewBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LOAD_MORE -> LoadMoreViewHolder(ItemLoadMoreViewBinding.inflate(inflater, parent, false))
            else -> PostEmptyViewHolder(LayoutSkeltonArticleBinding.inflate(inflater, parent, false).root)
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

    private fun highlightKeywords(text: String, keyword: String): String {
        if (text.isBlank() || keyword.isBlank()) return text
        val safeKeyword = Regex.escape(keyword)
        return text.replace(Regex(safeKeyword, RegexOption.IGNORE_CASE), "<font color='#E53935'><b>$0</b></font>")
    }

    inner class PostViewHolder internal constructor(
        private val binding: ItemPostViewBinding
    ) : BaseViewHolder(binding.root), View.OnClickListener {

        private var post: Post? = null
        private val htmlHttpImageGetter: Html.ImageGetter = HtmlCoilImageGetter(binding.root.context, binding.contentTextView, mFragment)

        override fun onBind(position: Int) {
            val p = mPostList.getOrNull(position) ?: return
            post = p
            
            itemView.isClickable = true
            itemView.setOnClickListener { onItemClick?.invoke(p) }
            binding.root.isClickable = true
            binding.root.setOnClickListener { onItemClick?.invoke(p) }
            binding.contentTextView.isClickable = true
            binding.contentTextView.setOnClickListener { onItemClick?.invoke(p) }

            with(binding) {
                try {
                    authorTextView.text = p.author
                    dateTextView.text = getDateDiff(p.date)
                    postAvatar.setOnClickListener { _ ->
                        mFragment.runCatching { parentFragmentManager }.getOrNull()?.let {
                            val location = IntArray(2)
                            postAvatar.getLocationInWindow(location)
                            val popUpInfo = PopUpProfileInfo(
                                location[0], location[1], p.avatar, p.author, p.extraInfo, p.followInfo, p.profileUrl
                            )
                            PopUpProfileDialog.newInstance(popUpInfo).show(it, PopUpProfileDialog::class.java.simpleName)
                        } ?: root.context.startActivity(
                            Intent(root.context, ProfileActivity::class.java).also {
                                it.putExtra(KEY_PROFILE_URL, p.profileUrl)
                            }
                        )
                    }
                    postAvatar.load(p.avatar) {
                        crossfade(false)
                        lifecycle(mFragment)
                        transformations(RoundedCornersTransformation(4.dpToPx(root.context)))
                        error(R.drawable.ic_noavatar_middle_gray)
                    }
                    contentTextView.run {
                        requestFocus()
                        setOnClickListener { onItemClick?.invoke(p) }
                        minHeight = (60 * context.resources.displayMetrics.density).toInt()
                        
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
                                EventBus.getDefault().post(OpenArticleEvent(url.substring(SERVER_BASE_URL.length)))
                            } else {
                                WebViewActivity.startWebViewWith(url, c)
                            }
                        }
                        
                        if (isFiltering) {
                            val plainText = org.jsoup.Jsoup.parse(p.content ?: "").text()
                            var snippet = plainText
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
                            if (snippet.isBlank()) snippet = "[图片或代码，无文字内容]"
                            setHtml(highlightKeywords(snippet, searchKeyword), htmlHttpImageGetter)
                        } else {
                            setHtml(p.content, htmlHttpImageGetter)
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
                            // 核心修复：绑定颜色状态
                            val isLikedColor = ContextCompat.getColor(root.context, R.color.colorAccent)
                            val normalColor = ContextCompat.getColor(root.context, R.color.gray)
                            postBtnThumbUp.setColorFilter(if (p.isLiked) isLikedColor else normalColor)
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
                    R.id.post_btn_reply -> p.replyUrl?.let { EventBus.getDefault().post(ReplyPostEvent(it, p.author)) }
                    R.id.post_btn_copy -> if (copyContent(p.content, p.author)) showMessage(R.string.copy_succeed) else showMessage(R.string.copy_failed)
                    R.id.post_btn_thumb_up -> {
                        if (p.isLiked) {
                            showMessage("您已经赞过该楼层了")
                            return
                        }
                        if (p.replyAddUrl.isNullOrBlank()) {
                            showMessage("无法获取点赞地址")
                            return
                        }
                        p.replyAddUrl?.let { url ->
                            // 乐观更新：立即变红
                            p.isLiked = true
                            val isLikedColor = ContextCompat.getColor(itemView.context, R.color.colorAccent)
                            binding.postBtnThumbUp.setColorFilter(isLikedColor)
                            // 发起请求
                            mListener.replyAdd(url)
                        }
                    }
                    R.id.post_btn_capture -> {
                        showMessage("正在生成截图，请稍候...")
                        itemView.postDelayed({
                            try {
                                val bitmap = createBitmapFromView(itemView)
                                if (bitmap != null) {
                                    val path = saveImageToGallery(bitmap, System.currentTimeMillis().toString())
                                    EventBus.getDefault().post(ScreenCaptureEvent(path))
                                } else {
                                    showMessage("截图失败，图片可能过大")
                                }
                            } catch (e: Exception) {
                                showMessage("截图异常")
                                Timber.e(e)
                            }
                        }, 800)
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
                itemView.isClickable = true
                itemView.setOnClickListener { onItemClick?.invoke(p) }
                binding.root.isClickable = true
                binding.root.setOnClickListener { onItemClick?.invoke(p) }
                binding.replyContentTextView.isClickable = true
                binding.replyContentTextView.setOnClickListener { onItemClick?.invoke(p) }

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
                            mFragment.runCatching { parentFragmentManager }.getOrNull()?.let {
                                val location = IntArray(2)
                                replyAvatar.getLocationInWindow(location)
                                val popUpInfo = PopUpProfileInfo(
                                    location[0], location[1], p.avatar, p.author, p.extraInfo, p.followInfo, p.profileUrl
                                )
                                PopUpProfileDialog.newInstance(popUpInfo).show(it, PopUpProfileDialog::class.java.simpleName)
                            } ?: root.context.startActivity(
                                Intent(root.context, ProfileActivity::class.java).also {
                                    it.putExtra(KEY_PROFILE_URL, p.profileUrl)
                                }
                            )
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
                            minHeight = (60 * context.resources.displayMetrics.density).toInt()
                            
                            if (AppConfig.articleHandlePreTag) {
                                setClickablePreCodeSpan(ClickablePreCodeSpanImpl())
                                val drawTableLinkSpan = DrawPreCodeSpan().also {
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
                                    EventBus.getDefault().post(OpenArticleEvent(url.substring(SERVER_BASE_URL.length)))
                                } else {
                                    WebViewActivity.startWebViewWith(url, c)
                                }
                            }
                            
                            if (isFiltering) {
                                val plainText = org.jsoup.Jsoup.parse(p.content ?: "").text()
                                var snippet = plainText
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
                                if (snippet.isBlank()) snippet = "[图片或代码，无文字内容]"
                                setHtml(highlightKeywords(snippet, searchKeyword), htmlHttpImageGetter)
                            } else {
                                setHtml(p.content, htmlHttpImageGetter)
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
                            val isLikedColor = ContextCompat.getColor(root.context, R.color.colorAccent)
                            val normalColor = ContextCompat.getColor(root.context, R.color.gray)
                            replyBtnThumbUp.setColorFilter(if (p.isLiked) isLikedColor else normalColor)
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
                    R.id.reply_btn_reply -> p.replyUrl?.let { EventBus.getDefault().post(ReplyPostEvent(it, p.author)) }
                    R.id.reply_btn_copy -> if (copyContent(p.content, p.author)) showMessage(R.string.copy_succeed) else showMessage(R.string.copy_failed)
                    R.id.reply_btn_thumb_up -> p.replyAddUrl?.let { mListener.replyAdd(it) }
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
                else -> date
            }
        } ?: date
    }

    class PostEmptyViewHolder internal constructor(view: View?) : BaseViewHolder(view) {
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

    private fun createBitmapFromView(view: View): Bitmap? {
        try {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
            
            var width = view.measuredWidth
            var height = view.measuredHeight
            if (width <= 0 || height <= 0) return null

            val maxHeight = 8000
            var scale = 1.0f
            
            if (height > maxHeight) {
                scale = maxHeight.toFloat() / height.toFloat()
                height = maxHeight
                width = (width * scale).toInt()
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            
            if (scale < 1.0f) {
                canvas.scale(scale, scale)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(view.measuredWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(view.measuredHeight, View.MeasureSpec.UNSPECIFIED)
                )
            }

            val oldWidth = view.width
            val oldHeight = view.height
            val oldLeft = view.left
            val oldTop = view.top

            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            view.draw(canvas)
            view.layout(oldLeft, oldTop, oldLeft + oldWidth, oldTop + oldHeight)

            return bitmap
        } catch (e: OutOfMemoryError) {
            Timber.e("OOM 内存溢出，长图生成失败")
            return null
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }


    fun markAsLiked(url: String) {
        val index = mPostList.indexOfFirst { it.replyAddUrl == url }
        if (index != -1) {
            mPostList[index].isLiked = true
            notifyItemChanged(index, "like_status_changed")
        }
    }

}
