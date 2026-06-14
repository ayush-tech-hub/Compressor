package com.compressx.app.ui.batch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.compressx.app.R
import com.compressx.app.databinding.ItemBatchFileBinding
import com.compressx.app.model.CompressionStatus
import com.compressx.app.model.FileItem
import com.compressx.app.model.FileType
import com.compressx.app.util.FileUtils

class BatchAdapter(
    private val onRemoveClick: (FileItem) -> Unit
) : ListAdapter<FileItem, BatchAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBatchFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBatchFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem) {
            binding.textFileName.text = item.name
            binding.textFileSize.text = FileUtils.formatFileSize(item.size)
            binding.imageFileType.setImageResource(
                if (item.type == FileType.IMAGE) R.drawable.ic_image else R.drawable.ic_pdf
            )

            when (item.status) {
                CompressionStatus.PENDING -> {
                    binding.chipStatus.text = binding.root.context.getString(R.string.status_pending)
                    binding.chipStatus.setChipBackgroundColorResource(R.color.colorSecondary)
                    binding.progressItem.isVisible = false
                }
                CompressionStatus.COMPRESSING -> {
                    binding.chipStatus.text = binding.root.context.getString(R.string.status_compressing)
                    binding.chipStatus.setChipBackgroundColorResource(R.color.colorPrimary)
                    binding.progressItem.isVisible = true
                    binding.progressItem.progress = item.progress
                }
                CompressionStatus.DONE -> {
                    binding.chipStatus.text = binding.root.context.getString(R.string.status_done)
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                    binding.progressItem.isVisible = false
                    if (item.compressedSize > 0) {
                        binding.textFileSize.text = "${FileUtils.formatFileSize(item.size)} → ${FileUtils.formatFileSize(item.compressedSize)}"
                    }
                }
                CompressionStatus.FAILED -> {
                    binding.chipStatus.text = binding.root.context.getString(R.string.status_failed)
                    binding.chipStatus.setChipBackgroundColorResource(R.color.colorError)
                    binding.progressItem.isVisible = false
                }
            }

            binding.buttonRemove.setOnClickListener {
                onRemoveClick(item)
            }
            binding.buttonRemove.isEnabled = item.status != CompressionStatus.COMPRESSING
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem) = oldItem == newItem
    }
}
