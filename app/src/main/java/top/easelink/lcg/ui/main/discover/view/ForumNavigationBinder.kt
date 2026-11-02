package top.easelink.lcg.ui.main.discover.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import top.easelink.lcg.databinding.FragmentForumsNavigationBinding
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel


class ForumNavigationBinder : BaseNavigationBinder<ForumListModel, ForumNavigationVH>() {

    override fun onBindViewHolder(holder: ForumNavigationVH, item: ForumListModel) {
        holder.onBind(item, null)
    }

    override fun onBindViewHolder(
        holder: ForumNavigationVH,
        item: ForumListModel,
        payloads: List<Any>
    ) {
        holder.onBind(item, payloads)
    }

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): ForumNavigationVH {
        return ForumNavigationVH(inflater, parent)
    }
}

class ForumNavigationVH(inflater: LayoutInflater, parentView: ViewGroup) : BaseNavigationViewHolder(
    FragmentForumsNavigationBinding.inflate(inflater, parentView, false).root
) {
    private val binding = FragmentForumsNavigationBinding.bind(itemView)

    fun onBind(item: ForumListModel, payloads: List<Any>?) {
        setUp(item.forumList)
    }

    private fun setUp(listModel: List<ForumNavigationModel>) {
        // 为整个板块导航加上类似首页的白底圆角卡片效果
        binding.root.setCardBackground()
        binding.apply {
            forumTips.setOnClickListener {
                navigationGrid.smoothScrollToPosition(
                    (navigationGrid.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() + 1
                )
            }
            navigationGrid.apply {
                layoutManager = LinearLayoutManager(context).apply {
                    orientation = RecyclerView.HORIZONTAL
                }
                adapter = ForumNavigationAdapter().also {
                    it.addItems(listModel)
                }
            }
        }
    }
}

// 动态设置卡片背景和边距（不改变 XML 布局层级）
fun View.setCardBackground() {
    val drawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(android.graphics.Color.WHITE)
        cornerRadius = 8 * resources.displayMetrics.density // 8dp 圆角
    }
    this.background = drawable
    // 模拟外边距
    val params = this.layoutParams as? android.view.ViewGroup.MarginLayoutParams
    params?.setMargins(
        (12 * resources.displayMetrics.density).toInt(),
        (8 * resources.displayMetrics.density).toInt(),
        (12 * resources.displayMetrics.density).toInt(),
        (6 * resources.displayMetrics.density).toInt()
    )
    this.layoutParams = params
    this.elevation = 0f
}
