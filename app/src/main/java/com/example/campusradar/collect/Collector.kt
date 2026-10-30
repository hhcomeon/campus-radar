package com.example.campusradar.collect

import android.content.Context
import android.util.Log
import com.example.campusradar.data.Prefs
import com.example.campusradar.model.RecruitItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.zip.GZIPInputStream

/**
 * 采集引擎 —— 应用的“心脏”。
 *
 * 支持三种数据源模式（在设置页切换）：
 *  1. demo：内置演示数据，每天自动换一批，**完全离线可用**，方便你先把 App 跑起来看效果
 *  2. json：GET 一个返回 JSON 的接口，格式见 [parseJson]
 *  3. rss ：GET 一个 RSS / Atom 源，用正则抽取 item
 *
 * 想接真实的校招信息源，只需要把你的接口地址填进设置页、
 * 保证返回格式符合 [parseJson] 就行，不用改这里的代码。
 */
object Collector {

    private const val TAG = "Collector"
    private const val TIMEOUT = 15_000

    class Result(
        val items: List<RecruitItem> = emptyList(),
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null
    }

    /** 按当前配置采集一次（同步方法，请在子线程调用） */
    fun collect(context: Context): Result {
        return try {
            when (Prefs.getMode(context)) {
                Prefs.MODE_JSON -> {
                    val url = Prefs.getUrl(context)
                    val raw = httpGet(url)
                    Result(parseJson(raw, url))
                }
                Prefs.MODE_RSS -> {
                    val url = Prefs.getUrl(context)
                    val raw = httpGet(url)
                    Result(parseRss(raw, url))
                }
                else -> Result(buildDemoItems())
            }
        } catch (e: Exception) {
            Log.e(TAG, "collect failed", e)
            Result(error = e.message ?: "采集失败")
        }
    }

    // ------------------------------------------------------------------ 网络

