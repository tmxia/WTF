package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import androidx.core.view.setPadding
import org.json.JSONArray
import android.widget.FrameLayout
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.widget.TextView
import android.widget.LinearLayout
import android.view.*
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.TopDialog
import top.easelink.framework.utils.CommonUtils
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.databinding.DialogCommentArticleBinding
import top.easelink.lcg.ui.main.article.viewmodel.ReplyPostViewModel
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.utils.showMessage
import top.easelink.lcg.utils.QuickReplyHelper

class CommentArticleDialog : TopDialog() {

    companion object {
        private val TAG = CommentArticleDialog::class.java.simpleName
        private const val REPLY_POST_URL = "reply_article_url"
        const val REPLY_POST_REQUEST_KEY = "reply_post_request"
        const val REPLY_POST_RESULT_SUCCESS = 1
        const val REPLY_POST_RESULT_FAILED = 0

        @JvmStatic
        fun newInstance(replyPostUrl: String): CommentArticleDialog {
            return CommentArticleDialog().apply {
                arguments = Bundle().apply {
                    putString(REPLY_POST_URL, replyPostUrl)
                }
            }
        }
    }

    private lateinit var replyPostViewModel: ReplyPostViewModel
    private var _binding: DialogCommentArticleBinding? = null
    private val binding get() = _binding!!
    private var lastClickTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupDialogWindow()
        _binding = DialogCommentArticleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        replyPostViewModel = ViewModelProvider(this)[ReplyPostViewModel::class.java]
        setupUI()
    }

    private fun setupDialogWindow() {
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent) 
            setWindowAnimations(R.style.BottomInOutAnim)
            
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                 
                   
            }
        }
    }

    private fun setupUI() {
        
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 32f 
        }
        binding.root.background = bgDrawable
        binding.root.setPadding(48, 48, 48, 48)

        
        // 读取折叠状态并绑定点击事件 (默认折叠)
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        var isExpanded = prefs.getBoolean("quick_reply_expanded", false)
        
        binding.quickReplyScroll.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"

        binding.quickReplyHeader.setOnClickListener {
            isExpanded = !isExpanded
            binding.quickReplyScroll.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"
            prefs.edit().putBoolean("quick_reply_expanded", isExpanded).apply()
        }

        // 渲染快捷短语
        QuickReplyHelper.renderQuickReplies(requireContext(), binding.quickReplyContainer) { phrase ->
            val current = binding.replyContent.text?.toString() ?: ""
            if (current.isEmpty()) binding.replyContent.setText(phrase)
            else binding.replyContent.setText("$current $phrase")
            binding.replyContent.setSelection(binding.replyContent.text?.length ?: 0)
        }


        // 添加一个“+ 添加”按钮
        val addBtn = TextView(requireContext()).apply {
            text = "+ 添加"
            setTextColor(android.graphics.Color.parseColor("#888888"))
            textSize = 12f
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F5F5F5"))
                cornerRadius = 16f * density
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { showEditDeleteDialog(-1, "") }
        }
        container.addView(addBtn)
    }

    private fun showEditDeleteDialog(index: Int, phrase: String) {
        val options = if (index == -1) arrayOf("新增短语") else arrayOf("编辑短语", "删除短语")
        AlertDialog.Builder(requireContext())
            .setTitle(if (index == -1) "新增快捷短语" else "操作: $phrase")
            .setItems(options) { _, which ->
                if (index == -1 || which == 0) {
                    showInputDialog(index, phrase)
                } else {
                    val list = getQuickReplies()
                    if (index in list.indices) {
                        list.removeAt(index)
                        saveQuickReplies(list)
                        renderQuickReplies()
                    }
                }
            }
            .show()
    }

    private fun showInputDialog(index: Int, phrase: String) {
        val input = EditText(requireContext()).apply {
            setText(phrase)
            setSelection(text.length)
            hint = "输入快捷短语"
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding((16 * resources.displayMetrics.density).toInt(), 0, (16 * resources.displayMetrics.density).toInt(), 0)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (index == -1) "新增短语" else "编辑短语")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    val list = getQuickReplies()
                    if (index == -1) {
                        list.add(newText)
                    } else if (index in list.indices) {
                        list[index] = newText
                    }
                    saveQuickReplies(list)
                    renderQuickReplies()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}