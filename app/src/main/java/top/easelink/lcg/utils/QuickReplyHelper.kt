package top.easelink.lcg.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
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

    // 用纯手工打造的极简弹窗，拒绝任何原生丑样式
    private fun showEditDeleteDialog(context: Context, index: Int, phrase: String, onUpdated: () -> Unit) {
        val items = if (index == -1) arrayOf("新增短语") else arrayOf("编辑短语", "删除短语")
        val titleStr = if (index == -1) "新增快捷短语" else "操作: $phrase"
        
        val dialog = AlertDialog.Builder(context).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val density = context.resources.displayMetrics.density
        
        // 核心：外层套一个 Material 大圆角卡片
        val card = MaterialCardView(context).apply {
            radius = 16f * density
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = 0
            elevation = 0f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 大幅缩小内边距
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
        }

        layout.addView(TextView(context).apply {
            text = titleStr
            textSize = 15f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        items.forEachIndexed { i, item ->
            layout.addView(TextView(context).apply {
                text = item
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                // 减小选项的上下间距
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    if (index == -1 || i == 0) showInputDialog(context, index, phrase, onUpdated)
                    else {
                        val list = getQuickReplies(context)
                        if (index in list.indices) {
                            list.removeAt(index)
                            saveQuickReplies(context, list)
                            onUpdated()
                        }
                    }
                }
            })
        }
        card.addView(layout)
        dialog.setView(card)
        dialog.show()
        // 核心修复：限制宽度为屏幕的 75% 或 280dp，让弹窗更精致
        val width = (280 * density).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showInputDialog(context: Context, index: Int, phrase: String, onUpdated: () -> Unit) {
        val dialog = AlertDialog.Builder(context).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val density = context.resources.displayMetrics.density

        val card = MaterialCardView(context).apply {
            radius = 16f * density
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = 0
            elevation = 0f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (8 * density).toInt())
        }

        layout.addView(TextView(context).apply {
            text = if (index == -1) "新增短语" else "编辑短语"
            textSize = 15f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        val textInputLayout = TextInputLayout(context).apply {
            hint = "输入快捷短语"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(8f * density, 8f * density, 8f * density, 8f * density)
            // 减小输入框上方的空白
            setPadding(0, 0, 0, 0)
        }

        val input = EditText(context).apply {
            setText(phrase)
            setSelection(text.length)
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT
            // 缩小输入框内部间距，降低高度
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        textInputLayout.addView(input)
        layout.addView(textInputLayout)

        val btnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            // 减小按钮栏的顶部间距
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        btnLayout.addView(TextView(context).apply {
            text = "取消"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            setOnClickListener { dialog.dismiss() }
        })

        btnLayout.addView(TextView(context).apply {
            text = "保存"
            textSize = 14f
            setTextColor(Color.parseColor("#E53935"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            setOnClickListener {
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    val list = getQuickReplies(context)
                    if (index == -1) list.add(newText)
                    else if (index in list.indices) list[index] = newText
                    saveQuickReplies(context, list)
                    onUpdated()
                }
                dialog.dismiss()
            }
        })

        layout.addView(btnLayout)
        card.addView(layout)
        dialog.setView(card)
        dialog.show()
        // 核心修复：限制宽度为屏幕的 75% 或 280dp
        val width = (280 * density).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
