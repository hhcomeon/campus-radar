package com.example.campusradar.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 一条校招/春招/实习招聘信息。
 *
 * 字段说明：
 *  - season   招聘季：秋招 / 春招 / 实习
 *  - deadline 投递截止日期，格式 yyyy-MM-dd，可为空
 *  - status   投递状态，见下方常量
 *  - isNew    是否为最近一次采集新抓到的条目（用于首页“今日新增”高亮）
 */
data class RecruitItem(
    var id: Long = 0,
    var company: String = "",
    var title: String = "",
    var season: String = SEASON_AUTUMN,
    var city: String = "",
    var deadline: String = "",
    var url: String = "",
    var source: String = "",
    var status: Int = STATUS_TODO,
    var starred: Boolean = false,
    var collectedAt: Long = System.currentTimeMillis(),
    var isNew: Boolean = true
) {

    companion object {
        const val STATUS_TODO = 0       // 未投递
        const val STATUS_APPLIED = 1    // 已投递
        const val STATUS_INTERVIEW = 2  // 面试中
        const val STATUS_OFFER = 3      // 已 Offer
        const val STATUS_CLOSED = 4     // 已结束 / 不投了

        const val SEASON_AUTUMN = "秋招"
        const val SEASON_SPRING = "春招"
        const val SEASON_INTERN = "实习"

        val STATUS_NAMES = arrayOf("未投递", "已投递", "面试中", "已Offer", "已结束")
        val SEASONS = arrayOf(SEASON_AUTUMN, SEASON_SPRING, SEASON_INTERN)

        fun statusName(status: Int): String =
            STATUS_NAMES.getOrElse(status) { STATUS_NAMES[0] }

        /** 用于去重：公司与岗位标题相同即视为同一条 */
        fun key(company: String, title: String) = "${company.trim()}|${title.trim()}"

        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        /**
         * 距离截止还有几天。
         * @return 剩余天数，已过期为负数，没有截止日期返回 null
         */
        fun daysLeft(deadline: String): Int? {
            if (deadline.isBlank()) return null
            val target = try {
                DATE_FMT.parse(deadline.trim()) ?: return null
            } catch (e: Exception) {
                return null
            }
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return ((target.time - today) / (24 * 60 * 60 * 1000L)).toInt()
        }
    }
}
