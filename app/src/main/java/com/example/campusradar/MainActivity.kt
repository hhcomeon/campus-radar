package com.example.campusradar

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusradar.collect.AlarmScheduler
import com.example.campusradar.collect.CollectRunner
import com.example.campusradar.data.DbHelper
import com.example.campusradar.data.Prefs
import com.example.campusradar.model.RecruitItem
import com.example.campusradar.ui.ItemAdapter
import com.example.campusradar.ui.Links
import com.example.campusradar.ui.Notify
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * 首页：招聘信息列表 + 筛选 + 搜索。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ItemAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var tvSummary: TextView
    private lateinit var searchEdit: EditText

    /** 招聘季筛选：null = 全部 */
    private var seasonFilter: String? = null
    /** 快捷筛选：全部 / 今日新增 / 收藏 */
    private var quickFilter = QUICK_ALL
    /** 状态筛选：null = 不限 */
    private var statusFilter: Int? = null

    companion object {
        private const val QUICK_ALL = 0
        private const val QUICK_NEW = 1
        private const val QUICK_STAR = 2
        private const val REQ_NOTIFY = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        com.example.campusradar.ui.Edge.setup(findViewById(R.id.root), toolbar)

        Notify.createChannel(this)
        AlarmScheduler.schedule(this)
        askNotificationPermission()

        recycler = findViewById(R.id.recycler)
        emptyView = findViewById(R.id.emptyView)
        tvSummary = findViewById(R.id.tvSummary)
        searchEdit = findViewById(R.id.etSearch)

        adapter = ItemAdapter(
            onOpen = { openItem(it) },
            onApply = { applyItem(it) },
            onNextStatus = { nextStatus(it) },
            onToggleStar = { toggleStar(it) },
            onLongPress = { showItemMenu(it) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupSeasonChips()
        setupQuickChips()

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }

        // 库里没有数据就先自动采一批，避免白屏
        val db = DbHelper.get(this)
        if (db.countAll() == 0) {
            doCollect(silent = true)
        } else if (Prefs.getMode(this) == Prefs.MODE_DEMO && db.countWithoutUrl() > 0) {
            // 老版本存的演示数据没有网申链接，静默补一次，让「网申投递」按钮可用
            doCollect(silent = true)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ------------------------------------------------------------------ 筛选

    private fun setupSeasonChips() {
        val group: ChipGroup = findViewById(R.id.chipSeason)
        val map = mapOf(
            R.id.chipSeasonAll to null,
            R.id.chipAutumn to RecruitItem.SEASON_AUTUMN,
            R.id.chipSpring to RecruitItem.SEASON_SPRING,
            R.id.chipIntern to RecruitItem.SEASON_INTERN
        )
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.setOnClickListener {
                seasonFilter = map[chip.id]
                refresh()
            }
        }
        (group.findViewById<Chip>(R.id.chipSeasonAll)).isChecked = true
    }

    private fun setupQuickChips() {
        val group: ChipGroup = findViewById(R.id.chipQuick)
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.setOnClickListener {
                when (chip.id) {
                    R.id.chipQuickAll -> { quickFilter = QUICK_ALL; statusFilter = null }
                    R.id.chipQuickNew -> { quickFilter = QUICK_NEW; statusFilter = null }
                    R.id.chipQuickStar -> { quickFilter = QUICK_STAR; statusFilter = null }
                    R.id.chipTodo -> { quickFilter = QUICK_ALL; statusFilter = RecruitItem.STATUS_TODO }
                    R.id.chipApplied -> { quickFilter = QUICK_ALL; statusFilter = RecruitItem.STATUS_APPLIED }
                    R.id.chipInterview -> { quickFilter = QUICK_ALL; statusFilter = RecruitItem.STATUS_INTERVIEW }
                    R.id.chipOffer -> { quickFilter = QUICK_ALL; statusFilter = RecruitItem.STATUS_OFFER }
                }
                refresh()
            }
        }
        (group.findViewById<Chip>(R.id.chipQuickAll)).isChecked = true
    }

    private fun refresh() {
        val list = DbHelper.get(this).query(
            season = seasonFilter,
            status = statusFilter,
            onlyNew = quickFilter == QUICK_NEW,
            onlyStar = quickFilter == QUICK_STAR,
            keyword = searchEdit.text.toString()
        )
        adapter.submitList(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        val db = DbHelper.get(this)
        val newCount = db.countNew()
        tvSummary.text = buildString {
            append("共 ${db.countAll()} 条")
            if (newCount > 0) append(" · 今日新增 $newCount")
            append(" · ${Prefs.lastCollectText(this@MainActivity)}")
        }
    }

    // ------------------------------------------------------------------ 交互

    /** 点卡片：打开网申页（没有链接就搜这家公司的投递入口），不自动改状态 */
    private fun openItem(item: RecruitItem) {
        Links.openApply(this, item)
    }

    /** 点「网申投递」：打开网申页，同时把状态从「未投递」推进到「已投递」 */
    private fun applyItem(item: RecruitItem) {
        if (item.status == RecruitItem.STATUS_TODO) {
            DbHelper.get(this).updateStatus(item.id, RecruitItem.STATUS_APPLIED)
            Toast.makeText(this, "已标记投递：${item.company}", Toast.LENGTH_SHORT).show()
            refresh()
        }
        Links.openApply(this, item)
    }

    /** 状态轮转：未投递 -> 已投递 -> 面试中 -> 已Offer -> 已结束 -> 未投递 */
    private fun nextStatus(item: RecruitItem) {
        val next = when (item.status) {
            RecruitItem.STATUS_TODO -> RecruitItem.STATUS_APPLIED
            RecruitItem.STATUS_APPLIED -> RecruitItem.STATUS_INTERVIEW
            RecruitItem.STATUS_INTERVIEW -> RecruitItem.STATUS_OFFER
            RecruitItem.STATUS_OFFER -> RecruitItem.STATUS_CLOSED
            else -> RecruitItem.STATUS_TODO
        }
        DbHelper.get(this).updateStatus(item.id, next)
        Toast.makeText(this, "${item.company} -> ${RecruitItem.statusName(next)}", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun toggleStar(item: RecruitItem) {
        DbHelper.get(this).toggleStar(item.id, !item.starred)
        refresh()
    }

    private fun showItemMenu(item: RecruitItem) {
        val options = arrayOf("打开网申页", "编辑信息", "复制投递链接", "删除这条")
        AlertDialog.Builder(this)
            .setTitle("${item.company} · ${item.title}")
            .setItems(options) { _: DialogInterface, which: Int ->
                when (which) {
                    0 -> Links.openApply(this, item)
                    1 -> startActivity(
                        Intent(this, AddEditActivity::class.java).putExtra(AddEditActivity.EXTRA_ID, item.id)
                    )
                    2 -> {
                        if (item.url.isBlank()) {
                            Toast.makeText(this, "这条没有链接，可先编辑补上网申地址", Toast.LENGTH_SHORT).show()
                        } else {
                            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", item.url))
                            Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: RecruitItem) {
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定删除「${item.company} - ${item.title}」？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                DbHelper.get(this).delete(item.id)
                refresh()
            }
            .show()
    }

    // ------------------------------------------------------------------ 采集

    private fun doCollect(silent: Boolean = false) {
        if (!silent) Toast.makeText(this, "正在采集…", Toast.LENGTH_SHORT).show()
        CollectRunner.runAsync(this) { outcome ->
            if (outcome.isSuccess) {
                Toast.makeText(
                    this,
                    if (outcome.added > 0) "采集完成，新增 ${outcome.added} 条"
                    else "采集完成，暂无新信息",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this, "采集失败：${outcome.error}", Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }

    // -------------------------------------------------------------- 菜单 / 权限

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_collect -> { doCollect(); true }
            R.id.action_clear_new -> {
                DbHelper.get(this).clearNewFlag()
                refresh()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_all -> {
                AlertDialog.Builder(this)
                    .setTitle("清空全部数据")
                    .setMessage("会删除本地所有招聘信息，确定吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空") { _, _ ->
                        DbHelper.get(this).deleteAll()
                        refresh()
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            }
        }
    }
}
