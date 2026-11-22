package top.easelink.lcg.ui.main.follow.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import top.easelink.framework.base.BaseViewHolder
import top.easelink.lcg.R
import top.easelink.lcg.databinding.ItemEmptyViewBinding
import top.easelink.lcg.databinding.ItemFollowViewBinding
import top.easelink.lcg.databinding.ItemLoadMoreViewBinding
import top.easelink.lcg.ui.main.follow.model.FollowInfo
import top.easelink.lcg.utils.ImageLoaderHelper

class FollowListAdapter(
    private val onLoadMore: (() -> Unit)? = null
) : RecyclerView.Adapter<BaseViewHolder>() {
    private val mFollowing: MutableList<FollowInfo> = mutableListOf()
    var nextPageUrl: String? = null
    var loadMoreLoading: Boolean = false

    override fun getItemCount(): Int = if (mFollowing.isEmpty()) {
        1
    } else {
        mFollowing.size + 1
    }

    override fun getItemViewType(position: Int): Int = if (mFollowing.isEmpty()) {
        VIEW_TYPE_EMPTY
    } else {
        if (position == mFollowing.size) {
            VIEW_TYPE_LOAD_MORE
        } else {
            VIEW_TYPE_NORMAL
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NORMAL -> {
                val binding = ItemFollowViewBinding.inflate(inflater, parent, false)
                ArticleViewHolder(binding)
            }

            VIEW_TYPE_LOAD_MORE -> {
                val binding = ItemLoadMoreViewBinding.inflate(inflater, parent, false)
                LoadMoreViewHolder(binding)
            }

            else -> {
                val binding = ItemEmptyViewBinding.inflate(inflater, parent, false)
                EmptyViewHolder(binding)
            }
        }
    }

    fun addItems(follows: List<FollowInfo>) {
        mFollowing.addAll(follows)
        notifyDataSetChanged()
    }

    fun appendItems(follows: List<FollowInfo>) {
        val count = itemCount
        mFollowing.addAll(follows)
        notifyItemRangeInserted(count - 1, follows.size)
    }

    fun clearItems() {
        mFollowing.clear()
    }

    inner class ArticleViewHolder internal constructor(
        private val binding: ItemFollowViewBinding
    ) : BaseViewHolder(binding.root) {
        override fun onBind(position: Int) {
            val follow = mFollowing[position]
            binding.apply {
                follow.avatar.let {
                    ImageLoaderHelper.loadAvatar(avatar, it, cornerDp = 2f)
                }
                lastAction.text = follow.lastAction
                username.text = follow.username
                followerNum.text =
                    root.context.getString(R.string.follower_num_template, follow.followerNum)
                followingNum.text =
                    root.context.getString(R.string.following_num_template, follow.followingNum)
                followListContainer.setOnClickListener {
                }
            }
        }
    }

    inner class EmptyViewHolder(binding: ItemEmptyViewBinding) : BaseViewHolder(binding.root) {
        override fun onBind(position: Int) {}
    }

    inner class LoadMoreViewHolder internal constructor(
        private val binding: ItemLoadMoreViewBinding
    ) : BaseViewHolder(binding.root) {
        override fun onBind(position: Int) {
            val url = nextPageUrl
            if (!url.isNullOrEmpty()) {
                binding.loading.apply {
                    if (loadMoreLoading) {
                        visibility = View.VISIBLE
                        playAnimation()
                    } else {
                        cancelAnimation()
                        visibility = View.GONE
                    }
                }
                onLoadMore?.invoke()
            } else {
                binding.loading.apply {
                    cancelAnimation()
                    visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_NORMAL = 1
        private const val VIEW_TYPE_LOAD_MORE = 2
    }
}
