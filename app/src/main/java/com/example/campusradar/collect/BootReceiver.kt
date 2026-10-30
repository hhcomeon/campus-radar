package com.example.campusradar.collect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campusradar.data.Prefs

/** 开机后重新挂上每日采集任务 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            if (Prefs.isAutoEnabled(context)) AlarmScheduler.schedule(context)
        }
    }
}
