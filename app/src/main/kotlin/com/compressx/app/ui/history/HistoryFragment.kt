package com.compressx.app.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.compressx.app.R
import com.compressx.app.data.db.CompressionHistory
import com.compressx.app.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(
            onItemClick = { history -> openFile(history) },
            onItemLongClick = { history -> showDeleteDialog(history) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        viewModel.allHistory.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.textEmptyHistory.isVisible = list.isEmpty()
            binding.recyclerHistory.isVisible = list.isNotEmpty()
        }

        binding.buttonClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear) { _, _ -> viewModel.deleteAll() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun openFile(history: CompressionHistory) {
        val uri = Uri.parse(history.outputPath)
        val mimeType = if (history.fileType == "pdf") "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteDialog(history: CompressionHistory) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_entry)
            .setMessage(R.string.delete_entry_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(history) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
