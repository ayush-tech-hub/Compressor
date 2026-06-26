package com.compressx.app.ui.pdf

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var pendingOutputUri: Uri? = null
    private var targetFileName: String? = null
    private var savedOutputUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onPdfSelected(it) } }

    private val requestWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            saveFile()
        } else {
            Toast.makeText(requireContext(), "Write permission is required to save files", Toast.LENGTH_SHORT).show()
        }
    }

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

        // Image quality slider: progress 0–90 maps to quality 10–100
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
                
                val originalName = viewModel.fileName.value ?: FileUtils.getFileName(requireContext(), uri)
                targetFileName = FileUtils.buildCompressedFileName(originalName)
                savedOutputUri = null
                binding.textSaveSuccess.isVisible = false
                binding.buttonOpenFolder.isVisible = false
                binding.buttonSave.isEnabled = true
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
            if (viewModel.selectedUri.value == null) return@setOnClickListener
            val tempFile = FileUtils.createTempFile(requireContext(), "compressed_", "pdf")
            pendingOutputUri = Uri.fromFile(tempFile)
            binding.textSaveSuccess.isVisible = false
            binding.buttonOpenFolder.isVisible = false
            binding.buttonSave.isEnabled = true
            viewModel.compress(pendingOutputUri!!)
        }

        binding.buttonSave.setOnClickListener {
            saveFile()
        }

        binding.buttonShare.setOnClickListener {
            val uriToShare = savedOutputUri ?: pendingOutputUri
            uriToShare?.let { shareFile(it) }
        }

        binding.buttonRename.setOnClickListener {
            viewModel.selectedUri.value?.let { showRenameDialog(it) }
        }

        binding.buttonOpen.setOnClickListener {
            val uriToOpen = savedOutputUri ?: pendingOutputUri
            uriToOpen?.let { openFile(it) }
        }

        binding.buttonOpenFolder.setOnClickListener {
            FileUtils.openCompressXFolder(requireContext())
        }

        binding.buttonDelete.setOnClickListener {
            val uriToDelete = savedOutputUri ?: pendingOutputUri
            uriToDelete?.let {
                try {
                    if (it.scheme == "file") {
                        java.io.File(it.path ?: "").delete()
                    } else {
                        requireContext().contentResolver.delete(it, null, null)
                    }
                } catch (_: Exception) {}
                Toast.makeText(requireContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show()
                binding.groupResult.isVisible = false
                pendingOutputUri = null
                savedOutputUri = null
                viewModel.clearResult()
            }
        }
    }

    private fun saveFile() {
        val tempUri = pendingOutputUri ?: return
        val tempFile = java.io.File(tempUri.path ?: return)
        if (!tempFile.exists()) {
            Toast.makeText(requireContext(), "Compressed file not found. Please compress again.", Toast.LENGTH_SHORT).show()
            return
        }

        checkAndRequestWritePermission {
            val uri = viewModel.selectedUri.value ?: return@checkAndRequestWritePermission
            val originalName = viewModel.fileName.value ?: FileUtils.getFileName(requireContext(), uri)
            val name = targetFileName ?: FileUtils.buildCompressedFileName(originalName)
            val mimeType = "application/pdf"

            val result = FileUtils.saveToCompressX(requireContext(), tempFile, name, mimeType)
            if (result.success && result.uri != null) {
                savedOutputUri = result.uri
                binding.textSaveSuccess.text = "Saved to: ${result.absolutePath}"
                binding.textSaveSuccess.isVisible = true
                binding.buttonOpenFolder.isVisible = true
                binding.buttonSave.isEnabled = false
                Toast.makeText(requireContext(), "File saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
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

    private fun showRenameDialog(uri: Uri) {
        val currentName = targetFileName ?: FileUtils.buildCompressedFileName(
            viewModel.fileName.value?.takeIf { it.isNotEmpty() } ?: FileUtils.getFileName(requireContext(), uri)
        )
        val editText = EditText(requireContext()).apply {
            setText(currentName)
            selectAll()
            hint = getString(R.string.rename_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_title)
            .setView(editText)
            .setPositiveButton(R.string.rename_confirm) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    targetFileName = newName
                    Toast.makeText(requireContext(), R.string.rename_success, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareFile(uri: Uri) {
        val shareableUri = FileUtils.getShareableUri(requireContext(), uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun openFile(uri: Uri) {
        try {
            val shareableUri = FileUtils.getShareableUri(requireContext(), uri)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareableUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
