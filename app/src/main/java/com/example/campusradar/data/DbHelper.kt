package com.example.campusradar.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.campusradar.model.RecruitItem

/**
 * 本地 SQLite 数据库。
 * 直接用 SQLiteOpenHelper 而不是 Room，是为了零额外依赖 —— 方便你随时改表结构、加字段。
 *
 * 表结构见 [onCreate]。UNIQUE(company, title) 用来自动去重，
 * 同一条招聘信息重复采集只会保留一条。
 */
class DbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "campusradar.db"
        private const val DB_VERSION = 3
        private const val TABLE = "recruit"

        private const val C_ID = "id"
        private const val C_COMPANY = "company"
        private const val C_TITLE = "title"
        private const val C_SEASON = "season"
        private const val C_CITY = "city"
        private const val C_DEADLINE = "deadline"
        private const val C_URL = "url"
        private const val C_SOURCE = "source"
        private const val C_STATUS = "status"
        private const val C_STARRED = "starred"
        private const val C_COLLECTED_AT = "collected_at"
        private const val C_IS_NEW = "is_new"

        @Volatile
        private var instance: DbHelper? = null

        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_COMPANY TEXT NOT NULL,
                $C_TITLE TEXT NOT NULL,
                $C_SEASON TEXT,
                $C_CITY TEXT,
                $C_DEADLINE TEXT,
                $C_URL TEXT,
                $C_SOURCE TEXT,
                $C_STATUS INTEGER DEFAULT 0,
                $C_STARRED INTEGER DEFAULT 0,
                $C_COLLECTED_AT INTEGER,
                $C_IS_NEW INTEGER DEFAULT 1,
                UNIQUE($C_COMPANY, $C_TITLE)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_deadline ON $TABLE($C_DEADLINE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 版本升级策略很简单：重建表。如果你后续要保留数据，这里改成 ALTER TABLE 即可。
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * 批量写入采集结果，已存在的（company+title 相同）不会重复添加。
     *
     * 但有一种情况要处理：老记录里没有网申链接，而这次采集拿到了链接 ——
     * 这时把链接补进去（只补空链接，不动你改过的状态、收藏）。
     *
     * @param overwriteUrl true 时无条件用新链接覆盖（内置演示数据用，方便改模板后立刻生效）
     * @return 真正新增的条数
     */
    fun insertOrIgnore(items: List<RecruitItem>, overwriteUrl: Boolean = false): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (item in items) {
                val rowId = db.insertWithOnConflict(
                    TABLE, null, toValues(item), SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId != -1L) {
                    added++
                } else if (item.url.isNotBlank()) {
                    val where = if (overwriteUrl) {
                        "$C_COMPANY=? AND $C_TITLE=?"
                    } else {
                        "$C_COMPANY=? AND $C_TITLE=? AND ($C_URL IS NULL OR $C_URL='')"
                    }
                    db.execSQL(
                        "UPDATE $TABLE SET $C_URL=? WHERE $where",
                        arrayOf(item.url, item.company.trim(), item.title.trim())
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    /** 新增一条，返回 rowId；失败返回 -1 */
    fun insert(item: RecruitItem): Long =
        writableDatabase.insertWithOnConflict(TABLE, null, toValues(item), SQLiteDatabase.CONFLICT_IGNORE)

    fun update(item: RecruitItem): Int {
        val values = toValues(item)
        values.put(C_ID, item.id)
        return writableDatabase.update(TABLE, values, "$C_ID=?", arrayOf(item.id.toString()))
    }

    fun updateStatus(id: Long, status: Int) {
        val v = ContentValues().apply { put(C_STATUS, status) }
        writableDatabase.update(TABLE, v, "$C_ID=?", arrayOf(id.toString()))
    }

    fun toggleStar(id: Long, starred: Boolean) {
        val v = ContentValues().apply { put(C_STARRED, if (starred) 1 else 0) }
        writableDatabase.update(TABLE, v, "$C_ID=?", arrayOf(id.toString()))
    }

    /** 把“今日新增”标记清掉，用户看过后就不再高亮 */
    fun clearNewFlag() {
        val v = ContentValues().apply { put(C_IS_NEW, 0) }
        writableDatabase.update(TABLE, v, null, null)
    }

    fun delete(id: Long): Int =
        writableDatabase.delete(TABLE, "$C_ID=?", arrayOf(id.toString()))

    fun deleteAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun getById(id: Long): RecruitItem? {
        readableDatabase.query(TABLE, null, "$C_ID=?", arrayOf(id.toString()), null, null, null)
            .use { c -> return if (c.moveToFirst()) fromCursor(c) else null }
    }

    /**
     * 按条件查询。
     * @param season   招聘季筛选，null 表示全部
     * @param status   状态筛选，null 表示全部
     * @param onlyNew  只看今日新增
     * @param onlyStar 只看收藏
     * @param keyword  关键词（公司 / 岗位 / 城市）
     */
    fun query(
        season: String? = null,
        status: Int? = null,
        onlyNew: Boolean = false,
        onlyStar: Boolean = false,
        keyword: String = ""
    ): List<RecruitItem> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()

        if (!season.isNullOrBlank()) {
            where.add("$C_SEASON=?")
            args.add(season)
        }
        if (status != null) {
            where.add("$C_STATUS=?")
            args.add(status.toString())
        }
        if (onlyNew) where.add("$C_IS_NEW=1")
        if (onlyStar) where.add("$C_STARRED=1")
        val kw = keyword.trim()
        if (kw.isNotEmpty()) {
            where.add("($C_COMPANY LIKE ? OR $C_TITLE LIKE ? OR $C_CITY LIKE ?)")
            args.add("%$kw%"); args.add("%$kw%"); args.add("%$kw%")
        }

        // 收藏优先 -> 截止日期近的优先 -> 采集时间新的优先
        val orderBy = "$C_STARRED DESC, " +
                "CASE WHEN $C_DEADLINE IS NULL OR $C_DEADLINE='' THEN 1 ELSE 0 END ASC, " +
                "$C_DEADLINE ASC, $C_COLLECTED_AT DESC"

        val list = ArrayList<RecruitItem>()
        readableDatabase.query(
            TABLE, null,
            where.joinToString(" AND ").ifEmpty { null },
            args.toTypedArray().ifEmpty { null },
            null, null, orderBy
        ).use { c ->
            while (c.moveToNext()) list.add(fromCursor(c))
        }
        return list
    }

    fun countAll(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun countNew(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE $C_IS_NEW=1", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 没有网申链接的条数（用来判断要不要补一次链接） */
    fun countWithoutUrl(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE $C_URL IS NULL OR $C_URL=''", null
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun toValues(item: RecruitItem) = ContentValues().apply {
        put(C_COMPANY, item.company.trim())
        put(C_TITLE, item.title.trim())
        put(C_SEASON, item.season)
        put(C_CITY, item.city)
        put(C_DEADLINE, item.deadline)
        put(C_URL, item.url)
        put(C_SOURCE, item.source)
        put(C_STATUS, item.status)
        put(C_STARRED, if (item.starred) 1 else 0)
        put(C_COLLECTED_AT, item.collectedAt)
        put(C_IS_NEW, if (item.isNew) 1 else 0)
    }

    private fun fromCursor(c: Cursor) = RecruitItem(
        id = c.getLong(c.getColumnIndexOrThrow(C_ID)),
        company = c.getString(c.getColumnIndexOrThrow(C_COMPANY)),
        title = c.getString(c.getColumnIndexOrThrow(C_TITLE)),
        season = c.getString(c.getColumnIndexOrThrow(C_SEASON)) ?: RecruitItem.SEASON_AUTUMN,
        city = c.getString(c.getColumnIndexOrThrow(C_CITY)) ?: "",
        deadline = c.getString(c.getColumnIndexOrThrow(C_DEADLINE)) ?: "",
        url = c.getString(c.getColumnIndexOrThrow(C_URL)) ?: "",
        source = c.getString(c.getColumnIndexOrThrow(C_SOURCE)) ?: "",
        status = c.getInt(c.getColumnIndexOrThrow(C_STATUS)),
        starred = c.getInt(c.getColumnIndexOrThrow(C_STARRED)) == 1,
        collectedAt = c.getLong(c.getColumnIndexOrThrow(C_COLLECTED_AT)),
        isNew = c.getInt(c.getColumnIndexOrThrow(C_IS_NEW)) == 1
    )
}
