package com.compressx.app.ui.batch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.compressx.app.R
import com.compressx.app.databinding.FragmentBatchBinding
import com.compressx.app.util.FileUtils

class BatchFragment : Fragment() {

    private var _binding: FragmentBatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by viewModels()
    private lateinit var adapter: BatchAdapter
    private val savedBatchUris = ArrayList<Uri>()

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    private val pickPdfsLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPdfs(uris) }

    private val requestWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            saveBatchFiles()
        } else {
            Toast.makeText(requireContext(), "Write permission is required to save files", Toast.LENGTH_SHORT).show()
        }
    }

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
            if (items.isEmpty()) {
                binding.buttonShareAll.isVisible = false
                binding.textBatchSaveSuccess.isVisible = false
                binding.buttonOpenFolderBatch.isVisible = false
            }
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

        viewModel.allDone.observe(viewLifecycleOwner) { done ->
            val hasSucceeded = viewModel.getCompletedOutputUris().isNotEmpty()
            binding.buttonShareAll.isVisible = done && hasSucceeded
            if (done && hasSucceeded) {
                saveBatchFiles()
            }
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
            binding.textBatchSaveSuccess.isVisible = false
            binding.buttonOpenFolderBatch.isVisible = false
            viewModel.startCompression()
        }

        binding.buttonClearAll.setOnClickListener {
            viewModel.clearAll()
            savedBatchUris.clear()
        }

        binding.buttonShareAll.setOnClickListener {
            shareAllCompleted()
        }

        binding.buttonOpenFolderBatch.setOnClickListener {
            FileUtils.openCompressXFolder(requireContext())
        }
    }

    private fun saveBatchFiles() {
        savedBatchUris.clear()
        val completedCacheUris = viewModel.getCompletedOutputUris()
        if (completedCacheUris.isEmpty()) return

        checkAndRequestWritePermission {
            var successCount = 0
            for (cacheUri in completedCacheUris) {
                try {
                    val file = java.io.File(cacheUri.path ?: continue)
                    if (file.exists()) {
                        val fileName = file.name
                        val mimeType = if (fileName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                        val saveResult = FileUtils.saveToCompressX(requireContext(), file, fileName, mimeType)
                        if (saveResult.success && saveResult.uri != null) {
                            successCount++
                            savedBatchUris.add(saveResult.uri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (successCount > 0) {
                val folderPath = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CompressX").absolutePath
                binding.textBatchSaveSuccess.text = "Saved $successCount files to: $folderPath"
                binding.textBatchSaveSuccess.isVisible = true
                binding.buttonOpenFolderBatch.isVisible = true
                Toast.makeText(requireContext(), "Saved $successCount files to CompressX folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestWritePermission(onPermissionGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted()
            } else {
                requestWritePermissionLauncher.launch(permission)
            }
        } else {
            onPermissionGranted()
        }
    }

    private fun shareAllCompleted() {
        val urisToShare = if (savedBatchUris.isNotEmpty()) savedBatchUris else viewModel.getCompletedOutputUris()
        if (urisToShare.isEmpty()) return

        val contentUris = ArrayList<Uri>(urisToShare.mapNotNull { fileUri ->
            try {
                FileUtils.getShareableUri(requireContext(), fileUri)
            } catch (_: Exception) {
                null
            }
        })

        if (contentUris.isEmpty()) return

        val intent = if (contentUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
