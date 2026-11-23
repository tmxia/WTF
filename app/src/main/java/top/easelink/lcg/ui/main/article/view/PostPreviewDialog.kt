package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.customview.htmltextview.HtmlCoilImageGetter
import top.easelink.framework.topbase.TopDialog
import top.easelink.lcg.R
import top.easelink.lcg.databinding.DialogPostPreviewBinding
import top.easelink.lcg.ui.main.article.viewmodel.PostPreviewLauncher
import top.easelink.lcg.ui.main.article.viewmodel.PostPreviewViewModel
import top.easelink.lcg.utils.ImageLoaderHelper

class PostPreviewDialog : TopDialog() {
    private lateinit var mViewModel: PostPreviewViewModel
    private var _binding: DialogPostPreviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        val TAG: String = PostPreviewDialog::class.java.simpleName
        private const val ARTICLE_QUERY = "article_query"

        @JvmStatic
        fun newInstance(url: String): PostPreviewDialog = PostPreviewDialog().apply {
            arguments = Bundle().apply {
                putString(ARTICLE_QUERY, url)
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

        binding.root.visibility = View.INVISIBLE

        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(0)
            val params = attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER
            attributes = params
        }

        binding.dialogRoot.setOnClickListener {
            dismissDialog()
        }

        binding.exit.setOnClickListener {
            dismissDialog()
        }

        initView()
    }

    private fun initView() {
        try {
            arguments
                ?.getString(ARTICLE_QUERY)
                ?.takeIf { it.isNotBlank() }
                ?.let { query ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        mViewModel.content.collect { contentHtml ->
                            if (contentHtml.isBlank()) return@collect
                            binding.contentTextView.setHtml(
                                contentHtml,
                                HtmlCoilImageGetter(
                                    binding.contentTextView.context,
                                    binding.contentTextView,
                                    this@PostPreviewDialog
                                )
                            )

                            binding.root.visibility = View.VISIBLE
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        mViewModel.author.collect {
                            binding.authorTextView.text = it
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        mViewModel.avatar.collect {
                            ImageLoaderHelper.loadAvatar(
                                binding.postAvatar,
                                it,
                                cornerDp = 0f,
                                errorRes = 0,
                                placeholderRes = 0,
                            )
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        mViewModel.date.collect {
                            binding.dateTextView.text = it
                        }
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        mViewModel.loadingResult.collect { status ->
                            
                            
                            if (status != -1 && status != R.string.preview_loading) {
                                dismissDialog()
                            }
                        }
                    }

                    mViewModel.initUrl(query)
                }
        } catch (e: Exception) {
            Timber.e(e)
            dismissDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PostPreviewLauncher.notifyDismissed(arguments?.getString(ARTICLE_QUERY))
        _binding = null
    }
}
