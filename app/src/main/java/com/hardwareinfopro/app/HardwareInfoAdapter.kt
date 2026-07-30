package com.hardwareinfopro.app

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 硬件信息列表适配器 - 使用卡片式分类展示
 */
class HardwareInfoAdapter(
    private var sections: List<InfoSection> = emptyList()
) : RecyclerView.Adapter<HardwareInfoAdapter.SectionViewHolder>() {

    fun updateData(newSections: List<InfoSection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_header, parent, false)
        return SectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size

    inner class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorBar: View = itemView.findViewById(R.id.categoryColorBar)
        private val titleText: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        private val expandIcon: ImageView = itemView.findViewById(R.id.ivExpand)
        private val itemsContainer: LinearLayout = itemView.findViewById(R.id.itemsContainer)

        fun bind(section: InfoSection) {
            // 设置分类标题
            titleText.text = section.category.title

            // 设置分类颜色条
            val color = ContextCompat.getColor(itemView.context, section.category.colorResId)
            colorBar.setBackgroundColor(color)

            // 设置展开/折叠状态
            updateExpandState(section.isExpanded, false)

            // 填充子项
            itemsContainer.removeAllViews()
            for (item in section.items) {
                val rowView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_info_row, itemsContainer, false)

                val keyText = rowView.findViewById<TextView>(R.id.tvKey)
                val valueText = rowView.findViewById<TextView>(R.id.tvValue)

                keyText.text = item.key
                valueText.text = item.value

                // 添加分割线
                val divider = View(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        topMargin = 4
                        bottomMargin = 4
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                }
                itemsContainer.addView(rowView)
                itemsContainer.addView(divider)
            }

            // 点击展开/折叠
            itemView.setOnClickListener {
                section.isExpanded = !section.isExpanded
                updateExpandState(section.isExpanded, true)
            }
        }

        private fun updateExpandState(expanded: Boolean, animate: Boolean) {
            if (expanded) {
                itemsContainer.visibility = View.VISIBLE
                if (animate) {
                    ObjectAnimator.ofFloat(expandIcon, "rotation", 180f, 0f).apply {
                        duration = 200
                        start()
                    }
                } else {
                    expandIcon.rotation = 0f
                }
            } else {
                if (animate) {
                    ObjectAnimator.ofFloat(expandIcon, "rotation", 0f, 180f).apply {
                        duration = 200
                        start()
                    }
                    itemsContainer.visibility = View.GONE
                } else {
                    expandIcon.rotation = 180f
                    itemsContainer.visibility = View.GONE
                }
            }
        }
    }
}
