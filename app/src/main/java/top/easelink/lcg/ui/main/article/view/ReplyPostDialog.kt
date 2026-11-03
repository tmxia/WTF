package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import top.easelink.lcg.R
import top.easelink.lcg.databinding.DialogReplyPostBinding
import top.easelink.lcg.ui.main.article.viewmodel.ReplyPostViewModel
import top.easelink.lcg.utils.showMessage

class ReplyPostDialog : DialogFragment() {

    private lateinit var replyPostViewModel: ReplyPostViewModel
    private var _binding: DialogReplyPostBinding? = null
    private val binding get() = _binding!!
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog_FullScreen_BottomInOut)
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog?.window?.setDimAmount(0.5f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.setWindowAnimations(R.style.BottomInOutAnim)
        _binding = DialogReplyPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        replyPostViewModel = ViewModelProvider(this)[ReplyPostViewModel::class.java]

        binding.btnCancel.setOnClickListener { dismiss() }
        
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 32f
        }
        binding.root.background = bgDrawable
        binding.root.setPadding(48, 48, 48, 48)

        val editBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#F5F5F5"))
            cornerRadius = 16f
        }
        binding.replyContent.background = editBg
        binding.replyContent.setPadding(32, 32, 32, 32)
        
        binding.replyTo.text = getString(R.string.reply_post_dialog_title, arguments?.getString(REPLY_POST_AUTHOR))

        replyPostViewModel.sending.observe(viewLifecycleOwner) { isSending ->
            binding.btnConfirm.setText(if (isSending) R.string.reply_post_btn_sending else R.string.reply_post_btn_sent)
        }

        binding.btnConfirm.setOnClickListener {
            if (System.currentTimeMillis() - lastClickTime < 2000) {
                showMessage(R.string.reply_btn_debounced_notice)
                return@setOnClickListener
            }
            lastClickTime = System.currentTimeMillis()
            arguments?.getString(REPLY_POST_URL)?.let { url ->
                replyPostViewModel.sendReply(url, binding.replyContent.text.toString()) {
                    view?.postDelayed({ dismiss() }, 1000L)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            
            setBackgroundDrawableResource(android.R.color.transparent)
            
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            val params = attributes
            
            
            attributes = params
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REPLY_POST_URL = "reply_post_url"
        private const val REPLY_POST_AUTHOR = "reply_post_author"

        fun newInstance(replyPostUrl: String, author: String): ReplyPostDialog {
            return ReplyPostDialog().apply {
                arguments = Bundle().apply {
                    putString(REPLY_POST_URL, replyPostUrl)
                    putString(REPLY_POST_AUTHOR, author)
                }
            }
        }
    }
}