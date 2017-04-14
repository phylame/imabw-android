package pw.phylame.support

import android.graphics.Color
import android.support.annotation.UiThread
import android.support.annotation.WorkerThread
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

abstract class Hierarchical {
    open val isGroup: Boolean = false

    abstract val parent: Hierarchical?

    open var isSelected: Boolean = false

    internal var children = emptyList<Hierarchical>()
}

abstract class HierarchicalHelper<T : Hierarchical, P>(var isAsyncMode: Boolean = false) {
    lateinit var root: T

    lateinit var current: T
        private set

    val positions = LinkedList<P>()

    // indices of selected items
    val indices = LinkedHashSet<Int>()

    // selected items
    val selections = LinkedHashSet<T>()

    @UiThread
    open fun onPreLoad() {
    }

    // UI or worker thread by isAsyncMode
    abstract fun loadChildren(item: T): List<T>

    @UiThread
    abstract fun onPostLoad(items: List<T>)

    open var position: P?
        get() = null
        set(value) {}

    @UiThread
    fun activateItem(item: T) {
        positions.clear()
        activateItem(item, null)
        if (item.parent == null) root = item
    }

    @UiThread
    fun enterChild(item: T) {
        positions.offer(position)
        activateItem(item, null)
    }

    @UiThread
    @Suppress("UNCHECKED_CAST")
    fun backParent(): Boolean {
        if (current === root) {
            return false
        }
        val parent = current.parent as? T ?: throw IllegalStateException("non-root item must have parent")
        activateItem(parent, positions.poll())
        return true
    }

    @UiThread
    fun refreshChildren(keepPosition: Boolean = true) {
        Worker.schedule {
            indices.clear()
            selections.clear()
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    reloadChildren(if (keepPosition) position else null)
                }
    }

    @WorkerThread
    fun resetSelections() {
        selections.forEach {
            it.isSelected = false
        }
        indices.clear()
        selections.clear()
    }

    @WorkerThread
    fun removeSelections(action: (Int) -> Unit) {
        indices.sorted().forEachIndexed { index, position ->
            action(position - index)
        }
        indices.clear()
        selections.clear()
    }

    @UiThread
    fun toggleSelection(item: T, position: Int, selected: Boolean? = null) {
        item.isSelected = selected ?: !item.isSelected
        if (item.isSelected) {
            indices += position
            selections += item
        } else {
            indices -= position
            selections -= item
        }
        onSelectionChanged(position)
    }

    @UiThread
    @Suppress("UNCHECKED_CAST")
    fun toggleSelections() {
        Worker.schedule {
            val selected = selections.size != current.children.size
            current.children.forEachIndexed { position, item ->
                item.isSelected = selected
                if (selected) {
                    indices += position
                    selections += item as T
                }
            }
            if (!selected) {
                indices.clear()
                selections.clear()
            }
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    onSelectionChanged(-1)
                }
    }

    @UiThread
    open fun onSelectionChanged(position: Int) {
    }

    @WorkerThread
    @Suppress("UNCHECKED_CAST")
    fun pathToRoot(): List<T> {
        val paths = ArrayList<T>(12)
        var parent: Hierarchical? = current
        while (parent != null) {
            paths += parent as T
            parent = parent.parent
        }
        return paths.reversed()
    }

    @UiThread
    fun activatePathItem(item: T, begin: Int) = if (item !== current) {
        var position: P? = null
        for (i in begin..positions.size - 1) {
            position = positions.removeLast()
        }
        activateItem(item, position)
        true
    } else {
        false
    }

    private fun activateItem(item: T, position: P?) {
        current = item
        reloadChildren(position)
    }

    private fun reloadChildren(position: P?) {
        if (isAsyncMode) {
            Worker.schedule {
                loadChildren(current)
            }.observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe {
                        onPreLoad()
                    }
                    .subscribe { items ->
                        onChildrenLoaded(items, position)
                    }
        } else {
            onPreLoad()
            onChildrenLoaded(loadChildren(current), position)
        }
    }

    private fun onChildrenLoaded(items: List<T>, position: P?) {
        onPostLoad(items)
        current.children = items
        this.position = position
    }
}

class ScrollIndicatorHelper(val recycler: RecyclerView,
                            val fab: FloatingActionButton,
                            val action: (FloatingActionButton, Boolean) -> Unit) : RecyclerView.OnFlingListener() {
    var delayedTime = 2000L

    var minVelocity = 5000

    var showLimits = 64

    val listener = object : FloatingActionButton.OnVisibilityChangedListener() {
        override fun onShown(fab: FloatingActionButton) {
            fab.postDelayed({
                fab.hide()
            }, delayedTime)
        }
    }

    init {
        fab.setOnClickListener {
            if (it.tag == true) { // down
                val end = recycler.adapter.itemCount - 1
                if (end - recycler.bottomPosition <= recycler.childCount) {
                    recycler.smoothScrollToPosition(end)
                } else {
                    recycler.scrollToPosition(end)
                }
                fab.hide()
            } else { // up
                if (recycler.topPosition <= recycler.childCount) {
                    recycler.smoothScrollToPosition(0)
                } else {
                    recycler.scrollToPosition(0)
                }
                fab.hide()
            }
        }
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        if (recycler.adapter.itemCount < showLimits) {
            return false
        } else if (velocityY < 0) { // up
            if (velocityY < -minVelocity) {
                fab.tag = false
                action(fab, false)
                fab.show(listener)
            }
        } else { // down
            if (velocityY > minVelocity) {
                fab.tag = true
                action(fab, true)
                fab.show(listener)
            }
        }
        return false
    }
}

class PathHolder(view: View, listener: View.OnClickListener) : RecyclerView.ViewHolder(view) {
    init {
        view.setOnClickListener(listener)
    }
}

class PathAdapter<T : Hierarchical>(val layout: Int,
                                    val helper: HierarchicalHelper<T, *>,
                                    val binder: (View, String, Int) -> Unit,
                                    val listener: ((View, T) -> Unit)? = null) : RecyclerView.Adapter<PathHolder>(), View.OnClickListener {
    var color: Int = Color.BLACK
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathHolder {
        return PathHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false), this)
    }

    override fun onBindViewHolder(holder: PathHolder, position: Int) {
        val item = items[position]
        val root = holder.itemView

        root[R.id.tag_data] = item
        root[R.id.tag_extra] = holder
        binder(root, item.toString(), color)
    }

    override fun onClick(view: View) {
        if (!helper.activatePathItem(view[R.id.tag_data], view.get<PathHolder>(R.id.tag_extra).adapterPosition)) { // path item is current
            listener?.invoke(view, view[R.id.tag_data])
        }
    }
}
