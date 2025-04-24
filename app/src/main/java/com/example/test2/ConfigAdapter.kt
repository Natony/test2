    package com.example.test2

    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.ImageButton
    import android.widget.PopupMenu
    import android.widget.TextView
    import androidx.recyclerview.widget.RecyclerView

    class ConfigAdapter(
        private val onItemClick: (ConfigItem) -> Unit,
        private val onEditClick: (ConfigItem) -> Unit,
        private val onDeleteClick: (ConfigItem) -> Unit
    ) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

        private val items = mutableListOf<ConfigItem>()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
            val btnMenu: ImageButton = itemView.findViewById(R.id.btnMenu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_config, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvDeviceName.text = item.name
            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.btnMenu.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    menuInflater.inflate(R.menu.config_item_menu, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit -> onEditClick(item)
                            R.id.action_delete -> onDeleteClick(item)
                        }
                        true
                    }
                }.show()
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateData(newItems: List<ConfigItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }