package com.picfinder.app.ui.folders

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.picfinder.app.databinding.FragmentFoldersBinding
import com.picfinder.app.utils.PermissionUtils
import kotlinx.coroutines.launch

class FoldersFragment : Fragment() {
    
    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: FoldersViewModel by viewModels()
    private lateinit var folderAdapter: FolderAdapter
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openFolderPicker()
        } else {
            Toast.makeText(
                requireContext(),
                "Storage permission is required to access folders",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFolder(uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onRescanClick = { folder ->
                viewModel.scanFolder(folder)
            },
            onRemoveClick = { folder ->
                viewModel.removeFolder(folder)
            }
        )
        
        binding.foldersRecycler.apply {
            adapter = folderAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupClickListeners() {
        binding.addFolderButton.setOnClickListener {
            if (PermissionUtils.hasStoragePermission(requireContext())) {
                openFolderPicker()
            } else {
                requestStoragePermission()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.folders.collect { folders ->
                folderAdapter.submitList(folders)
                updateEmptyState(folders.isEmpty())
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is FoldersViewModel.UiEvent.ShowMessage -> {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    }
                    is FoldersViewModel.UiEvent.ShowError -> {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanProgress.collect { progress ->
                when (progress) {
                    is FoldersViewModel.ScanProgress.Idle -> {
                        // Hide any progress indicators
                    }
                    is FoldersViewModel.ScanProgress.Scanning -> {
                        Toast.makeText(
                            requireContext(),
                            "Scanning ${progress.folderName}...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is FoldersViewModel.ScanProgress.Complete -> {
                        Toast.makeText(requireContext(), progress.message, Toast.LENGTH_SHORT).show()
                    }
                    is FoldersViewModel.ScanProgress.Error -> {
                        Toast.makeText(requireContext(), progress.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.foldersRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun requestStoragePermission() {
        val permissions = PermissionUtils.getRequiredPermissions()
        permissionLauncher.launch(permissions)
    }
    
    private fun openFolderPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }
            folderPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error opening folder picker: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun handleSelectedFolder(uri: Uri) {
        try {
            // Take persistable permission
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Convert URI to file path (this is a simplified approach)
            val folderPath = getFolderPathFromUri(uri)
            if (folderPath != null) {
                viewModel.addFolder(folderPath)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Could not access selected folder",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error accessing folder: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun getFolderPathFromUri(uri: Uri): String? {
        return try {
            // This is a simplified approach - in a real app you'd want to handle
            // document tree URIs more robustly
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("primary:")) {
                val path = docId.substring("primary:".length)
                "/storage/emulated/0/$path"
            } else {
                // For external storage or other providers, you'd need more complex handling
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}