package com.compressx.app.ui.image

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
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
import com.compressx.app.databinding.FragmentImageCompressBinding
import com.compressx.app.model.CompressionQuality
import com.compressx.app.util.FileUtils

class ImageCompressFragment : Fragment() {

    private var _binding: FragmentImageCompressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ImageCompressViewModel by viewModels()
    private var pendingOutputUri: Uri? = null
    private var targetFileName: String? = null
    private var savedOutputUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onImageSelected(it) } }

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
        _binding = FragmentImageCompressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupQualitySelector()
        setupObservers()
        setupClickListeners()
    }

    private fun setupQualitySelector() {
        binding.radioGroupQuality.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioLow -> {
                    binding.seekBarQuality.isVisible = false
                    binding.textQualityValue.isVisible = false
                    viewModel.setQuality(CompressionQuality.LOW.value)
                }
                R.id.radioMedium -> {
                    binding.seekBarQuality.isVisible = false
                    binding.textQualityValue.isVisible = false
                    viewModel.setQuality(CompressionQuality.MEDIUM.value)
                }
                R.id.radioHigh -> {
                    binding.seekBarQuality.isVisible = false
                    binding.textQualityValue.isVisible = false
                    viewModel.setQuality(CompressionQuality.HIGH.value)
                }
                R.id.radioCustom -> {
                    binding.seekBarQuality.isVisible = true
                    binding.textQualityValue.isVisible = true
                    viewModel.setQuality(binding.seekBarQuality.progress.coerceAtLeast(1))
                }
            }
        }

        binding.seekBarQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val q = progress.coerceAtLeast(1)
                binding.textQualityValue.text = getString(R.string.quality_value, q)
                if (binding.radioCustom.isChecked) viewModel.setQuality(q)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.radioMedium.isChecked = true
        binding.seekBarQuality.progress = 60
        binding.textQualityValue.text = getString(R.string.quality_value, 60)
    }

    private fun setupObservers() {
        viewModel.selectedUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                loadImagePreview(uri, binding.imagePreview)
                binding.imagePreview.isVisible = true
                binding.textSelectHint.isVisible = false
                binding.buttonCompress.isEnabled = true
                binding.cardFileInfo.isVisible = true
                binding.groupResult.isVisible = false
                
                val originalName = FileUtils.getFileName(requireContext(), uri)
                targetFileName = FileUtils.buildCompressedFileName(originalName)
                savedOutputUri = null
                binding.textSaveSuccess.isVisible = false
                binding.buttonOpenFolder.isVisible = false
                binding.buttonSave.isEnabled = true
            }
        }

        viewModel.originalSize.observe(viewLifecycleOwner) { size ->
            binding.textOriginalSize.text = getString(R.string.original_size, FileUtils.formatFileSize(size))
        }

        viewModel.estimatedSize.observe(viewLifecycleOwner) { size ->
            binding.textEstimatedSize.text = getString(R.string.estimated_size, FileUtils.formatFileSize(size))
            val original = viewModel.originalSize.value ?: 1L
            if (original > 0L) {
                val savings = ((1f - size.toFloat() / original.toFloat()) * 100f).toInt()
                binding.textSavingsPercent.text = getString(R.string.savings_percent, savings)
            }
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
                // Show compressed preview
                pendingOutputUri?.let { loadImagePreview(it, binding.imagePreview) }
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
        binding.buttonSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.buttonCompress.setOnClickListener {
            val uri = viewModel.selectedUri.value ?: return@setOnClickListener
            val ext = FileUtils.getExtension(requireContext(), uri).ifEmpty { "jpg" }
            val tempFile = FileUtils.createTempFile(requireContext(), "compressed_", ext)
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
            val originalName = FileUtils.getFileName(requireContext(), uri)
            val name = targetFileName ?: FileUtils.buildCompressedFileName(originalName)
            val mimeType = FileUtils.getMimeType(requireContext(), uri) ?: "image/jpeg"

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
        val currentName = targetFileName ?: FileUtils.buildCompressedFileName(FileUtils.getFileName(requireContext(), uri))
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

    private fun loadImagePreview(uri: Uri, target: android.widget.ImageView) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            target.setImageBitmap(bmp)
        } catch (_: Exception) {
            target.setImageResource(R.drawable.ic_image)
        }
    }

    private fun shareFile(uri: Uri) {
        val shareableUri = FileUtils.getShareableUri(requireContext(), uri)
        val mime = requireContext().contentResolver.getType(shareableUri) ?: "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun openFile(uri: Uri) {
        try {
            val shareableUri = FileUtils.getShareableUri(requireContext(), uri)
            val mime = requireContext().contentResolver.getType(shareableUri) ?: "image/*"
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareableUri, mime)
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
