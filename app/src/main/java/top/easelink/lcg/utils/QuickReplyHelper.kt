package top.easelink.lcg.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray

object QuickReplyHelper {

    private fun getQuickReplies(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
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

    private fun saveQuickReplies(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("quick_replies_json", array.toString()).apply()
    }

    fun renderQuickReplies(context: Context, container: LinearLayout, onPhraseClicked: (String) -> Unit) {
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

                setOnClickListener { onPhraseClicked(phrase) }

                
                setOnLongClickListener {
                    showEditDeleteDialog(context, index, phrase) { renderQuickReplies(context, container, onPhraseClicked) }
                    true
                }
            }
            container.addView(textView)
        }

        
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
                showEditDeleteDialog(context, -1, "") { renderQuickReplies(context, container, onPhraseClicked) }
            }
        }
        container.addView(addBtn)
    }

    private fun showEditDeleteDialog(context: Context, index: Int, phrase: String, onUpdated: () -> Unit) {
        val options = if (index == -1) arrayOf("新增短语") else arrayOf("编辑短语", "删除短语")
        MaterialAlertDialogBuilder(context)
            .setTitle(if (index == -1) "新增快捷短语" else "操作: $phrase")
            .setItems(options) { _, which ->
                if (index == -1 || which == 0) {
                    showInputDialog(context, index, phrase, onUpdated)
                } else {
                    val list = getQuickReplies(context)
                    if (index in list.indices) {
                        list.removeAt(index)
                        saveQuickReplies(context, list)
                        onUpdated()
                    }
                }
            }
            .show()
    }

    private fun showInputDialog(context: Context, index: Int, phrase: String, onUpdated: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val padding = (20 * density).toInt()
        
        val textInputLayout = TextInputLayout(context).apply {
            hint = "输入快捷短语"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f * density, 12f * density, 12f * density, 12f * density)
            setPadding(padding, (12 * density).toInt(), padding, 0)
        }
        
        val input = EditText(context).apply {
            setText(phrase)
            setSelection(text.length)
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        textInputLayout.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle(if (index == -1) "新增短语" else "编辑短语")
            .setView(textInputLayout)
            .setPositiveButton("保存") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    val list = getQuickReplies(context)
                    if (index == -1) list.add(newText)
                    else if (index in list.indices) list[index] = newText
                    
                    saveQuickReplies(context, list)
                    onUpdated()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
