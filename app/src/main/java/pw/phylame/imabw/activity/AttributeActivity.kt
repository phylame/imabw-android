package pw.phylame.imabw.activity

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.support.v4.graphics.ColorUtils
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import jem.Attributes
import jem.Chapter
import jem.kotlin.cover
import jem.kotlin.title
import jem.util.Variants
import kotlinx.android.synthetic.main.activity_attribute.*
import pw.phylame.imabw.R
import pw.phylame.support.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

private typealias Item = Map.Entry<String, Any>

class AttributeActivity : BaseActivity() {
    companion object {
        private val TAG: String = AttributeActivity::class.java.simpleName
        private val ignoredKeys = arrayOf(Attributes.TITLE, Attributes.COVER)

        const val COLOR_KEY = 101
        const val CHAPTER_KEY = 100
    }

    lateinit var chapter: Chapter
    lateinit var adapter: ItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attribute)
        setupToolbar(toolbar, showHomeAsUp = true)
        toolbar.topMargin = statusHeight
        setStatusTransparent()
        appbar.addOnOffsetChangedListener { bar, offset ->
            if (offset == 0) {
                title = getString(R.string.attributes)
            } else if (Math.abs(offset) == bar.totalScrollRange) {
                title = chapter.title
            } else {

            }
        }

        adapter = ItemAdapter()
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        if (isImageBackground) {
            recycler.setBackgroundResource(R.drawable.light_mask)
        }

        val chapter: Chapter? = DataHub.take(CHAPTER_KEY)
        if (chapter == null) {
            Log.e(TAG, "no chapter found in Hub")
            finish()
        } else {
            this.chapter = chapter
            initChapter(chapter)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.attribute, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun initChapter(chapter: Chapter) {
        title = chapter.title

        Observable.create<Item> { sub ->
            chapter.attributes.entries()
                    .filter { it.key !in ignoredKeys }
                    .sortedBy { it.key }
                    .forEach(sub::onNext)
            sub.onCompleted()
        }.subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted {
                    placeholder.isVisible = adapter.itemCount == 0
                }
                .subscribe(adapter::append)

        val barColor: Int? = DataHub.take(COLOR_KEY)
        val stream = chapter.cover?.openStream()
        if (stream != null) {
            val mask = ColorDrawable(ColorUtils.setAlphaComponent(Color.BLACK, 0xAA))
            Images.load(stream)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { bmp ->
                        setupImageColors(barColor ?: primaryColor)
                        banner.setImageDrawable(LayerDrawable(arrayOf(BitmapDrawable(resources, bmp), mask)))
                    }
        } else {
            setupBannerColors(barColor ?: primaryColor)
        }
    }

    private fun setupImageColors(barColor: Int) {
        val actionColor = Color.WHITE
        fab.tintBackground(barColor)
        fab.tintImage(showyColorFor(barColor))
        toolbar.setTitleTextColor(actionColor)
        toolbar.tintItems(actionColor)
    }

    private fun setupBannerColors(barColor: Int) {
        setToolbarColor(barColor)
        fab.tintBackground(primaryColor)
        banner.setBackgroundColor(barColor)
        collapsing.setContentScrimColor(barColor)
    }
}

class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
    val text1: TextView = view.viewFor(R.id.name)
    val text2: TextView = view.viewFor(R.id.value)
}

class ItemAdapter : RecyclerView.Adapter<ItemHolder>() {
    var items = ArrayList<Item>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun append(item: Item) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.attribute_item, parent, false)
        return ItemHolder(view)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val (key, value) = items[position]
        holder.text1.text = Attributes.titleOf(key) ?: key.capitalize()
        holder.text2.text = Variants.printable(value) ?: value.toString()
    }

}
