package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
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
import top.easelink.lcg.utils.QuickReplyHelper
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
        
        dialog?.setCanceledOnTouchOutside(true)
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
        

        

        
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        var isExpanded = prefs.getBoolean("quick_reply_expanded", false)
        
        binding.quickReplyScroll.visibility = if (isExpanded) View.VISIBLE else View.GONE
        binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"

        binding.quickReplyHeader.setOnClickListener {
            isExpanded = !isExpanded
            binding.quickReplyScroll.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.quickReplyArrow.text = if (isExpanded) "▲" else "▼"
            prefs.edit().putBoolean("quick_reply_expanded", isExpanded).apply()
        }

        
        QuickReplyHelper.renderQuickReplies(requireContext(), binding.quickReplyContainer) { phrase ->
            val current = binding.replyContent.text?.toString() ?: ""
            if (current.isEmpty()) {
                binding.replyContent.setText(phrase)
            } else {
                binding.replyContent.setText("$current $phrase")
            }
            binding.replyContent.setSelection(binding.replyContent.text?.length ?: 0)
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}
