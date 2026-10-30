package com.example.campusradar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campusradar.R
import com.example.campusradar.model.RecruitItem

/**
 * 首页招聘信息列表适配器。
 *
 * 交互约定：
 *  - 点「网申投递」-> 打开网申页（内置浏览器），并把状态从「未投递」推进到「已投递」
 *  - 点卡片        -> 打开网申页 / 详情页
 *  - 点状态标签    -> 状态轮转：未投递 -> 已投递 -> 面试中 -> 已Offer -> 已结束
 *  - 点星标        -> 收藏 / 取消收藏
 *  - 长按卡片      -> 编辑 / 删除
 */
class ItemAdapter(
    private val onOpen: (RecruitItem) -> Unit,
    private val onApply: (RecruitItem) -> Unit,
    private val onNextStatus: (RecruitItem) -> Unit,
    private val onToggleStar: (RecruitItem) -> Unit,
    private val onLongPress: (RecruitItem) -> Unit
) : ListAdapter<RecruitItem, ItemAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecruitItem>() {
            override fun areItemsTheSame(a: RecruitItem, b: RecruitItem) = a.id == b.id
            override fun areContentsTheSame(a: RecruitItem, b: RecruitItem) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recruit, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCompany: TextView = itemView.findViewById(R.id.tvCompany)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSeason: TextView = itemView.findViewById(R.id.tvSeason)
        private val tvCity: TextView = itemView.findViewById(R.id.tvCity)
        private val tvDeadline: TextView = itemView.findViewById(R.id.tvDeadline)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvNew: TextView = itemView.findViewById(R.id.tvNew)
        private val ivStar: ImageView = itemView.findViewById(R.id.ivStar)
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        private val btnApply: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.btnApply)

        fun bind(item: RecruitItem) {
            tvCompany.text = item.company
            tvTitle.text = item.title
            tvSeason.text = item.season
            tvCity.text = if (item.city.isBlank()) "城市不限" else item.city

            // 截止日期 + 剩余天数
            val left = RecruitItem.daysLeft(item.deadline)
            tvDeadline.text = when {
                item.deadline.isBlank() -> "长期有效"
                left == null -> "截止 ${item.deadline}"
                left < 0 -> "已截止 ${item.deadline}"
                left == 0 -> "今天截止！"
                left <= 3 -> "仅剩 $left 天（${item.deadline}）"
                else -> "截止 ${item.deadline}（剩 $left 天）"
            }
            // 越紧急越红
            tvDeadline.setTextColor(
                itemView.context.getColor(
                    when {
                        left == null || left > 7 -> R.color.text_secondary
                        left <= 0 -> R.color.danger
                        left <= 3 -> R.color.danger
                        else -> R.color.warn
                    }
                )
            )

            tvStatus.text = RecruitItem.statusName(item.status)
            tvStatus.setBackgroundResource(statusBg(item.status))

            tvNew.visibility = if (item.isNew) View.VISIBLE else View.GONE
            ivStar.setImageResource(
                if (item.starred) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )

            // 按钮文案跟着状态走：没链接=找入口，还没投=网申投递，投过了=打开网申
            btnApply.text = when {
                !Links.hasLink(item) -> "找投递入口"
                item.status == RecruitItem.STATUS_TODO -> "网申投递 ↗"
                else -> "打开网申 ↗"
            }
            tvSource.text = if (item.source.isBlank()) "手动录入" else "来源：${item.source}"

            btnApply.setOnClickListener { onApply(item) }
            tvStatus.setOnClickListener { onNextStatus(item) }
            ivStar.setOnClickListener { onToggleStar(item) }
            itemView.setOnClickListener { onOpen(item) }
            itemView.setOnLongClickListener { onLongPress(item); true }
        }

        private fun statusBg(status: Int) = when (status) {
            RecruitItem.STATUS_TODO -> R.drawable.bg_status_todo
            RecruitItem.STATUS_APPLIED -> R.drawable.bg_status_applied
            RecruitItem.STATUS_INTERVIEW -> R.drawable.bg_status_interview
            RecruitItem.STATUS_OFFER -> R.drawable.bg_status_offer
            else -> R.drawable.bg_status_closed
        }
    }
}
