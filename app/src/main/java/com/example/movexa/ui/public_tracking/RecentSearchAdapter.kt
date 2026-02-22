package com.example.movexa.ui.public_tracking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.data.model.RecentSearch
import com.example.movexa.databinding.ItemRecentSearchBinding

/**
 * ═══════════════════════════════════════════════════════════════════
 *  RECENT SEARCH ADAPTER
 * ═══════════════════════════════════════════════════════════════════
 *
 * Displays a list of recently searched tracking IDs on the
 * EnterTrackingFragment. Tapping an item re-triggers the search;
 * the ✕ button removes it from history.
 *
 * Uses [ListAdapter] + [DiffUtil] for efficient list updates.
 *
 * @param onItemClick  Callback when the user taps a recent search row
 * @param onRemoveClick Callback when the user taps the remove (✕) button
 *
 * @since 2026-02-22
 */
class RecentSearchAdapter(
    private val onItemClick: (RecentSearch) -> Unit,
    private val onRemoveClick: (RecentSearch) -> Unit
) : ListAdapter<RecentSearch, RecentSearchAdapter.RecentSearchViewHolder>(DIFF_CALLBACK) {

    // ═══════════════════════════════════════════════════════════
    //  VIEW HOLDER
    // ═══════════════════════════════════════════════════════════

    inner class RecentSearchViewHolder(
        private val binding: ItemRecentSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentSearch) {
            binding.tvRecentTrackingId.text = item.trackingId
            binding.tvRecentStatus.text = item.statusLabel
            binding.tvRecentTime.text = item.timeAgoDisplay

            binding.root.setOnClickListener { onItemClick(item) }
            binding.ivRemove.setOnClickListener { onRemoveClick(item) }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ADAPTER OVERRIDES
    // ═══════════════════════════════════════════════════════════

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val binding = ItemRecentSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ═══════════════════════════════════════════════════════════
    //  DIFF CALLBACK
    // ═══════════════════════════════════════════════════════════

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecentSearch>() {
            override fun areItemsTheSame(
                oldItem: RecentSearch,
                newItem: RecentSearch
            ): Boolean = oldItem.trackingId == newItem.trackingId

            override fun areContentsTheSame(
                oldItem: RecentSearch,
                newItem: RecentSearch
            ): Boolean = oldItem == newItem
        }
    }
}
