package pw.phylame.imabw.activity

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.ColorUtils
import android.support.v7.app.AlertDialog
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_settings.*
import pw.phylame.imabw.ImabwApp
import pw.phylame.imabw.Jem
import pw.phylame.imabw.R
import pw.phylame.support.Titled
import pw.phylame.support.setTint
import pw.phylame.support.tintImage
import pw.phylame.support.viewFor

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar(toolbar, showHomeAsUp = true)
        setToolbarColor(primaryColor)
        if (isImageBackground) {
            recycler.setBackgroundResource(R.drawable.light_mask)
        }
        init()
    }

    private fun init() {
        val uPrefs = ImabwApp.sharedApp.uiSettings
        val items = listOf(
                getString(R.string.pref_ui),
                SettingsItem(uPrefs, R.string.pref_primary_color, "colorPrimary", "color", primaryColor),
                SettingsItem(uPrefs, R.string.pref_background_image, "backgroundImage", "file|image/*"),
                SettingsItem(uPrefs, R.string.pref_md_status, "mdStatusColor", "bool", isMdStatusMode),
                SettingsItem(uPrefs, R.string.pref_cover_palette, "coverPalette", "bool", Jem.isCoverPalette),
                SettingsItem(uPrefs, R.string.pref_palette_mode, "paletteMode", "choice", Jem.PaletteMode.values() to Jem.coverPaletteMode)
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = SettingsAdapter(items, primaryColor)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }
}

private class SettingsItem(val prefs: SharedPreferences,
                           val name: Int,
                           val key: String,
                           val type: String,
                           val extra: Any? = null)

private class SettingsAdapter(val items: List<Any>, val tintColor: Int) : RecyclerView.Adapter<ItemViewHolder>() {
    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TEXT = 1
        const val TYPE_SWITCH = 2
        const val TYPE_CHECKBOX = 3
        const val TYPE_CUSTOM = 4
        const val TYPE_COLOR = 5
        const val TYPE_FILE = 6
        const val TYPE_CHOICE = 7
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is SettingsItem -> when (item.type) {
                "str" -> TYPE_TEXT
                "int" -> TYPE_TEXT
                "color" -> TYPE_COLOR
                "bool" -> TYPE_SWITCH
                "file" -> TYPE_FILE
                "choice" -> TYPE_CHOICE
                "custom" -> TYPE_CUSTOM
                else -> if (item.type.startsWith("file")) {
                    TYPE_FILE
                } else {
                    throw IllegalArgumentException("Invalid type settings item: ${item.type}")
                }
            }
            is CharSequence -> TYPE_HEADER
            else -> throw IllegalArgumentException("Invalid settings item: $item")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT -> TextViewHolder(inflater.inflate(R.layout.pref_item_text, parent, false), tintColor)
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.pref_item_header, parent, false), tintColor)
            TYPE_SWITCH -> SwitchViewHolder(inflater.inflate(R.layout.pref_item_switch, parent, false), tintColor)
            TYPE_CUSTOM -> CustomViewHolder(inflater.inflate(R.layout.pref_item_custom, parent, false), tintColor)
            TYPE_CHECKBOX -> CheckboxViewHolder(inflater.inflate(R.layout.pref_item_checkbox, parent, false), tintColor)
            TYPE_COLOR -> ColorViewHolder(inflater.inflate(R.layout.pref_item_custom, parent, false), tintColor)
            TYPE_FILE -> FileViewHolder(inflater.inflate(R.layout.pref_item_custom, parent, false), tintColor)
            TYPE_CHOICE -> ChoiceViewHolder(inflater.inflate(R.layout.pref_item_custom, parent, false), tintColor)
            else -> throw IllegalArgumentException("Invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }
}

private open class ItemViewHolder(view: View, val tintColor: Int) : RecyclerView.ViewHolder(view) {
    init {
        val alpha = view.context.resources.getInteger(R.integer.list_item_color_alpha)
        if (this !is HeaderViewHolder) {
            val color = ContextCompat.getColor(view.context, R.color.listItemColor)
            view.setBackgroundColor(ColorUtils.setAlphaComponent(color, alpha))
        } else {
            val color = ContextCompat.getColor(view.context, R.color.listHeaderColor)
            view.setBackgroundColor(ColorUtils.setAlphaComponent(color, alpha))
        }
    }

    open fun bind(item: Any) {}
}

private class HeaderViewHolder(view: View, tintColor: Int) : ItemViewHolder(view, tintColor) {
    init {
        (view as TextView).setTextColor(tintColor)
    }

    override fun bind(item: Any) {
        (itemView as TextView).text = item.toString()
    }
}

