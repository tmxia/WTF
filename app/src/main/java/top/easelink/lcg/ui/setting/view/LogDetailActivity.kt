package top.easelink.lcg.ui.setting.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.easelink.lcg.R
import top.easelink.lcg.utils.AppLogManager

class LogDetailActivity : AppCompatActivity() {

    private lateinit var timeStr: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_detail)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        timeStr = intent.getStringExtra("time") ?: ""
        val level = intent.getIntExtra("level", Log.DEBUG)
        val tag = intent.getStringExtra("tag") ?: ""
        val message = intent.getStringExtra("message") ?: ""
        val stack = intent.getStringExtra("stack")

        val tvLevel = findViewById<TextView>(R.id.tv_level)
        val tvTag = findViewById<TextView>(R.id.tv_tag)
        val tvTime = findViewById<TextView>(R.id.tv_time)
        val tvMessage = findViewById<TextView>(R.id.tv_message)
        val tvStackTitle = findViewById<TextView>(R.id.tv_stack_title)
        val tvStack = findViewById<TextView>(R.id.tv_stack)

        // 渲染等级和颜色
        when (level) {
            Log.ERROR -> { tvLevel.text = "E"; tvLevel.setTextColor(android.graphics.Color.parseColor("#F44336")) }
            Log.WARN -> { tvLevel.text = "W"; tvLevel.setTextColor(android.graphics.Color.parseColor("#FF9800")) }
            Log.INFO -> { tvLevel.text = "I"; tvLevel.setTextColor(android.graphics.Color.parseColor("#4CAF50")) }
            else -> { tvLevel.text = "D"; tvLevel.setTextColor(android.graphics.Color.parseColor("#2196F3")) }
        }

        val levelName = when (level) {
            Log.ERROR -> "Error"
            Log.WARN -> "Warn"
            Log.INFO -> "Info"
            else -> "Debug"
        }
        tvTag.text = "$levelName · $tag"
        tvTime.text = timeStr
        tvMessage.text = message

        // 处理堆栈
        if (!stack.isNullOrBlank()) {
            tvStackTitle.visibility = View.VISIBLE
            tvStack.visibility = View.VISIBLE
            tvStack.text = stack
        } else {
            tvStackTitle.visibility = View.GONE
            tvStack.visibility = View.GONE
        }

        // 复制按钮
        findViewById<View>(R.id.btn_copy).setOnClickListener {
            val fullText = "$levelName · $tag\n$timeStr\n\n$message\n\n${stack ?: ""}"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("LogDetail", fullText))
            Toast.makeText(this, "已复制该条日志", Toast.LENGTH_SHORT).show()
        }

        // 删除按钮
        findViewById<View>(R.id.btn_delete).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("删除日志")
                .setMessage("确定要删除这条日志记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    AppLogManager.removeLog(timeStr)
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
