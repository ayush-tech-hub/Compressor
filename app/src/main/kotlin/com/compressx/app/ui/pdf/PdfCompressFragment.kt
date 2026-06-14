package com.compressx.app.ui.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.compressx.app.R
import com.compressx.app.databinding.FragmentPdfCompressBinding
import com.compressx.app.model.PdfCompressionLevel
import com.compressx.app.util.FileUtils
import androidx.activity.result.contract.ActivityResultContracts

class PdfCompressFragment : Fragment() {

    private var _binding: FragmentPdfCompressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PdfCompressViewModel by viewModels()
    private var pendingOutputUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPdfSelected(it) }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            pendingOutputUri = it
            viewModel.compress(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLevelSelector()
        setupObservers()
        setupClickListeners()
    }

    private fun setupLevelSelector() {
        binding.radioGroupLevel.setOnCheckedChangeListener { _, checkedId ->
            val level = when (checkedId) {
                R.id.radioLow -> PdfCompressionLevel.LOW
                R.id.radioBalanced -> PdfCompressionLevel.BALANCED
                R.id.radioMaximum -> PdfCompressionLevel.MAXIMUM
                else -> PdfCompressionLevel.BALANCED
            }
            viewModel.setCompressionLevel(level)
        }
        binding.radioBalanced.isChecked = true
    }

    private fun setupObservers() {
        viewModel.selectedUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                binding.imagePdfIcon.isVisible = true
                binding.textNoPdfSelected.isVisible = false
                binding.buttonCompress.isEnabled = true
                binding.cardFileInfo.isVisible = true
                binding.groupResult.isVisible = false
            }
        }

        viewModel.fileName.observe(viewLifecycleOwner) { name ->
            binding.textPdfFileName.text = name
        }

        viewModel.originalSize.observe(viewLifecycleOwner) { size ->
            binding.textOriginalSize.text = getString(R.string.original_size, FileUtils.formatFileSize(size))
        }

        viewModel.estimatedSize.observe(viewLifecycleOwner) { size ->
            binding.textEstimatedSize.text = getString(R.string.estimated_size, FileUtils.formatFileSize(size))
        }

        viewModel.isCompressing.observe(viewLifecycleOwner) { compressing ->
            binding.progressCompressing.isVisible = compressing
            binding.buttonCompress.isEnabled = !compressing && viewModel.selectedUri.value != null
        }

        viewModel.compressionResult.observe(viewLifecycleOwner) { result ->
            if (result != null && result.success) {
                binding.groupResult.isVisible = true
                binding.textResultOriginal.text = getString(R.string.result_original, FileUtils.formatFileSize(result.originalSize))
                binding.textResultCompressed.text = getString(R.string.result_compressed, FileUtils.formatFileSize(result.compressedSize))
                binding.textResultSaved.text = getString(R.string.result_saved, "%.1f".format(result.compressionRatio))
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonSelectPdf.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        binding.buttonCompress.setOnClickListener {
            val uri = viewModel.selectedUri.value ?: return@setOnClickListener
            val name = viewModel.fileName.value ?: "document"
            val outName = FileUtils.buildCompressedFileName(name)
            saveFileLauncher.launch(outName)
        }

        binding.buttonShare.setOnClickListener {
            pendingOutputUri?.let { shareFile(it) }
        }

        binding.buttonOpen.setOnClickListener {
            pendingOutputUri?.let { openFile(it) }
        }

        binding.buttonDelete.setOnClickListener {
            pendingOutputUri?.let {
                requireContext().contentResolver.delete(it, null, null)
                Toast.makeText(requireContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show()
                binding.groupResult.isVisible = false
                pendingOutputUri = null
                viewModel.clearResult()
            }
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun openFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
