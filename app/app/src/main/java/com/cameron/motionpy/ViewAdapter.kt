package com.cameron.motionpy

import android.annotation.SuppressLint
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.rv_item.view.*

class ViewAdapter : RecyclerView.Adapter<ViewAdapter.ViewHolder>() {

    private val entries = mutableListOf<Entry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.rv_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = entries.size


    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        with(holder.itemView) {
            tag = entry.id
            item_id.text = "ID: ${entry.id}"
            item_time.text = "Time: ${entry.time}"
            Picasso.get().load(entry.url).into(item_image)
        }
    }

    fun addItem(entry: Entry) {
        entries.add(entry)
        notifyItemInserted(entries.size)
    }

    fun removeAtPosition(position: Int) {
        entries.removeAt(position)
        notifyItemRemoved(position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}