    @Throws(Exception::class)
    fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "CampusRadar/1.0 (Android)")
            setRequestProperty("Accept", "application/json,text/xml,*/*")
            setRequestProperty("Accept-Encoding", "gzip")
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            val stream = if ("gzip".equals(conn.contentEncoding, true))
                GZIPInputStream(conn.inputStream) else conn.inputStream
            stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------- JSON 解析

    /**
     * 支持的 JSON 格式（两种都行）：
     *
     * {"items":[{"company":"腾讯","title":"2027 校招 后端开发","season":"秋招",
     *            "city":"深圳","deadline":"2026-11-20","url":"https://...","tags":"后端"}]}
     *
     * 或直接给数组：[{...},{...}]
     */
    fun parseJson(raw: String, source: String = "json"): List<RecruitItem> {
        val arr: JSONArray = runCatching {
            val obj = JSONObject(raw)
            when {
                obj.has("items") -> obj.getJSONArray("items")
                obj.has("data") -> obj.getJSONArray("data")
                obj.has("list") -> obj.getJSONArray("list")
                else -> JSONArray()
            }
        }.getOrElse { JSONArray(raw) }

        val now = System.currentTimeMillis()
        val out = ArrayList<RecruitItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val company = o.optString("company").ifBlank { guessCompany(o.optString("title")) }
            val title = o.optString("title")
            if (title.isBlank()) continue
            out.add(
                RecruitItem(
                    company = company,
                    title = title,
                    season = normalizeSeason(o.optString("season"), title),
                    city = o.optString("city"),
                    deadline = normalizeDeadline(o.optString("deadline")),
                    url = o.optString("url").ifBlank { o.optString("link") },
                    source = o.optString("source").ifBlank { source },
                    collectedAt = now,
                    isNew = true
                )
            )
        }
        return out
    }

    // -------------------------------------------------------------- RSS 解析

    /** 轻量 RSS/Atom 解析：抽取 <item>/<entry> 里的 title / link / pubDate */
    fun parseRss(raw: String, source: String = "rss"): List<RecruitItem> {
        val now = System.currentTimeMillis()
        val blocks = ArrayList<String>()
        Regex("<(item|entry)[\\s>][\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
            .findAll(raw).forEach { blocks.add(it.value) }

        val out = ArrayList<RecruitItem>()
        for (b in blocks) {
            val title = decodeXml(pick(b, "title"))
            if (title.isBlank()) continue
            val link = pick(b, "link").ifBlank {
                Regex("href\\s*=\\s*\"([^\"]+)\"").find(b)?.groupValues?.get(1) ?: ""
            }
            out.add(
                RecruitItem(
                    company = guessCompany(title),
                    title = title,
                    season = normalizeSeason("", title),
                    city = guessCity(title),
                    deadline = normalizeDeadline(pick(b, "pubDate")),
                    url = link,
                    source = source,
                    collectedAt = now,
                    isNew = true
                )
            )
        }
        return out
    }

    private fun pick(block: String, tag: String): String {
        val m = Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE).find(block)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun decodeXml(s: String): String = s
        .replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>")) { it.groupValues[1] }
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("<[^>]+>"), "")
        .trim()

    // ---------------------------------------------------------------- 归一化

    fun normalizeSeason(explicit: String, title: String): String {
        val s = explicit.trim()
        if (s.contains("春")) return RecruitItem.SEASON_SPRING
        if (s.contains("实习") || s.contains("Intern")) return RecruitItem.SEASON_INTERN
        if (s.contains("秋")) return RecruitItem.SEASON_AUTUMN
        // 没显式给就按标题猜
        if (title.contains("春招") || title.contains("春季")) return RecruitItem.SEASON_SPRING
        if (title.contains("实习") || title.contains("日常实习")) return RecruitItem.SEASON_INTERN
        return RecruitItem.SEASON_AUTUMN
    }

    /** 把各种日期写法统一成 yyyy-MM-dd；解析不了就返回空串 */
    fun normalizeDeadline(rawDate: String): String {
        val s = rawDate.trim()
        if (s.isEmpty()) return ""
        val patterns = listOf(
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd",
            "EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (p in patterns) {
            runCatching {
                return SimpleDateFormat(p, Locale.US).format(SimpleDateFormat(p, Locale.US).parse(s)!!)
            }
        }
        // 常见中文写法：11月20日 / 11-20
        Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日").find(s)?.let {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            return String.format(Locale.US, "%04d-%02d-%02d", year, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return ""
    }

    private val CITY_KEYWORDS = listOf(
        "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "南京",
        "苏州", "长沙", "重庆", "天津", "厦门", "珠海", "合肥", "郑州", "青岛", "远程"
    )

    fun guessCity(title: String): String =
        CITY_KEYWORDS.firstOrNull { title.contains(it) } ?: ""

    /** 从标题里猜公司名：取第一个分隔符前的片段，或直接取前 6 个字 */
    fun guessCompany(title: String): String {
        val t = title.trim()
        if (t.isEmpty()) return "未知公司"
        val head = Regex("^[^\\s|｜\\[【（(、,，:：-]+").find(t)?.value ?: t
        return if (head.length <= 2) t.take(6) else head.take(12)
    }

    // ------------------------------------------------------------ 演示数据源

    /**
     * 公司 -> 官方校招网申入口。
     * 演示数据直接带这些真实地址，点「网申投递」就能跳过去。
     * 换成你自己的数据源后，这里的链接会被数据源里的 url 字段覆盖。
     */
    private val COMPANIES = linkedMapOf(
        "腾讯" to "https://join.qq.com/",
        "阿里巴巴" to "https://campus-talent.alibaba.com/?lang=zh",
        "字节跳动" to "https://jobs.bytedance.com/campus",
        "美团" to "https://campus.meituan.com/",
        "京东" to "https://campus.jd.com/",
        "百度" to "https://talent.baidu.com/",
        "网易" to "https://campus.163.com/",
        "小米" to "https://hr.xiaomi.com/campus",
        "华为" to "https://career.huawei.com/reccampportal",
        "拼多多" to "https://campus.pinduoduo.com/",
        "蚂蚁集团" to "https://talent.antgroup.com/campus",
        "滴滴" to "https://talent.didiglobal.com/campus",
        "快手" to "https://campus.kuaishou.cn/",
        "小红书" to "https://job.xiaohongshu.com/campus",
        "哔哩哔哩" to "https://jobs.bilibili.com/campus",
        "招商银行" to "https://career.cmbchina.com/",
        "中国移动" to "https://job.10086.cn/",
        "国家电网" to "http://zhaopin.sgcc.com.cn/",
        "比亚迪" to "https://job.byd.com/",
        "大疆创新" to "https://we.dji.com/campus",
        "商汤科技" to "https://hr.sensetime.com/",
        "蔚来" to "https://nio.jobs.feishu.cn/campus"
    )
    private val POSITIONS = listOf(
        "后端开发工程师", "前端开发工程师", "安卓开发工程师", "算法工程师",
        "测试开发工程师", "数据分析师", "产品经理", "运维开发工程师",
        "客户端开发工程师", "机器学习研究员", "安全工程师", "UI 设计师"
    )
    private val CITIES = listOf("深圳", "北京", "上海", "杭州", "广州", "成都", "武汉", "南京", "西安", "远程")

    /**
     * 生成演示数据。以“今天是第几天”为随机种子，
     * 保证同一天多次采集结果一致，第二天会自动换一批新公司/岗位。
     */
    fun buildDemoItems(): List<RecruitItem> {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        val rnd = Random(day.toLong())
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        val count = 8 + rnd.nextInt(5)
        val used = HashSet<String>()
        val out = ArrayList<RecruitItem>()

        val names = COMPANIES.keys.toList()

        repeat(count) {
            val company = names[rnd.nextInt(names.size)]
            val position = POSITIONS[rnd.nextInt(POSITIONS.size)]
            val title = "${currentGradYear()}届校园招聘 · $position"
            if (!used.add(RecruitItem.key(company, title))) return@repeat

            val offset = 5 + rnd.nextInt(45)
            val dd = fmt.format(Date(now + offset * 86_400_000L))
            val season = when (Calendar.getInstance().get(Calendar.MONTH)) {
                in 2..5 -> RecruitItem.SEASON_SPRING      // 3-6 月算春招季
                in 8..11 -> RecruitItem.SEASON_AUTUMN     // 9-12 月算秋招季
                else -> if (rnd.nextBoolean()) RecruitItem.SEASON_INTERN else RecruitItem.SEASON_AUTUMN
            }
            out.add(
                RecruitItem(
                    company = company,
                    title = title,
                    season = season,
                    city = CITIES[rnd.nextInt(CITIES.size)],
                    deadline = dd,
                    // 直接带官方校招网申地址，点「网申投递」就能跳过去
                    url = COMPANIES[company] ?: "",
                    source = "内置演示数据",
                    collectedAt = now,
                    isNew = true
                )
            )
        }
        return out
    }

    /** 根据当前月份推算届数：9 月之后算下一届 */
    private fun currentGradYear(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) + if (cal.get(Calendar.MONTH) >= Calendar.SEPTEMBER) 1 else 0
    }
}
