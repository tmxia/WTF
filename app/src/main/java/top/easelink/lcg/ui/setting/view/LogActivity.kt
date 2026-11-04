package top.easelink.lcg.ui.setting.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.easelink.lcg.R
import top.easelink.lcg.utils.AppLogManager
import top.easelink.lcg.utils.LogItem
import java.io.File

class LogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private lateinit var tvEmpty: TextView
    private var allLogs: List<LogItem> = emptyList()
    private var currentFilter = 0 // 0=全部, 1=Info, 2=Error

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.log_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        tvEmpty = findViewById(R.id.tv_empty)
        adapter = LogAdapter()
        val recycler = findViewById<RecyclerView>(R.id.recycler_logs)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        allLogs = AppLogManager.getLogs().reversed()
        applyFilter()

        findViewById<com.google.android.material.chip.ChipGroup>(R.id.log_filter_group).setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_info -> 1
                R.id.chip_error -> 2
                else -> 0
            }
            applyFilter()
        }

        findViewById<TextView>(R.id.btn_export_log).setOnClickListener {
            val logText = AppLogManager.exportLogs()
            if (logText.isBlank()) {
                Toast.makeText(this, "没有日志可导出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AppLogs", logText))
            
            val file = File(getExternalFilesDir(null), "wtf_debug_log.txt")
            file.writeText(logText)
            Toast.makeText(this, "已复制到剪贴板，并保存至:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun applyFilter() {
        val filtered = allLogs.filter {
            when (currentFilter) {
                1 -> it.level >= Log.INFO
                2 -> it.level >= Log.WARN
                else -> true
            }
        }
        adapter.submit(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showLogDetailDialog(item: LogItem) {
        val levelStr = when (item.level) {
            Log.ERROR -> "ERROR"
            Log.WARN -> "WARN"
            Log.INFO -> "INFO"
            else -> "DEBUG"
        }
        val fullText = "[${item.time}] $levelStr/${item.tag}\n\n${item.message}"

        // 核心修复：使用自定义的圆角布局
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_log_detail, null)
        val dialog = android.app.AlertDialog.Builder(this).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.setView(dialogView)
        dialog.show()

        // 限制弹窗宽度
        val density = resources.displayMetrics.density
        dialog.window?.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvContent = dialogView.findViewById<TextView>(R.id.tv_dialog_log_content)
        tvContent.text = fullText

        dialogView.findViewById<View>(R.id.btn_dialog_close).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_dialog_copy).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("LogDetail", fullText))
            Toast.makeText(this, "已复制该条日志", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {
        private var list: List<LogItem> = emptyList()
        fun submit(newList: List<LogItem>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val levelStr = when (item.level) {
                Log.ERROR -> "E"
                Log.WARN -> "W"
                Log.INFO -> "I"
                else -> "D"
            }
            holder.tvHeader.text = "[${item.time}] $levelStr/${item.tag}"
            holder.tvMessage.text = item.message
            holder.itemView.setOnClickListener {
                showLogDetailDialog(item)
            }
        }

        override fun getItemCount() = list.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tv_log_header)
            val tvMessage: TextView = view.findViewById(R.id.tv_log_message)
        }
    }
}
