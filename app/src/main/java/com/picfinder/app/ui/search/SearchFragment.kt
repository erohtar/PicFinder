package com.picfinder.app.ui.search

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.picfinder.app.R
import com.picfinder.app.data.database.ImageEntity
import com.picfinder.app.databinding.FragmentSearchBinding
import kotlinx.coroutines.launch
import java.io.File

class SearchFragment : Fragment() {
    
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchResultAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchInput()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        searchAdapter = SearchResultAdapter { image ->
            openImage(image)
        }
        
        binding.searchResultsRecycler.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupSearchInput() {
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { results ->
                searchAdapter.submitList(results)
                updateUIState(results)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun updateUIState(results: List<ImageEntity>) {
        val query = viewModel.searchQuery.value
        when {
            query.isBlank() -> {
                binding.searchResultsRecycler.visibility = View.GONE
                binding.noResultsLayout.visibility = View.GONE
            }
            results.isEmpty() -> {
                binding.searchResultsRecycler.visibility = View.GONE
                binding.noResultsLayout.visibility = View.VISIBLE
            }
            else -> {
                binding.searchResultsRecycler.visibility = View.VISIBLE
                binding.noResultsLayout.visibility = View.GONE
            }
        }
    }
    
    private fun openImage(image: ImageEntity) {
        try {
            val file = File(image.filePath)
            if (file.exists()) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(file), "image/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No app found to open images",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Image file not found",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error opening image: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}