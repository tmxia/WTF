package top.easelink.lcg.ui.main.article.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import top.easelink.framework.topbase.TopDialog
import top.easelink.lcg.R
import top.easelink.lcg.databinding.DialogDownloadLinkBinding

class DownloadLinkDialog : TopDialog() {

    private var _binding: DialogDownloadLinkBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDownloadLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val params = attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            attributes = params
        }

        arguments?.getStringArrayList(KEY_LINK_LIST)?.let { linkList ->
            if (linkList.isNotEmpty()) {
                val density = resources.displayMetrics.density
                val padding = (12 * density).toInt()
                val marginBottom = (10 * density).toInt()

                
                val allCodes = linkList.filter { 
                    it.contains("提取码") || it.contains("密码") || it.contains("访问码") || it.contains("口令") 
                }.joinToString(" ") { it.trim() }

                linkList.forEach { link ->
                    val textView = TextView(requireContext()).apply {
                        text = link
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(context, R.color.black))
                        setPadding(padding, padding, padding, padding)

                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#F5F5F5"))
                            cornerRadius = 8f * density
                        }

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            this.bottomMargin = marginBottom
                        }
                        layoutParams = params

                        
                        setOnClickListener { 
                            val copyText = buildSmartLink(link, linkList, allCodes)
                            copy(copyText) 
                        }
                        
                        setOnLongClickListener { 
                            val copyText = buildSmartLink(link, linkList, allCodes)
                            copy(copyText)
                            true
                        }
                    }
                    binding.linkContainer.addView(textView)
                }
            }
        }

        binding.exit.setOnClickListener { dismissDialog() }
    }

    
    private fun buildSmartLink(targetLink: String, allLinks: List<String>, allCodes: String): String {
        return targetLink.trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }

    private fun copy(clipString: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val mClipData = ClipData.newPlainText("DownloadLink", clipString)
        cm.setPrimaryClip(mClipData)
        
        
        val toastMsg = if (clipString.contains("提取码") || clipString.contains("密码")) {
            "已复制（含提取码），可直接粘贴"
        } else {
            "链接已复制"
        }
        Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val TAG: String = DownloadLinkDialog::class.java.simpleName
        private const val KEY_LINK_LIST = "KEY_LINK_LIST"

        @JvmStatic
        fun newInstance(downloadLinkList: ArrayList<String>): DownloadLinkDialog {
            val fragment = DownloadLinkDialog()
            val bundle = Bundle()
            bundle.putStringArrayList(KEY_LINK_LIST, downloadLinkList)
            fragment.arguments = bundle
            return fragment
        }
    }
}
