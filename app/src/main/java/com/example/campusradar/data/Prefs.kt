package com.example.campusradar.data

import android.content.Context

/**
 * 简单的配置存储（SharedPreferences）。
 * 所有可调参数集中在这里，方便你后续加开关。
 */
object Prefs {

    private const val FILE = "campusradar_prefs"

    private const val K_SOURCE_MODE = "source_mode"   // demo / json / rss
    private const val K_SOURCE_URL = "source_url"
    private const val K_AUTO_ENABLED = "auto_enabled"
    private const val K_AUTO_HOUR = "auto_hour"       // 每天几点采集（24 小时制）
    private const val K_AUTO_MINUTE = "auto_minute"
    private const val K_LAST_COLLECT = "last_collect" // 上次采集时间戳
    private const val K_NOTIFY = "notify_enabled"
    private const val K_SEARCH_ENGINE = "search_engine"   // 没有网申链接时用来兜底搜索
    private const val K_EXTERNAL_BROWSER = "external_browser" // 网申页用系统浏览器打开（否则用内置浏览器）

    const val MODE_DEMO = "demo"
    const val MODE_JSON = "json"
    const val MODE_RSS = "rss"

    /** 兜底搜索模板，%s 是 URL 编码后的关键词。想换引擎改这里就行 */
    val SEARCH_ENGINES = mapOf(
        "百度" to "https://m.baidu.com/s?word=%s",
        "必应" to "https://cn.bing.com/search?q=%s",
        "搜狗" to "https://m.sogou.com/web/searchList.jsp?keyword=%s",
        "牛客" to "https://www.nowcoder.com/search?query=%s"
    )

    /** 默认数据源：开源社区维护的校招信息 JSON，可直接在设置页替换成你自己的接口 */
    const val DEFAULT_URL =
        "https://raw.githubusercontent.com/helloflask/campus-recruitment/main/data/items.json"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getMode(context: Context): String =
        sp(context).getString(K_SOURCE_MODE, MODE_DEMO) ?: MODE_DEMO

    fun setMode(context: Context, mode: String) {
        sp(context).edit().putString(K_SOURCE_MODE, mode).apply()
    }

    fun getUrl(context: Context): String =
        sp(context).getString(K_SOURCE_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun setUrl(context: Context, url: String) {
        sp(context).edit().putString(K_SOURCE_URL, url).apply()
    }

    fun isAutoEnabled(context: Context): Boolean =
        sp(context).getBoolean(K_AUTO_ENABLED, true)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(K_AUTO_ENABLED, enabled).apply()
    }

    fun getAutoTime(context: Context): Pair<Int, Int> =
        sp(context).getInt(K_AUTO_HOUR, 9) to sp(context).getInt(K_AUTO_MINUTE, 0)

    fun setAutoTime(context: Context, hour: Int, minute: Int) {
        sp(context).edit().putInt(K_AUTO_HOUR, hour).putInt(K_AUTO_MINUTE, minute).apply()
    }

    fun isNotifyEnabled(context: Context): Boolean =
        sp(context).getBoolean(K_NOTIFY, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(K_NOTIFY, enabled).apply()
    }

    fun getSearchEngine(context: Context): String =
        sp(context).getString(K_SEARCH_ENGINE, "百度") ?: "百度"

    fun setSearchEngine(context: Context, name: String) {
        sp(context).edit().putString(K_SEARCH_ENGINE, name).apply()
    }

    /** true = 用系统浏览器打开网申页；false = 用 App 内置浏览器（不跳出应用） */
    fun useExternalBrowser(context: Context): Boolean =
        sp(context).getBoolean(K_EXTERNAL_BROWSER, false)

    fun setExternalBrowser(context: Context, external: Boolean) {
        sp(context).edit().putBoolean(K_EXTERNAL_BROWSER, external).apply()
    }

    fun getLastCollect(context: Context): Long =
        sp(context).getLong(K_LAST_COLLECT, 0L)

    fun setLastCollect(context: Context, time: Long) {
        sp(context).edit().putLong(K_LAST_COLLECT, time).apply()
    }

    fun lastCollectText(context: Context): String {
        val t = getLastCollect(context)
        if (t == 0L) return "尚未采集"
        val diff = System.currentTimeMillis() - t
        val min = diff / 60000
        return when {
            min < 1 -> "刚刚采集"
            min < 60 -> "${min} 分钟前采集"
            min < 60 * 24 -> "${min / 60} 小时前采集"
            else -> "${min / (60 * 24)} 天前采集"
        }
    }
}
