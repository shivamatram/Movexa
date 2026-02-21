package com.example.movexa.ui.dashboard.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemDriverRankingBinding
import com.example.movexa.service.AnalyticsEngine.DriverRanking

/**
 * DriverRankingAdapter — displays driver performance rankings with rank badges.
 *
 * ─── Features ────────────────────────────────────────────────────────────
 *  • Gold / silver / bronze rank badges for top 3
 *  • Score displayed prominently with grade
 *  • Trip count, violations, and mileage in detail row
 *  • DiffUtil for efficient updates
 */
class DriverRankingAdapter :
    ListAdapter<DriverRanking, DriverRankingAdapter.DriverRankingViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DriverRanking>() {
            override fun areItemsTheSame(old: DriverRanking, new: DriverRanking): Boolean =
                old.driverId == new.driverId

            override fun areContentsTheSame(old: DriverRanking, new: DriverRanking): Boolean =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverRankingViewHolder {
        val binding = ItemDriverRankingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DriverRankingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DriverRankingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DriverRankingViewHolder(
        private val binding: ItemDriverRankingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DriverRanking) {
            val ctx = binding.root.context

            // Rank badge
            binding.tvRank.text = ctx.getString(R.string.analytics_rank_header, item.rank)
            val badgeColor = when (item.rank) {
                1 -> R.color.analytics_rank_gold
                2 -> R.color.analytics_rank_silver
                3 -> R.color.analytics_rank_bronze
                else -> R.color.text_hint
            }
            binding.viewRankBg.background.setTint(ContextCompat.getColor(ctx, badgeColor))

            // Driver name
            binding.tvDriverName.text = item.driverName

            // Detail row
            binding.tvTrips.text = ctx.getString(R.string.analytics_rank_trips, item.completedTrips)
            binding.tvViolations.text = ctx.getString(R.string.analytics_rank_violations, item.violationsCount)
            binding.tvMileage.text = item.mileageDisplay

            // Score + grade
            binding.tvScore.text = item.score.toString()
            binding.tvGrade.text = item.grade

            // Color score based on value
            val scoreColor = when {
                item.score >= 80 -> R.color.analytics_score_excellent
                item.score >= 60 -> R.color.analytics_score_good
                item.score >= 40 -> R.color.analytics_score_average
                else -> R.color.analytics_score_risky
            }
            binding.tvScore.setTextColor(ContextCompat.getColor(ctx, scoreColor))
        }
    }
}
