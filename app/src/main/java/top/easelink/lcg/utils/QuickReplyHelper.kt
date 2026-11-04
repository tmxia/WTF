package top.easelink.lcg.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import top.easelink.lcg.R
import top.easelink.lcg.databinding.DialogEditQuickReplyBinding

object QuickReplyHelper {

    fun getQuickReplies(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val json = prefs.getString("quick_replies_json", null)
        val list = mutableListOf<String>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (e: Exception) {}
        }
        if (list.isEmpty()) {
            list.addAll(listOf("感谢分享", "支持楼主", "谢谢大佬", "看看是什么", "学习了"))
        }
        return list
    }

    fun saveQuickReplies(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("quick_replies_json", array.toString()).apply()
    }

    fun renderQuickReplies(context: Context, container: LinearLayout, onItemClick: (String) -> Unit) {
        container.removeAllViews()
        val density = context.resources.displayMetrics.density
        val list = getQuickReplies(context)

        list.forEachIndexed { index, phrase ->
            val textView = TextView(context).apply {
                text = phrase
                setTextColor(Color.parseColor("#666666"))
                textSize = 12f
                val padH = (12 * density).toInt()
                val padV = (6 * density).toInt()
                setPadding(padH, padV, padH, padV)
                
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F5F5F5"))
                    cornerRadius = 16f * density
                }
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * density).toInt() }

                setOnClickListener { onItemClick(phrase) }
                setOnLongClickListener { 
                    showActionMenu(context, index, phrase) {
                        renderQuickReplies(context, container, onItemClick)
                    }
                    true 
                }
            }
            container.addView(textView)
        }

        // "+ 添加" 按钮
        val addBtn = TextView(context).apply {
            text = "+ 添加"
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = 16f * density
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { 
                showEditDialog(context, -1, "") {
                    renderQuickReplies(context, container, onItemClick)
                } 
            }
        }
        container.addView(addBtn)
    }

    private fun showActionMenu(context: Context, index: Int, phrase: String, onRefresh: () -> Unit) {
        val options = arrayOf("编辑短语", "删除短语")
        AlertDialog.Builder(context)
            .setTitle("操作: $phrase")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showEditDialog(context, index, phrase, onRefresh)
                } else {
                    val list = getQuickReplies(context)
                    if (index in list.indices) {
                        list.removeAt(index)
                        saveQuickReplies(context, list)
                        onRefresh()
                    }
                }
            }
            .show()
    }

    fun showEditDialog(context: Context, index: Int, phrase: String, onSaved: () -> Unit) {
        val binding = DialogEditQuickReplyBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        binding.tvTitle.text = if (index == -1) "新增快捷短语" else "编辑快捷短语"
        binding.etContent.setText(phrase)
        if (phrase.isNotEmpty()) {
            binding.etContent.setSelection(phrase.length)
        }

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnSave.setOnClickListener {
            val newText = binding.etContent.text.toString().trim()
            if (newText.isNotEmpty()) {
                val list = getQuickReplies(context)
                if (index == -1) {
                    list.add(newText)
                } else if (index in list.indices) {
                    list[index] = newText
                }
                saveQuickReplies(context, list)
                onSaved()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
