package com.example.campusradar.collect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 每日定时采集的接收器。
 * 触发后在后台线程跑一次采集，抓到新条目就发通知。
 */
class DailyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AUTO_COLLECT = "com.example.campusradar.AUTO_COLLECT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTO_COLLECT) return
        Log.d("DailyReceiver", "触发每日采集")
        // goAsync 保证 Broadcast 期间进程不会被立刻回收
        val pending = goAsync()
        CollectRunner.runAsync(context) { outcome ->
            Log.d("DailyReceiver", "采集完成：新增 ${outcome.added} 条")
            pending.finish()
        }
    }
}
