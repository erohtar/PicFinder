package com.picfinder.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.picfinder.app.R
import com.picfinder.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupClickListeners() {
        binding.clearDatabaseButton.setOnClickListener {
            showClearDatabaseConfirmation()
        }
        
        binding.manualScanButton.setOnClickListener {
            viewModel.performManualScan()
        }
    }
    
    private fun observeViewModel() {
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lastScanDate.collect { timestamp ->
                binding.lastScanText.text = if (timestamp == 0L) {
                    getString(R.string.never)
                } else {
                    SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                        .format(Date(timestamp))
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.databaseStats.collect { stats ->
                binding.databaseStatsText.text = getString(
                    R.string.total_images_in_database,
                    stats.totalImages
                )
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isScanning.collect { isScanning ->
                binding.manualScanButton.isEnabled = !isScanning
                binding.manualScanButton.text = if (isScanning) {
                    "Scanning..."
                } else {
                    "Scan All Folders Now"
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is SettingsViewModel.UiEvent.ShowMessage -> {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    }
                    is SettingsViewModel.UiEvent.ShowError -> {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    }
                    is SettingsViewModel.UiEvent.DatabaseCleared -> {
                        // Database cleared successfully
                    }
                }
            }
        }
    }
    
    private fun showClearDatabaseConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear Database")
            .setMessage("This will remove all scanned image data. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearDatabase()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}