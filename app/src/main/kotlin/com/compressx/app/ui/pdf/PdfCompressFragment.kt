package com.compressx.app.ui.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.compressx.app.R
import com.compressx.app.databinding.FragmentPdfCompressBinding
import com.compressx.app.model.PdfCompressionLevel
import com.compressx.app.model.PdfTargetDpi
import com.compressx.app.util.FileUtils

class PdfCompressFragment : Fragment() {

    private var _binding: FragmentPdfCompressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PdfCompressViewModel by viewModels()

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onPdfSelected(it) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPdfCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLevelSelector()
        setupCustomControls()
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

    private fun setupCustomControls() {
        binding.switchCustomMode.setOnCheckedChangeListener { _, checked ->
            binding.layoutCustomControls.isVisible = checked
            viewModel.setCustomMode(checked)
        }

        binding.seekBarPdfQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val quality = progress + 10
                binding.textPdfQualityValue.text = getString(R.string.pdf_image_quality_label, quality)
                viewModel.setImageQuality(quality)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.seekBarPdfQuality.progress = 50
        binding.textPdfQualityValue.text = getString(R.string.pdf_image_quality_label, 60)

        binding.radioGroupDpi.setOnCheckedChangeListener { _, checkedId ->
            val dpi = when (checkedId) {
                R.id.radioDpiOriginal -> PdfTargetDpi.ORIGINAL
                R.id.radioDpi300 -> PdfTargetDpi.DPI_300
                R.id.radioDpi150 -> PdfTargetDpi.DPI_150
                R.id.radioDpi72 -> PdfTargetDpi.DPI_72
                else -> PdfTargetDpi.ORIGINAL
            }
            viewModel.setTargetDpi(dpi)
        }

        binding.chipGroupTargetSize.setOnCheckedStateChangeListener { _, checkedIds ->
            val bytes: Long? = when {
                checkedIds.contains(R.id.chip500kb) -> 500L * 1024
                checkedIds.contains(R.id.chip1mb)   -> 1L * 1024 * 1024
                checkedIds.contains(R.id.chip5mb)   -> 5L * 1024 * 1024
                checkedIds.contains(R.id.chip10mb)  -> 10L * 1024 * 1024
                else -> null
            }
            viewModel.setTargetFileSize(bytes)
        }

        binding.switchRemoveMetadata.setOnCheckedChangeListener { _, checked ->
            viewModel.setRemoveMetadata(checked)
        }
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
            binding.buttonSave.isEnabled = !compressing
        }

        viewModel.compressionResult.observe(viewLifecycleOwner) { result ->
            if (result != null && result.success) {
                binding.groupResult.isVisible = true
                binding.textSavedPath.isVisible = false
                binding.buttonOpenFolder.isVisible = false
                binding.buttonSave.isEnabled = true
                val pct = result.compressionRatio.toInt()
                binding.textResultDetails.text = getString(
                    R.string.result_details,
                    FileUtils.formatFileSize(result.originalSize),
                    FileUtils.formatFileSize(result.compressedSize),
                    pct
                )
                binding.textResultOriginal.text = getString(R.string.result_original, FileUtils.formatFileSize(result.originalSize))
                binding.textResultCompressed.text = getString(R.string.result_compressed, FileUtils.formatFileSize(result.compressedSize))
                binding.textResultSaved.text = getString(R.string.result_saved, "%.1f".format(result.compressionRatio))
            }
        }

        viewModel.savedPath.observe(viewLifecycleOwner) { path ->
            if (!path.isNullOrEmpty()) {
                binding.textSavedPath.text = getString(R.string.saved_to_path, path)
                binding.textSavedPath.isVisible = true
                binding.buttonOpenFolder.isVisible = true
                binding.buttonSave.isEnabled = false
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
            viewModel.compress()
        }

        binding.buttonSave.setOnClickListener {
            val name = viewModel.fileName.value?.takeIf { it.isNotEmpty() } ?: "document.pdf"
            viewModel.saveToDownloads(name)
        }

        binding.buttonOpenFolder.setOnClickListener {
            FileUtils.openCompressXFolder(requireContext())
        }

        binding.buttonShare.setOnClickListener {
            val tempFile = viewModel.tempFile
            if (tempFile != null && tempFile.exists()) {
                shareTempFile(tempFile)
            } else {
                val savedUri = viewModel.savedUri.value
                if (savedUri != null) shareUri(savedUri)
            }
        }
    }

    private fun shareTempFile(file: java.io.File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "com.compressx.app.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
