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
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        var isExpanded = prefs.getBoolean("quick_reply_expanded", false)
        
        binding.quickReplyScroll.visibility = if (isExpanded) View.VISIBLE else View.GONE
        binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"

        binding.quickReplyHeader.setOnClickListener {
            isExpanded = !isExpanded
            binding.quickReplyScroll.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"
            prefs.edit().putBoolean("quick_reply_expanded", isExpanded).apply()
        }

        // 渲染快捷回复短语
        renderQuickReplies()

 

        
        val editBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#F5F5F5"))
            cornerRadius = 16f 
        }
        binding.replyContent.background = editBg
        binding.replyContent.setPadding(32, 32, 32, 32)

        binding.btnCancel.setOnClickListener {
            dismissDialog()
        }

        binding.btnConfirm.apply {
            replyPostViewModel.sending.observe(viewLifecycleOwner, Observer { isSending ->
                setText(if (isSending) R.string.reply_post_btn_sending else R.string.reply_post_btn_sent)
            })

            setOnClickListener {
                if (System.currentTimeMillis() - lastClickTime < 2000) {
                    showMessage(R.string.reply_btn_debounced_notice)
                    return@setOnClickListener
                }
                lastClickTime = System.currentTimeMillis()
                handleReplyAction()
            }
        }
    }

    private fun handleReplyAction() {
        val content = binding.replyContent.text?.toString()?.trim() ?: ""
        replyPostViewModel.sendReply(arguments?.getString(REPLY_POST_URL), content) { success ->
            binding.root.postDelayed({
                setResult(content, success)
                dismissDialog()
            }, 1000L)
        }
    }

    private fun setResult(content: String, success: Boolean) {
        parentFragmentManager.setFragmentResult(
            REPLY_POST_REQUEST_KEY,
            Bundle().apply {
                putInt("result_code", if (success) REPLY_POST_RESULT_SUCCESS else REPLY_POST_RESULT_FAILED)
                putParcelable("post", createPost(content))
            }
        )
    }

    private fun createPost(content: String) = Post(
        UserDataRepo.username,
        UserDataRepo.avatar,
        CommonUtils.getCurrentDate(),
        content,
        null,
        null
    )

    
    private fun getQuickReplies(): MutableList<String> {
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val json = prefs.getString("quick_replies_json", null)
        val list = mutableListOf<String>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (list.isEmpty()) {
            list.addAll(listOf("感谢分享", "支持楼主", "谢谢大佬", "看看是什么", "学习了"))
        }
        return list
    }

    private fun saveQuickReplies(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("quick_replies_json", array.toString()).apply()
    }

    private fun renderQuickReplies() {
        val container = binding.quickReplyContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val list = getQuickReplies()

        // 渲染已有的短语
        list.forEachIndexed { index, phrase ->
            val textView = TextView(requireContext()).apply {
                text = phrase
                setTextColor(android.graphics.Color.parseColor("#666666"))
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
                ).apply { marginEnd = (8 * density).toInt() }

                setOnClickListener {
                    val current = binding.replyContent.text?.toString() ?: ""
                    if (current.isEmpty()) binding.replyContent.setText(phrase)
                    else binding.replyContent.setText("$current $phrase")
                    binding.replyContent.setSelection(binding.replyContent.text?.length ?: 0)
                }

                setOnLongClickListener {
                    showEditDeleteDialog(index, phrase)
                    true
                }
            }
            container.addView(textView)
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