package com.cameron.motionpy

import android.graphics.drawable.ColorDrawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.rv_item_grid.view.*
import kotlinx.android.synthetic.main.rv_item_list.view.*

class ViewAdapter : RecyclerView.Adapter<ViewAdapter.ViewHolder>() {

    var onEntryClickListener: (String) -> Unit = { }
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.itemView) {
            val entry = entries[position]
            val imageView = if (useGrid) item_image_grid else item_image_list
            val placeholder = ColorDrawable(ContextCompat.getColor(context, R.color.colorPrimaryDark))

            tag = entry.id
            item_time?.text = resources.getString(R.string.capture_time, entry.time)

            Picasso.get().load(entry.url)
                    .placeholder(placeholder)
                    .into(imageView)
        }
    }

    fun swapLayout(useGrid: Boolean) {
        this.useGrid = useGrid
        notifyDataSetChanged()
    }

    fun addItem(entry: Entry, position: Int = 0) {
        entries.add(position, entry)
        notifyItemInserted(position)
    }

    fun getItem(position: Int): Entry = entries[position]

    fun removeAtPosition(position: Int) {
        entries.removeAt(position)
        notifyItemRemoved(position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            onEntryClickListener(entries[adapterPosition].id!!)
        }
    }
}