package pw.phylame.imabw.activity

import android.content.Context
import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.amulyakhare.textdrawable.TextDrawable
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tubb.smrv.SwipeHorizontalMenuLayout
import de.hdodenhof.circleimageview.CircleImageView
import jem.Book
import jem.Chapter
import jem.kotlin.cover
import jem.kotlin.intro
import jem.kotlin.title
import pw.phylame.imabw.Jem
import pw.phylame.imabw.R
import pw.phylame.support.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.lang.ref.WeakReference

class ChapterItem(val chapter: Chapter,
                  override var parent: ChapterItem? = null) : Hierarchical() {
    override fun toString(): String = chapter.title

    override val isGroup: Boolean get() = chapter.isSection
}

private typealias BaseHolder = RecyclerView.ViewHolder

class ChapterHolder(view: View) : BaseHolder(view) {
    val name: TextView = view.viewFor(R.id.name)
    val meta: TextView = view.viewFor(R.id.meta)
    val cover: CircleImageView = view.viewFor(R.id.cover)
    val option: ImageView = view.viewFor(R.id.option)
    val actionAdd: View = view.viewFor(R.id.itemAdd)
    val actionRename: View = view.viewFor(R.id.itemRename)
    val actionDelete: View = view.viewFor(R.id.itemDelete)
}

class PaddingHolder(view: View) : BaseHolder(view)

class ChapterAdapter(val context: Context, helper: ContentsHelper) : RecyclerView.Adapter<BaseHolder>() {
    companion object {
        const val EVENT_STATE = 1
        const val EVENT_COLOR = 2

        const val STATE_NORMAL = 0
        const val STATE_SELECTION = 1

        const val TYPE_NORMAL = 0
        const val TYPE_PADDING = 1
    }

    private val contentsHelper = WeakReference(helper)
    private val layoutInflater = LayoutInflater.from(context)
    private val coverSize = context.resources.getDimensionPixelSize(R.dimen.chapter_cover_size)

    var state: Int = STATE_NORMAL
        set(value) {
            if (value != field) {
                field = value
                if (value == STATE_NORMAL) {
                    notifyItemRemoved(items.size)
                } else {
                    notifyItemInserted(items.size)
                }
                notifyItemsChanged(EVENT_STATE)
            }
        }

    var color: Int = Color.BLACK
        set(value) {
            field = value
            notifyItemsChanged(EVENT_COLOR)
        }

    var items: List<ChapterItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = items.size + if (state == STATE_SELECTION) 1 else 0

