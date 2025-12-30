package com.picfinder.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
            showClearDatabaseConfirmationDialog()
        }

        binding.manualScanButton.setOnClickListener {
            viewModel.performManualScan()
        }
    }

    private fun showClearDatabaseConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Clear Database")
            .setMessage("Are you sure you want to clear the entire database? This will remove all scanned images and folders. This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearDatabase()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            viewModel.scanDurationAndImageCount.collect { scanInfo ->
                if (scanInfo != null && scanInfo.first != -1) {
                    binding.scanDurationAndImageCountText.visibility = View.VISIBLE
                    binding.scanDurationAndImageCountText.text = getString(
                        R.string.scan_duration_and_image_count,
                        scanInfo.first, scanInfo.second, scanInfo.third
                    )
                } else {
                    binding.scanDurationAndImageCountText.visibility = View.GONE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}