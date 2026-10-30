package com.example.campusradar

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.campusradar.data.DbHelper
import com.example.campusradar.model.RecruitItem
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 新增 / 编辑一条招聘信息。
 * 传 EXTRA_ID 进来就是编辑，不传就是新增。
 */
class AddEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_id"
    }

    private var editing: RecruitItem? = null

    private lateinit var etCompany: EditText
    private lateinit var etTitle: EditText
    private lateinit var etCity: EditText
    private lateinit var etUrl: EditText
    private lateinit var tvDeadline: TextView
    private lateinit var spSeason: Spinner
    private lateinit var spStatus: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        com.example.campusradar.ui.Edge.setup(findViewById(R.id.root), toolbar)

        etCompany = findViewById(R.id.etCompany)
        etTitle = findViewById(R.id.etTitle)
        etCity = findViewById(R.id.etCity)
        etUrl = findViewById(R.id.etUrl)
        tvDeadline = findViewById(R.id.tvDeadline)
        spSeason = findViewById(R.id.spSeason)
        spStatus = findViewById(R.id.spStatus)

        spSeason.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, RecruitItem.SEASONS.toList()
        )
        spStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, RecruitItem.STATUS_NAMES.toList()
        )

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id > 0) {
            editing = DbHelper.get(this).getById(id)
            fill(editing)
        }

        if (editing == null) spStatus.setSelection(RecruitItem.STATUS_TODO)

        findViewById<Button>(R.id.btnPickDate).setOnClickListener { pickDate() }
        findViewById<Button>(R.id.btnClearDate).setOnClickListener { tvDeadline.text = "" }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        val btnDelete: Button = findViewById(R.id.btnDelete)
        if (editing == null) {
            btnDelete.visibility = View.GONE
        } else {
            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage("确定删除这条信息吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        DbHelper.get(this).delete(editing!!.id)
                        finish()
                    }
                    .show()
            }
        }
    }

    private fun fill(item: RecruitItem?) {
        item ?: return
        etCompany.setText(item.company)
        etTitle.setText(item.title)
        etCity.setText(item.city)
        etUrl.setText(item.url)
        tvDeadline.text = item.deadline
        spSeason.setSelection(RecruitItem.SEASONS.indexOf(item.season).coerceAtLeast(0))
        spStatus.setSelection(item.status.coerceIn(0, RecruitItem.STATUS_NAMES.lastIndex))
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        android.app.DatePickerDialog(
            this,
            { _, y, m, d ->
                tvDeadline.text = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun save() {
        val company = etCompany.text.toString().trim()
        val title = etTitle.text.toString().trim()
        if (company.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "公司和岗位不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val deadline = tvDeadline.text.toString().trim()
        if (deadline.isNotEmpty() &&
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(deadline) }.getOrNull() == null
        ) {
            Toast.makeText(this, "日期格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show()
            return
        }

        val item = (editing ?: RecruitItem()).apply {
            this.company = company
            this.title = title
            this.city = etCity.text.toString().trim()
            // 用户可能只填了 www.xxx.com，这里补上 https:// 才能打开
            this.url = com.example.campusradar.ui.Links.normalize(etUrl.text.toString().trim())
            this.deadline = deadline
            this.season = RecruitItem.SEASONS[spSeason.selectedItemPosition]
            this.status = spStatus.selectedItemPosition
            if (editing == null) {
                this.source = "手动添加"
                this.isNew = false
            }
        }

        val db = DbHelper.get(this)
        if (editing == null) {
            val rowId = db.insert(item)
            if (rowId == -1L) {
                Toast.makeText(this, "已存在相同的公司+岗位，未重复添加", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            db.update(item)
        }
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
