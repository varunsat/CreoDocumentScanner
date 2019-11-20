package com.creoit.docscanner.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class SimpleAdapter<M, VH : RecyclerView.ViewHolder>(
    var items: ArrayList<M>,
    private val getLayoutId: (Int) -> Int,
    private val vhBinder: (View, Int) -> VH,
    private val binder: (VH, M) -> Unit
) : RecyclerView.Adapter<VH>() {

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = vhBinder(
        LayoutInflater.from(parent.context).inflate(getLayoutId(viewType), parent, false),
        viewType
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        binder(holder,items[position])
    }

    fun update(items: ArrayList<M>, notify: Boolean = true) {
        this.items = items
        if(notify)
            notifyDataSetChanged()
    }

    fun update(transform: (M)->Unit) {
        items.forEach {
            transform(it)
        }
        notifyDataSetChanged()
    }

    fun updateItem(pos: Int, transform: (M)->Unit) {
        transform(items[pos])
        notifyDataSetChanged()
    }

    fun addItem(item: M, pos: Int = -1, notify:Boolean=false) {
        if (pos > -1) items.add(pos, item) else items.add(item)
        if(notify) notifyItemInserted(if (pos > -1) pos else items.size - 1)
    }

    fun removeItem(pos: Int) {
        items.removeAt(pos)
        notifyItemRemoved(pos)
    }

    fun removeAll() {
        items.clear()
        notifyDataSetChanged()
    }


    fun getItemAt(position: Int): M {
        return items[position]
    }

    fun updateItem(pos: Int, item: M, notify: Boolean = true) {
        items[pos] = item
        if (notify) notifyItemChanged(pos)
    }

    fun addItems(items: ArrayList<M>, notify: Boolean = true, animate: Boolean = true) {
        val start = itemCount
        this.items.addAll(items)
        if (notify)
            if (animate) notifyItemRangeInserted(start, items.size)
            else notifyDataSetChanged()
    }
}