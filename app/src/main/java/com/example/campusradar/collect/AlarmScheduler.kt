package com.example.campusradar.collect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.campusradar.data.Prefs
import java.util.Calendar

/**
 * 每日自动采集的定时器（AlarmManager，无第三方依赖）。
 *
 * 触发后由 [DailyReceiver] 在后台线程跑一次采集，抓到新信息就发通知。
 * 开机后 [BootReceiver] 会重新注册，保证重启不失灵。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val REQ_CODE = 2026

    private fun pendingIntent(context: Context): PendingIntent {
        val i = Intent(context, DailyReceiver::class.java).apply {
            action = DailyReceiver.ACTION_AUTO_COLLECT
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQ_CODE, i, flags)
    }

    /** 按设置里的时间点注册每日重复任务 */
    fun schedule(context: Context) {
        val (hour, minute) = Prefs.getAutoTime(context)
        if (!Prefs.isAutoEnabled(context)) {
            cancel(context)
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)   // 今天这个点已过，就明天再开始
            }
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
        Log.d(TAG, "每日采集已安排：${hour}:${minute}")
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}
