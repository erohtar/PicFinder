package com.picfinder.app.ui.search

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.picfinder.app.R
import com.picfinder.app.data.database.ImageEntity
import com.picfinder.app.databinding.ItemSearchResultBinding
import java.io.File
import java.net.URLDecoder

class SearchResultAdapter(
    private val onItemClick: (ImageEntity) -> Unit
) : ListAdapter<ImageEntity, SearchResultAdapter.SearchResultViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(image: ImageEntity) {
            binding.apply {
                fileName.text = image.fileName
                extractedText.text = if (image.extractedText.isNotBlank()) {
                    image.extractedText
                } else {
                    root.context.getString(R.string.no_text_found)
                }
                
                // Decode and display folder path properly
                filePath.text = try {
                    if (image.folderPath.startsWith("content://")) {
                        URLDecoder.decode(image.folderPath, "UTF-8")
                            .substringAfter("primary:")
                            .replace("/", " > ")
                            .ifEmpty { "Selected Folder" }
                    } else {
                        image.folderPath
                    }
                } catch (e: Exception) {
                    image.folderPath
                }

                // Load thumbnail - handle both file paths and URIs
                val imageSource = if (image.filePath.startsWith("content://")) {
                    Uri.parse(image.filePath)
                } else {
                    File(image.filePath)
                }
                
                Glide.with(root.context)
                    .load(imageSource)
                    .placeholder(R.drawable.ic_folder)
                    .error(R.drawable.ic_folder)
                    .centerCrop()
                    .into(imageThumbnail)

                root.setOnClickListener {
                    onItemClick(image)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ImageEntity>() {
        override fun areItemsTheSame(oldItem: ImageEntity, newItem: ImageEntity): Boolean {
            return oldItem.filePath == newItem.filePath
        }

        override fun areContentsTheSame(oldItem: ImageEntity, newItem: ImageEntity): Boolean {
            return oldItem == newItem
        }
    }
}