private class TextViewHolder(view: View, tintColor: Int) : ItemViewHolder(view, tintColor) {
    val label: TextView = view.viewFor(R.id.label)
    val value: TextView = view.viewFor(R.id.value)

    override fun bind(item: Any) {
        val si = item as SettingsItem
        label.setText(si.name)
        value.text = si.extra?.toString() ?: si.prefs.getString(si.key, "")
    }
}

private class SwitchViewHolder(view: View, tintColor: Int) : ItemViewHolder(view, tintColor) {
    val label: TextView = view.viewFor(R.id.label)
    val switch: SwitchCompat = view.viewFor(R.id.switcher)
    lateinit var item: SettingsItem

    init {
        switch.setTint(tintColor)
        view.setOnClickListener {
            switch.isChecked = !switch.isChecked
        }
        switch.setOnCheckedChangeListener { _, isChecked ->
            item.prefs.edit()
                    .putBoolean(item.key, isChecked)
                    .apply()
        }
    }

    override fun bind(item: Any) {
        val si = item as SettingsItem
        this.item = si
        label.setText(si.name)
        switch.isChecked = (si.extra as? Boolean) ?: si.prefs.getBoolean(si.key, false)
    }
}

private class CheckboxViewHolder(view: View, tintColor: Int) : ItemViewHolder(view, tintColor) {
    val label: TextView = view.viewFor(R.id.label)
    val checkbox: CheckBox = view.viewFor(R.id.checkbox)
    lateinit var item: SettingsItem

    init {
        checkbox.setTint(tintColor)
        view.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
        }

        checkbox.setOnCheckedChangeListener { _, isChecked ->
            item.prefs.edit()
                    .putBoolean(item.key, isChecked)
                    .apply()
        }
    }

    override fun bind(item: Any) {
        val si = item as SettingsItem
        this.item = si
        label.setText(si.name)
        checkbox.isChecked = (si.extra as? Boolean) ?: si.prefs.getBoolean(si.key, false)
    }
}

private open class CustomViewHolder(view: View, tintColor: Int) : ItemViewHolder(view, tintColor), View.OnClickListener {
    val label: TextView = view.viewFor(R.id.label)
    val text: TextView = view.viewFor(R.id.text)
    val arrow: ImageView = view.viewFor(R.id.arrow)

    init {
        arrow.tintImage(tintColor)
        view.setOnClickListener(this)
    }

    override fun onClick(v: View) {
    }

    override fun bind(item: Any) {
        val si = item as SettingsItem
        label.setText(si.name)
        text.text = si.extra?.toString()
    }
}

private class ColorViewHolder(view: View, tintColor: Int) : CustomViewHolder(view, tintColor) {
    override fun bind(item: Any) {
        val si = item as SettingsItem
        label.setText(si.name)
        text.text = "QWERTY"
        text.setTextColor(si.extra as? Int? ?: si.prefs.getInt(si.key, Color.BLACK))
    }
}

private class FileViewHolder(view: View, tintColor: Int) : CustomViewHolder(view, tintColor) {
    override fun bind(item: Any) {
        val si = item as SettingsItem
        label.setText(si.name)
        text.text = si.prefs.getString(si.key, "")
    }
}

private class ChoiceViewHolder(view: View, tintColor: Int) : CustomViewHolder(view, tintColor) {
    private lateinit var item: SettingsItem
    private lateinit var values: Array<*>
    private var value: Any? = null

    private var titles: Array<String> = emptyArray()

    @Suppress("UNCHECKED_CAST")
    override fun bind(item: Any) {
        val si = item as SettingsItem
        this.item = si
        label.setText(si.name)
        val extra = si.extra
        if (extra is Array<*>) {
            values = extra
            value = si.prefs.getString(si.key, "")
        } else if (extra is Pair<*, *>) {
            if (extra.first is Array<*>) {
                values = extra.first as Array<*>
            } else {
                throw IllegalArgumentException("Extra for choice must be Array or Pair<Array, Any>")
            }
            value = extra.second ?: si.prefs.getString(si.key, "")
        } else {
            throw IllegalArgumentException("Extra for choice must be Array or Pair<Array, Any>")
        }
        text.text = getTitle(value, itemView.context)
        titles = values.map {
            getTitle(it, itemView.context) ?: ""
        }.toTypedArray()
    }

    fun getTitle(o: Any?, context: Context): String? = if (o is Titled) context.getString(o.titleId) else o?.toString()

    override fun onClick(v: View) {
        AlertDialog.Builder(v.context)
                .setTitle(item.name)
                .setItems(titles) { _, index ->
                    value = values[index].toString()
                    text.text = titles[index]
                    item.prefs.edit()
                            .putString(item.key, value.toString())
                            .apply()
                }
                .show()
    }
}
