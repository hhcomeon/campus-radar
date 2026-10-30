package com.example.campusradar.collect

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.campusradar.data.DbHelper
import com.example.campusradar.data.Prefs
import com.example.campusradar.model.RecruitItem
import java.util.concurrent.Executors

/**
 * 把“采集 -> 去重入库 -> 发通知”这一串动作封装起来，
 * 首页手动刷新和每日定时任务是同一套逻辑。
 */
object CollectRunner {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    class Outcome(
        val added: Int = 0,
        val total: Int = 0,
        val error: String? = null,
        val samples: List<RecruitItem> = emptyList()
    ) {
        val isSuccess: Boolean get() = error == null
    }

    /** 同步执行，必须在子线程调用 */
    fun runSync(context: Context): Outcome {
        val result = Collector.collect(context)
        if (!result.isSuccess || result.items.isEmpty()) {
            return Outcome(error = result.error ?: "本次没有抓到数据", total = DbHelper.get(context).countAll())
        }
        val db = DbHelper.get(context)
        val before = db.countAll()
        // 演示模式下链接永远以代码里的表为准，改完链接重新采集一次就生效
        val demo = Prefs.getMode(context) == Prefs.MODE_DEMO
        val added = db.insertOrIgnore(result.items, overwriteUrl = demo)
        Prefs.setLastCollect(context, System.currentTimeMillis())
        return Outcome(
            added = added,
            total = before + added,
            samples = result.items.take(3)
        )
    }

    /** 异步执行，回调回到主线程 */
    fun runAsync(context: Context, onDone: (Outcome) -> Unit) {
        executor.execute {
            val outcome = runSync(context)
            if (Prefs.isNotifyEnabled(context) && outcome.added > 0) {
                val preview = outcome.samples.joinToString("\n") { "· ${it.company} - ${it.title}" }
                com.example.campusradar.ui.Notify.sendNewItems(context, outcome.added, preview)
            }
            mainHandler.post { onDone(outcome) }
        }
    }
}
