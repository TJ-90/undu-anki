package com.tj90.unduanki.island

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.tj90.unduanki.game.ItemCatalog

/** Embeddable island controls; navigation is provided by the host activity. */
class IslandScreen(context: Context, private val repository: IslandRepository) : LinearLayout(context) {
    private var selected: String? = null
    private var message = "Select an owned decoration, then an empty slot."

    init { orientation = VERTICAL; refresh() }

    private fun selectSlot(slot: Int, state: IslandRepository.Snapshot) {
        val item = state.placements[slot]
        val label = item?.let { ItemCatalog.find(it)?.label ?: it }
        if (item != null) { selected = item; message = "$label selected. Choose an empty slot or return it to inventory." }
        else if (selected != null && repository.place(selected!!, slot)) { selected = null; message = "Decoration placed." }
        else message = "Select an owned decoration first."
        refresh()
    }

    private fun refresh() {
        removeAllViews()
        val state = repository.snapshot()
        addView(TextView(context).apply { text = "Island · ${state.stars} stars\n" + if (state.rescued) "Your penguin is home!" else "Rescue the lost penguin!" })
        addView(TextView(context).apply { text = message })
        addView(IslandView(context, state) { slot -> selectSlot(slot, state) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        for (slot in 1..6) {
            val item = state.placements[slot]
            val label = item?.let { ItemCatalog.find(it)?.label ?: it } ?: "Empty"
            addView(Button(context).apply {
                text = "Slot $slot: $label"
                setOnClickListener { selectSlot(slot, state) }
            })
        }
        addView(Button(context).apply {
            text = "Return selected decoration to inventory"
            isEnabled = selected?.let { state.placements.containsValue(it) } == true
            setOnClickListener {
                state.placements.entries.find { it.value == selected }?.let { repository.unplace(it.key) }
                selected = null; message = "Decoration returned to inventory."; refresh()
            }
        })
        addView(TextView(context).apply { text = "Inventory and shop" })
        for (item in ItemCatalog.items) {
            val owned = item.id in state.owned
            addView(Button(context).apply {
                text = if (owned) "Select ${item.label}" else "Buy ${item.label} (${item.price} stars)"
                setOnClickListener {
                    if (owned) { selected = item.id; message = "${item.label} selected." }
                    else if (repository.buy(item.id)) { selected = item.id; message = "${item.label} purchased." }
                    else message = "Not enough stars."
                    refresh()
                }
            })
        }
    }
}
