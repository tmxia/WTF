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
                R.id.chip_net -> 1
                R.id.chip_biz -> 2
                R.id.chip_error -> 3
                else -> 0
            }
            applyFilter()
        }

        findViewById<TextView>(R.id.btn_clear_log).setOnClickListener {
            AppLogManager.clearLogs()
            allLogs = emptyList()
            applyFilter()
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btn_export_log).setOnClickListener {
            val logText = AppLogManager.exportLogs()
            if (logText.isBlank()) {
                Toast.makeText(this, "没有日志可导出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AppLogs", logText))
            
            // 核心修复：导出到系统的 Download 公共目录
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val fileName = "WTF_Log_${System.currentTimeMillis()}.txt"
                val file = File(downloadsDir, fileName)
                file.writeText(logText)
                Toast.makeText(this, "已复制到剪贴板\n并保存至：Download/$fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // 兜底（比如某些系统权限问题）
                val fallbackFile = File(getExternalFilesDir(null), "wtf_debug_log.txt")
                fallbackFile.writeText(logText)
                Toast.makeText(this, "系统目录不可写，已保存至：\n${fallbackFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
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
                1 -> // 网络
                    it.tag.contains("网络层") || it.tag.contains("详情页") || it.message.contains("【网络层】") || it.message.contains("【详情页】")
                2 -> // 业务
                    it.tag.contains("业务层") || it.tag.contains("UI层") || it.tag.contains("Adapter") || it.tag.contains("状态层") || it.tag.contains("任务层") ||
                    it.message.contains("【业务层】") || it.message.contains("【UI层】") || it.message.contains("【Adapter】") || it.message.contains("【状态层】") || it.message.contains("【任务层】")
                3 -> // 报错
                    it.level >= Log.WARN
                else -> true
            }
        }
        adapter.submit(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
                val ctx = holder.itemView.context
                val intent = android.content.Intent(ctx, LogDetailActivity::class.java).apply {
                    putExtra("time", item.time)
                    putExtra("level", item.level)
                    putExtra("tag", item.tag)
                    putExtra("message", item.message)
                    putExtra("stack", item.stackTrace)
                }
                ctx.startActivity(intent)
            }
        }

        override fun getItemCount() = list.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tv_log_header)
            val tvMessage: TextView = view.findViewById(R.id.tv_log_message)
        }
    }
}