    override fun getItemViewType(position: Int) = if (position == items.size) TYPE_PADDING else TYPE_NORMAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == TYPE_NORMAL) {
        val holder = ChapterHolder(layoutInflater.inflate(R.layout.chapter_item, parent, false))
        setupListeners(holder)
        holder
    } else {
        PaddingHolder(layoutInflater.inflate(R.layout.padding_item, parent, false))
    }

    private fun setupListeners(holder: ChapterHolder) {
        holder.cover.setOnClickListener {
            val position = it.get<ChapterHolder>(R.id.tag_extra).adapterPosition
            contentsHelper.get()!!.onItemCoverClicked(it[R.id.tag_data], position, it)
        }
        holder.itemView.setOnClickListener {
            val position = it.get<ChapterHolder>(R.id.tag_extra).adapterPosition
            contentsHelper.get()!!.onItemClicked(it[R.id.tag_data], position, it)
        }
        holder.actionAdd.setOnClickListener {
            val ch = it.get<ChapterHolder>(R.id.tag_extra)
            (ch.itemView as SwipeHorizontalMenuLayout).smoothCloseEndMenu()
            contentsHelper.get()!!.newChapter(it[R.id.tag_data], ch.adapterPosition)
        }
        holder.actionRename.setOnClickListener {
            val ch = it.get<ChapterHolder>(R.id.tag_extra)
            (ch.itemView as SwipeHorizontalMenuLayout).smoothCloseEndMenu()
            contentsHelper.get()!!.renameChapter(it[R.id.tag_data], ch.adapterPosition)
        }
        holder.actionDelete.setOnClickListener {
            val ch = it.get<ChapterHolder>(R.id.tag_extra)
            (ch.itemView as SwipeHorizontalMenuLayout).smoothCloseEndMenu()
            contentsHelper.get()!!.removeItem(ch.adapterPosition)
        }
    }

    // ignored by the following method
    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int, payloads: MutableList<Any>) {
        if (holder !is ChapterHolder) {
            return
        }
        val item = items[position]

        try {
            (holder.itemView as SwipeHorizontalMenuLayout).smoothCloseMenu()
        } catch (e: NullPointerException) {
        }

        holder.itemView[R.id.tag_data] = item
        holder.itemView[R.id.tag_extra] = holder
        holder.cover[R.id.tag_data] = item
        holder.cover[R.id.tag_extra] = holder
        holder.actionAdd[R.id.tag_data] = item
        holder.actionAdd[R.id.tag_extra] = holder
        holder.actionRename[R.id.tag_data] = item
        holder.actionRename[R.id.tag_extra] = holder
        holder.actionDelete[R.id.tag_data] = item
        holder.actionDelete[R.id.tag_extra] = holder

        if (payloads.isEmpty()) {
            bindItem(item, holder)
        } else {
            when (payloads.first()) {
                EVENT_STATE -> {
                    setOptionImage(holder.option, item)
                    holder.option.tintImage(color)
                }
                EVENT_COLOR -> {
                    setColors(item, holder)
                }
            }
        }
    }

    private fun bindItem(item: ChapterItem, holder: ChapterHolder) {
        holder.name.text = item.chapter.title
        setOptionImage(holder.option, item)
        setCoverImage(holder.cover, item)
        setMetaText(holder.meta, item)
        setColors(item, holder)
    }

    private fun setColors(item: ChapterItem, holder: ChapterHolder) {
        val coverView = holder.cover
        val drawable = coverView.drawable
        if (drawable is TextDrawable) {
            setFallbackCover(coverView, item)
        }
        holder.option.tintImage(color)
    }

    private fun setCoverImage(view: ImageView, item: ChapterItem) {
        val stream = item.chapter.cover?.openStream()
        if (stream != null) {
            Images.glide(context, stream)
                    .crossFade()
                    .override(coverSize, coverSize)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(Images.consume { bmp ->
                        if (bmp == null) { // load failed
                            setFallbackCover(view, item)
                        }
                    })
                    .into(view)
        } else {
            setFallbackCover(view, item)
        }
    }

    private fun setFallbackCover(view: ImageView, item: ChapterItem) {
        val drawable = TextDrawable.builder()
                .beginConfig()
                .width(coverSize)
                .height(coverSize)
                .textColor(Jem.coverTextColor(color))
                .endConfig()
                .buildRound(Jem.hintTextOf(item.chapter.title), color)
        view.setImageDrawable(drawable)
    }

    private fun setMetaText(view: TextView, item: ChapterItem) {
        val intro = item.chapter.intro?.text
        if (intro.isNullOrEmpty()) {
            view.isVisible = false
        } else {
            view.isVisible = true
            view.text = intro
        }
    }

    private fun setOptionImage(view: ImageView, item: ChapterItem) {
        if (state == STATE_SELECTION) {
            view.isVisible = true
            view.setImageResource(if (item.isSelected) R.mipmap.ic_checked_checkbox else R.mipmap.ic_unchecked_checkbox)
        } else if (item.chapter.isSection) {
            view.isVisible = true
            view.setImageResource(R.mipmap.ic_forward)
        } else {
            view.isVisible = false
        }
    }
}

open class ContentsHelper(val recycler: RecyclerView) : HierarchicalHelper<ChapterItem, LinearPosition>(true) {
    val adapter: ChapterAdapter
    val clipboard = ArrayList<ChapterItem>()
    lateinit var children: MutableList<ChapterItem>

    init {
        adapter = ChapterAdapter(recycler.context, this)
        recycler.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recycler: RecyclerView, holder: RecyclerView.ViewHolder): Int {
                return makeMovementFlags(if (!isInSelection && size > 1) ItemTouchHelper.DOWN or ItemTouchHelper.UP else 0, 0)
            }

