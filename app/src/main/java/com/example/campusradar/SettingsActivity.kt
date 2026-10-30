package com.example.campusradar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.campusradar.collect.AlarmScheduler
import com.example.campusradar.collect.CollectRunner
import com.example.campusradar.collect.Collector
import com.example.campusradar.data.Prefs
import com.example.campusradar.ui.Notify
import com.google.android.material.appbar.MaterialToolbar
import java.util.Locale
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

/**
 * 设置页：数据源、每日自动采集时间、通知开关。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var spMode: Spinner
    private lateinit var etUrl: EditText
    private lateinit var swAuto: SwitchCompat
    private lateinit var swNotify: SwitchCompat
    private lateinit var tvAutoTime: TextView
    private lateinit var swExternal: SwitchCompat
    private lateinit var spSearch: Spinner

    private var hour = 9
    private var minute = 0

    private val bg = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        com.example.campusradar.ui.Edge.setup(findViewById(R.id.root), toolbar)

        spMode = findViewById(R.id.spMode)
        etUrl = findViewById(R.id.etUrl)
        swAuto = findViewById(R.id.swAuto)
        swNotify = findViewById(R.id.swNotify)
        tvAutoTime = findViewById(R.id.tvAutoTime)
        swExternal = findViewById(R.id.swExternal)
        spSearch = findViewById(R.id.spSearch)

        spMode.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("内置演示数据（离线可用）", "JSON 接口", "RSS / Atom 订阅源")
        )

        val engines = Prefs.SEARCH_ENGINES.keys.toList()
        spSearch.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, engines
        )
        spSearch.setSelection(engines.indexOf(Prefs.getSearchEngine(this)).coerceAtLeast(0))
        swExternal.isChecked = Prefs.useExternalBrowser(this)

        // 载入当前配置
        val mode = Prefs.getMode(this)
        spMode.setSelection(
            when (mode) {
                Prefs.MODE_JSON -> 1
                Prefs.MODE_RSS -> 2
                else -> 0
            }
        )
        etUrl.setText(Prefs.getUrl(this))
        swAuto.isChecked = Prefs.isAutoEnabled(this)
        swNotify.isChecked = Prefs.isNotifyEnabled(this)
        val (h, m) = Prefs.getAutoTime(this)
        hour = h; minute = m
        updateTimeText()
        syncUrlEnabled()

        spMode.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = syncUrlEnabled()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        tvAutoTime.setOnClickListener { pickTime() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTestSource).setOnClickListener { testSource() }
        findViewById<Button>(R.id.btnTestNotify).setOnClickListener {
            Notify.sendTest(this)
            Toast.makeText(this, "已发送测试通知", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCollectNow).setOnClickListener {
            Toast.makeText(this, "开始采集…", Toast.LENGTH_SHORT).show()
            CollectRunner.runAsync(this) { o ->
                Toast.makeText(
                    this,
                    if (o.isSuccess) "新增 ${o.added} 条" else "失败：${o.error}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun syncUrlEnabled() {
        val needUrl = spMode.selectedItemPosition != 0
        etUrl.isEnabled = needUrl
        etUrl.alpha = if (needUrl) 1f else 0.5f
    }

    private fun updateTimeText() {
        tvAutoTime.text = String.format(Locale.US, "每天 %02d:%02d", hour, minute)
    }

    private fun pickTime() {
        android.app.TimePickerDialog(this, { _, h, m ->
            hour = h; minute = m
            updateTimeText()
        }, hour, minute, true).show()
    }

    private fun save() {
        val mode = when (spMode.selectedItemPosition) {
            1 -> Prefs.MODE_JSON
            2 -> Prefs.MODE_RSS
            else -> Prefs.MODE_DEMO
        }
        Prefs.setMode(this, mode)
        Prefs.setUrl(this, etUrl.text.toString().trim())
        Prefs.setAutoEnabled(this, swAuto.isChecked)
        Prefs.setNotifyEnabled(this, swNotify.isChecked)
        Prefs.setAutoTime(this, hour, minute)
        Prefs.setExternalBrowser(this, swExternal.isChecked)
        Prefs.setSearchEngine(this, spSearch.selectedItem?.toString() ?: "百度")

        if (swAuto.isChecked) AlarmScheduler.schedule(this) else AlarmScheduler.cancel(this)
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** 试连一次数据源，验证地址/格式是否可用 */
    private fun testSource() {
        val mode = when (spMode.selectedItemPosition) {
            1 -> Prefs.MODE_JSON
            2 -> Prefs.MODE_RSS
            else -> Prefs.MODE_DEMO
        }
        val url = etUrl.text.toString().trim()
        Toast.makeText(this, "正在测试…", Toast.LENGTH_SHORT).show()
        bg.execute {
            val msg = try {
                when (mode) {
                    Prefs.MODE_DEMO -> {
                        val n = Collector.buildDemoItems().size
                        "演示数据源正常，可生成 $n 条示例数据"
                    }
                    Prefs.MODE_JSON -> {
                        val raw = Collector.httpGet(url)
                        "连接成功，解析到 ${Collector.parseJson(raw, url).size} 条"
                    }
                    else -> {
                        val raw = Collector.httpGet(url)
                        "连接成功，解析到 ${Collector.parseRss(raw, url).size} 条"
                    }
                }
            } catch (e: Exception) {
                "失败：${e.message}"
            }
            main.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }
}
