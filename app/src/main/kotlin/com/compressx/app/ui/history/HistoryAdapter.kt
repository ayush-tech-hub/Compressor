package com.compressx.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.compressx.app.R
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.databinding.ItemHistoryBinding
import com.compressx.app.util.FileUtils
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClick: (CompressionHistory) -> Unit,
    private val onItemLongClick: (CompressionHistory) -> Unit
) : ListAdapter<CompressionHistory, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CompressionHistory) {
            binding.textHistoryFileName.text = item.fileName
            binding.textHistorySizes.text = binding.root.context.getString(
                R.string.history_sizes,
                FileUtils.formatFileSize(item.originalSize),
                FileUtils.formatFileSize(item.compressedSize),
                "%.1f".format(item.compressionRatio)
            )
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(Date(item.timestamp))
            binding.textHistoryDate.text = dateStr
            binding.imageHistoryType.setImageResource(
                if (item.fileType == "image") R.drawable.ic_image else R.drawable.ic_pdf
            )
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CompressionHistory>() {
        override fun areItemsTheSame(a: CompressionHistory, b: CompressionHistory) = a.id == b.id
        override fun areContentsTheSame(a: CompressionHistory, b: CompressionHistory) = a == b
    }
}
