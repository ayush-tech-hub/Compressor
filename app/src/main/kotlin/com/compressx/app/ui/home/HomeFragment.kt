package com.compressx.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.compressx.app.R
import com.compressx.app.databinding.FragmentHomeBinding
import com.compressx.app.util.FileUtils

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardCompressImages.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_imageCompressFragment)
        }

        binding.cardCompressPdfs.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_pdfCompressFragment)
        }

        viewModel.totalCount.observe(viewLifecycleOwner) { count ->
            binding.textTotalCompressed.text = getString(R.string.stat_total_compressed, count ?: 0)
        }

        viewModel.totalSpaceSaved.observe(viewLifecycleOwner) { saved ->
            val bytes = saved ?: 0L
            binding.textSpaceSaved.text = getString(R.string.stat_space_saved, FileUtils.formatFileSize(bytes))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
