package com.cameron.motionpy

import android.annotation.SuppressLint
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.rv_item_list.view.*
import kotlinx.android.synthetic.main.rv_item_grid.view.*

class ViewAdapter : RecyclerView.Adapter<ViewAdapter.ViewHolder>() {

    private val entries = mutableListOf<Entry>()
    private var useGrid = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int =
            if (useGrid) R.layout.rv_item_grid
            else R.layout.rv_item_list

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        with(holder.itemView) {
            tag = entry.id
            if (useGrid) {
                Picasso.get().load(entry.url).into(item_image_grid)
            } else {
                item_id.text = "ID: ${entry.id}"
                item_time.text = "Time: ${entry.time}"
                Picasso.get().load(entry.url).into(item_image)
            }
        }
    }

    fun swapLayout(useGrid: Boolean) {
        this.useGrid = useGrid
        notifyDataSetChanged()
    }

    fun addItem(entry: Entry) {
        // Ensures the newest entries always appear at the top
        entries.add(0, entry)
        notifyItemInserted(0)
    }

    fun getItem(position: Int): Entry = entries[position]

    fun removeAtPosition(position: Int) {
        entries.removeAt(position)
        notifyItemRemoved(position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}