            override fun onMove(recycler: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                swapItems(source.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {}
        }).attachToRecyclerView(recycler)
    }

    override var position: LinearPosition?
        get() = recycler.position
        set(value) {
            recycler.position = value
        }

    val isPasteable
        get() = clipboard.isNotEmpty()

    fun cut() {
        Worker.schedule {
            clipboard.clear()
            clipboard += selections
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    removeSelections(cleanup = false)
                }
    }

    fun paste() {
        if (isPasteable) {
            val start = children.size
            val count = clipboard.size
            Observable.create<ChapterItem> { sub ->
                for (item in clipboard) {
                    sub.onNext(item)
                }
                clipboard.clear()
                sub.onCompleted()
            }.subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnCompleted {
                        adapter.notifyItemRangeInserted(start, count)
                        onSizeChanged()
                    }
                    .subscribe {
                        appendItem(it, false)
                    }
        }
    }

    val size: Int get() = children.size

    fun swapItems(from: Int, to: Int, refreshUI: Boolean = true) {
        children.swap(from, to)
        current.chapter.swap(from, to)
        if (refreshUI) {
            adapter.notifyItemMoved(from, to)
        }
    }

    fun appendItem(item: ChapterItem, refreshUI: Boolean = true) {
        children.add(item)
        item.parent = current
        current.chapter.append(item.chapter)
        if (refreshUI) {
            adapter.notifyItemInserted(children.size - 1)
            onSizeChanged()
        }
    }

    // position: position of parent
    fun appendChapter(chapter: Chapter, parent: Chapter, position: Int) {
        parent.append(chapter)
        adapter.notifyItemChanged(position)
    }

    fun removeItem(position: Int, cleanup: Boolean = false, refreshUI: Boolean = true) {
        val chapter = current.chapter.removeAt(position)
        if (cleanup) {
            chapter.cleanup()
        }
        val item = children.removeAt(position)
        item.parent = null
        item.isSelected = false
        if (isInSelection) {
            indices.remove(position)
            selections.remove(item)
        }
        if (refreshUI) {
            adapter.notifyItemRemoved(position)
            if (isInSelection) {
                onSelectionChanged(-2)
            }
            onSizeChanged()
        }
    }

    open fun activateBook(book: Book) {
        Worker.schedule {
            for (item in clipboard) {
                item.chapter.cleanup()
            }
            clipboard.clear()
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    finishSelection()
                    activateItem(ChapterItem(book))
                }
    }

    open fun newChapter(parent: ChapterItem, position: Int) {}

    open fun renameChapter(parent: ChapterItem, position: Int) {}

    fun setItemColor(color: Int) {
        adapter.color = color
    }

    val isInSelection: Boolean
        get() = adapter.state == ChapterAdapter.STATE_SELECTION

    open fun beginSelection(): Boolean = if (!isInSelection && size > 0) {
        resetSelections()
        onSelectionChanged(-1)
        adapter.state = ChapterAdapter.STATE_SELECTION
        true
    } else false

    open fun finishSelection() {
        adapter.state = ChapterAdapter.STATE_NORMAL
    }

    fun removeSelections(cleanup: Boolean, refreshUI: Boolean = true) {
        removeSelections { position ->
            removeItem(position, cleanup, refreshUI)
        }
    }

    open fun onSizeChanged() {}

    // position: -1, all changed, -2: item removed
    override fun onSelectionChanged(position: Int) {
        if (position == -1) {
            adapter.notifyItemsChanged(ChapterAdapter.EVENT_STATE)
        } else {
            adapter.notifyItemChanged(position, ChapterAdapter.EVENT_STATE)
        }
    }

    override fun loadChildren(item: ChapterItem): List<ChapterItem> {
        val items = ArrayList<ChapterItem>()
        for (chapter in item.chapter) {
            items += ChapterItem(chapter, item)
        }
        this.children = items
        return items
    }

    override fun onPostLoad(items: List<ChapterItem>) {
        adapter.items = items
        onSizeChanged()
    }

    open fun onItemClicked(item: ChapterItem, position: Int, view: View): Boolean {
        if (isInSelection) {
            toggleSelection(item, position)
        } else if (item.chapter.isSection) {
            enterChild(item)
        } else {
            return false
        }
        return true
    }

    open fun onItemCoverClicked(item: ChapterItem, position: Int, view: View) {}
}