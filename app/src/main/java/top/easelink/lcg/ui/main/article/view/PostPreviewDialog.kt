package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import coil.load
import timber.log.Timber
import top.easelink.framework.customview.htmltextview.HtmlCoilImageGetter
import top.easelink.framework.topbase.TopDialog
import top.easelink.framework.utils.dpToPx
import top.easelink.lcg.databinding.DialogPostPreviewBinding
import top.easelink.lcg.ui.main.article.viewmodel.PostPreviewViewModel
import top.easelink.lcg.utils.getScreenHeightDp
import top.easelink.lcg.utils.getScreenWidthDp

class PostPreviewDialog : TopDialog() {

    private lateinit var mViewModel: PostPreviewViewModel
    private var _binding: DialogPostPreviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        val TAG: String = PostPreviewDialog::class.java.simpleName
        private const val ARTICLE_QUERY = "article_query"

        @JvmStatic
        fun newInstance(url: String): PostPreviewDialog {
            return PostPreviewDialog().apply {
                arguments = Bundle().apply {
                    putString(ARTICLE_QUERY, url)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel = ViewModelProvider(this)[PostPreviewViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPostPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 核心修复：在数据加载完成前保持透明，彻底杜绝“加载窗口”闪现
        binding.root.alpha = 0f

                // 核心修复：让 Window 铺满全屏，且背景透明，由内部 Card 呈现内容
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            // 核心修复：去除弹窗所有过渡动画，瞬间弹出
            setWindowAnimations(0)
            val params = attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER
            attributes = params
        }
        
        // 显式绑定关闭按钮
        binding.exit.setOnClickListener {
            dismissDialog()
        }
        
        // 支持点击遮罩层（即 Card 外的空白区域）关闭
        dialog?.setCanceledOnTouchOutside(true)
        initView()
    }

    private fun initView() {
        try {
            arguments
                ?.getString(ARTICLE_QUERY)
                ?.takeIf { it.isNotBlank() }
                ?.let { query ->
                    mViewModel.content.observe(viewLifecycleOwner, Observer { contentHtml ->
                        binding.contentTextView.setHtml(
                            contentHtml, HtmlCoilImageGetter(
                                binding.contentTextView.context,
                                binding.contentTextView,
                                this
                            )
                    binding.root.alpha = 1f
                        )
                    })
                    mViewModel.author.observe(viewLifecycleOwner, Observer {
                        binding.authorTextView.text = it
                    })
                    mViewModel.avatar.observe(viewLifecycleOwner, Observer {
                        binding.postAvatar.load(it)
                    })
                    mViewModel.date.observe(viewLifecycleOwner, Observer {
                        binding.dateTextView.text = it
                    })
                    // 静默加载：不显示加载过程窗口
                    binding.loadingStatus.visibility = View.GONE
                    mViewModel.initUrl(query)
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}