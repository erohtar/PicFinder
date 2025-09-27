package com.picfinder.app.ui.folders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.picfinder.app.R
import com.picfinder.app.data.database.FolderEntity
import com.picfinder.app.databinding.ItemFolderBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FolderAdapter(
    private val onRescanClick: (FolderEntity) -> Unit,
    private val onRemoveClick: (FolderEntity) -> Unit
) : ListAdapter<FolderEntity, FolderAdapter.FolderViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: FolderEntity) {
            binding.apply {
                folderName.text = folder.displayName
                folderPath.text = folder.folderPath
                imageCount.text = root.context.getString(
                    R.string.images_count_format,
                    folder.imageCount
                )

                rescanButton.setOnClickListener {
                    onRescanClick(folder)
                }

                removeButton.setOnClickListener {
                    onRemoveClick(folder)
                }

                removeIcon.setOnClickListener {
                    onRemoveClick(folder)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FolderEntity>() {
        override fun areItemsTheSame(oldItem: FolderEntity, newItem: FolderEntity): Boolean {
            return oldItem.folderPath == newItem.folderPath
        }

        override fun areContentsTheSame(oldItem: FolderEntity, newItem: FolderEntity): Boolean {
            return oldItem == newItem
        }
    }
}