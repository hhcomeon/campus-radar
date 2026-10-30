package com.example.campusradar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.campusradar.WebActivity
import com.example.campusradar.data.Prefs
import com.example.campusradar.model.RecruitItem
import java.net.URLEncoder

/**
 * 网申链接的打开逻辑，集中在这一个文件里，方便你改。
 *
 * 规则很简单：
 *   1. 这条信息自带 url      -> 直接打开网申页
 *   2. 这条信息没有 url      -> 用搜索引擎搜「公司 + 岗位 + 校园招聘 网申」兜底，
 *                              保证任何一条都能点得出去，不会点了没反应
 *   3. 打开方式：默认用 App 内置浏览器（不跳出应用，能登录、能填简历）；
 *      设置页可以切换成系统浏览器。
 */
object Links {

    fun hasLink(item: RecruitItem): Boolean = item.url.isNotBlank()

    /** 没有网申链接时的兜底搜索地址 */
    fun searchUrl(context: Context, item: RecruitItem): String {
        val kw = buildString {
            append(item.company)
            if (item.title.isNotBlank()) append(" ").append(item.title)
            append(" 校园招聘 网申")
        }
        val tpl = Prefs.SEARCH_ENGINES[Prefs.getSearchEngine(context)]
            ?: Prefs.SEARCH_ENGINES.getValue("百度")
        return tpl.format(URLEncoder.encode(kw, "UTF-8"))
    }

    /** 点「网申投递」走这里 */
    fun openApply(context: Context, item: RecruitItem) {
        val fromSearch = !hasLink(item)
        val url = if (fromSearch) searchUrl(context, item) else normalize(item.url)
        val title = if (fromSearch) "找网申入口：${item.company}" else "${item.company} · 网申"
        open(context, url, title)
    }

    /** 打开一个网址（外部浏览器 / 内置浏览器，取决于设置） */
    fun open(context: Context, rawUrl: String, title: String = "") {
        val url = if (rawUrl.startsWith("http")) rawUrl else normalize(rawUrl)
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || url.isBlank()) {
            Toast.makeText(context, "这条信息没有可用的链接", Toast.LENGTH_SHORT).show()
            return
        }
        android.util.Log.i("CampusRadar", "Links.open external=${Prefs.useExternalBrowser(context)} url=$url")
        if (Prefs.useExternalBrowser(context)) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                )
            }.onFailure {
                Toast.makeText(context, "没找到能打开网页的应用", Toast.LENGTH_SHORT).show()
            }
        } else {
            context.startActivity(
                WebActivity.intent(context, url, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 补协议头：数据源里可能只给了 www.xxx.com */
    fun normalize(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return u
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }
}
