package com.example.campusradar.ui

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 边到边（edge-to-edge）适配。
 *
 * targetSdk >= 35 之后系统强制页面绘制到状态栏 / 导航栏底下，
 * 不处理的话标题会被状态栏时间压住。这里做两件事：
 *   1. 给 Toolbar 加上状态栏高度的顶部内边距（Toolbar 背景色正好填满状态栏区域）
 *   2. 给根布局加底部内边距，避开导航栏和输入法
 */
object Edge {

    fun setup(root: View, toolbar: View?) {
        val ctx = root.context
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
        val actionBarSize = TypedValue.complexToDimensionPixelSize(
            tv.data, ctx.resources.displayMetrics
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.updatePadding(bottom = maxOf(sys.bottom, ime.bottom))

            if (toolbar != null) {
                val lp = toolbar.layoutParams as ViewGroup.MarginLayoutParams
                val target = actionBarSize + sys.top
                if (lp.height != target) {
                    lp.height = target
                    toolbar.layoutParams = lp
                    toolbar.updatePadding(top = sys.top)
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
