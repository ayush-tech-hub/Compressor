package com.compressx.app.ui.batch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.compressx.app.R
import com.compressx.app.databinding.FragmentBatchBinding

class BatchFragment : Fragment() {

    private var _binding: FragmentBatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by viewModels()
    private lateinit var adapter: BatchAdapter

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    private val pickPdfsLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPdfs(uris) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BatchAdapter { item -> viewModel.removeItem(item) }
        binding.recyclerBatch.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBatch.adapter = adapter

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.fileItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items.toList())
            binding.buttonStartCompression.isEnabled = items.isNotEmpty() && viewModel.isRunning.value != true
            binding.textEmptyBatch.isVisible = items.isEmpty()
            binding.recyclerBatch.isVisible = items.isNotEmpty()
        }

        viewModel.overallProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressOverall.progress = progress
        }

        viewModel.isRunning.observe(viewLifecycleOwner) { running ->
            binding.progressOverall.isVisible = running
            binding.buttonStartCompression.isEnabled = !running && (viewModel.fileItems.value?.isNotEmpty() == true)
            binding.buttonAddImages.isEnabled = !running
            binding.buttonAddPdfs.isEnabled = !running
        }

        viewModel.statusText.observe(viewLifecycleOwner) { text ->
            binding.textBatchStatus.text = text
            binding.textBatchStatus.isVisible = text.isNotEmpty()
        }

        viewModel.etaText.observe(viewLifecycleOwner) { eta ->
            binding.textEta.text = eta
            binding.textEta.isVisible = eta.isNotEmpty()
        }
    }

    private fun setupClickListeners() {
        binding.buttonAddImages.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        binding.buttonAddPdfs.setOnClickListener {
            pickPdfsLauncher.launch("application/pdf")
        }

        binding.buttonStartCompression.setOnClickListener {
            viewModel.startCompression()
        }

        binding.buttonClearAll.setOnClickListener {
            viewModel.clearAll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
