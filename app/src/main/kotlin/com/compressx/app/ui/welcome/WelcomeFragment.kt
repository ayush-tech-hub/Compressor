package com.compressx.app.ui.welcome

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.compressx.app.R
import com.compressx.app.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonContinue.setOnClickListener {
            if (binding.checkboxDontShowAgain.isChecked) {
                requireContext().getSharedPreferences("prefs_compressx", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("welcome_shown", true)
                    .apply()
            }
            findNavController().navigate(
                R.id.action_welcomeFragment_to_homeFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.welcomeFragment, true)
                    .build()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
