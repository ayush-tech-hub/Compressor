package com.compressx.app.ui.image

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
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
                if (binding.radioCustom.isChecked) {
                    viewModel.setQuality(q)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Default selection
        binding.radioMedium.isChecked = true
        binding.seekBarQuality.progress = 60
        binding.textQualityValue.text = getString(R.string.quality_value, 60)
    }

    private fun setupObservers() {
        viewModel.selectedUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                loadImagePreview(uri)
                binding.imagePreview.isVisible = true
                binding.textSelectHint.isVisible = false
                binding.buttonCompress.isEnabled = true
                binding.cardFileInfo.isVisible = true
                binding.groupResult.isVisible = false
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
        binding.buttonSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.buttonCompress.setOnClickListener {
            val uri = viewModel.selectedUri.value ?: return@setOnClickListener
            val fileName = FileUtils.buildCompressedFileName(
                FileUtils.getFileName(requireContext(), uri)
            )
            saveFileLauncher.launch(fileName)
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

    private fun loadImagePreview(uri: Uri) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            binding.imagePreview.setImageBitmap(bmp)
        } catch (_: Exception) {
            binding.imagePreview.setImageResource(R.drawable.ic_image)
        }
    }

    private fun shareFile(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    private fun openFile(uri: Uri) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(openIntent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
