package pw.phylame.imabw.activity

import android.Manifest
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.ColorUtils
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.amulyakhare.textdrawable.TextDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import jem.Book
import jem.Chapter
import jem.epm.EpmInParam
import jem.kotlin.*
import kotlinx.android.synthetic.main.activity_imabw.*
import kotlinx.android.synthetic.main.path_item.view.*
import kotlinx.android.synthetic.main.popup_chapter_overview.view.*
import pw.phylame.imabw.InputBuilder
import pw.phylame.imabw.Jem
import pw.phylame.imabw.R
import pw.phylame.support.*
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.*
import pw.phylame.support.R as SR

class ImabwActivity : BaseActivity(), View.OnClickListener {
    companion object {
        private val TAG: String = ImabwActivity::class.java.simpleName
    }

    val quitGuard = TimedAction(2000)

    lateinit var contents: Contents
    lateinit var pathAdapter: PathAdapter<ChapterItem>

    var bookColor = 0
    var itemColor = 0
    var popup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imabw)
        setupToolbar(toolbar, showTitle = false)

        bookTitle.setOnClickListener(this)
        bookCover.setOnClickListener(this)
        fab.setOnClickListener(this)

        contents = Contents(chapterList)
        initPathBar()
        initRecycler()
        initControlsBar()

        newBook()
        requestPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_manage -> contents.beginSelection()
            R.id.action_new -> newBook()
            R.id.action_open -> openControlsMenu()
            R.id.action_extensions -> editExtensions(book!!)
            R.id.action_settings -> startActivity(SettingsActivity::class.java)
            R.id.action_exit -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBackPressed() {
        if (contents.isInSelection) {
            contents.finishSelection()
        } else if (contents.backParent()) {
            return
        } else {
            if (quitGuard.isEnable) {
                super.onBackPressed()
            } else {
                Toast.makeText(this, R.string.quit_press_tip, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        popup?.dismiss()
        book?.cleanup()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fab -> contents.newChapter(contents.root, -1)
            R.id.bookTitle -> renameChapter(book!!) { bookTitle.text = it }
            R.id.bookCover -> editAttributes(book!!, toolbar.backgroundColor)
        }
    }

    private fun initPathBar() {
        pathList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pathAdapter = PathAdapter(R.layout.path_item, contents, { root, name, color ->
            root.text.setTextAndColor(name, color)
            root.indicator.tintImage(color)
        })
        pathList.adapter = pathAdapter
    }

    private fun initRecycler() {
        if (isImageBackground) {
            chapterList.setBackgroundResource(R.drawable.light_mask)
        }
        chapterList.layoutManager = LinearLayoutManager(this)
        chapterList.onFlingListener = ScrollIndicatorHelper(chapterList, scroller) { fab, toDown ->
            fab.setImageResource(if (toDown) R.mipmap.ic_expand_arrow else R.mipmap.ic_collapse_arrow)
        }
    }

    private fun initControlsBar() {
        controlsBar.inflateMenu(R.menu.controls)
        controlsBar.setNavigationOnClickListener {
            contents.finishSelection()
        }
        controlsBar.setOnMenuItemClickListener {
            var result = true
            when (it.itemId) {
                R.id.action_cut -> contents.cut()
                R.id.action_delete -> deleteChapters()
                R.id.action_select_all -> contents.toggleSelections()
                else -> result = false
            }
            result
        }
    }

    private fun openControlsMenu() {
        val files = File(Environment.getExternalStorageDirectory(), "Books").listFiles()
        if (files != null) {
            val file = files[Random().nextInt(files.size)]
            openBook(EpmInParam(file, null, null, null))
        }
    }

    // Book & Chapter Operations

    fun newBook() {
        val untitled = getString(R.string.untitled)
        val book = Book(untitled)
        book.date = Date()
        book.vendor = "Imabw for Android"
        book.append(Chapter(untitled))
        setBook(book, null)
    }

    fun openBook(param: EpmInParam) {
        var dialog: ProgressDialog? = null
        Jem.openBook(param)
                .subscribeOn(Schedulers.io())
                .doOnNext {
                    book?.cleanup()
                }
                .map { book ->
                    val cover = book.cover
                    book to if (cover != null) Jem.cache(cover, cacheDir) else null
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    dialog = ProgressDialog.show(this, null, getString(R.string.loading), true, false)
                }
                .subscribe({ (book, cover) ->
                    setBook(book, cover)
                    dialog?.dismiss()
                }, { error ->
                    dialog?.dismiss()
                    Log.e(TAG, "failed to load book", error)
                    AlertDialog.Builder(this)
                            .setMessage(printable(error, param))
                            .setTitle(R.string.open_book_error)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                })
    }

    fun editExtensions(book: Book) {

    }

    fun editAttributes(chapter: Chapter, chapterColor: Int?) {
        if (chapterColor != null) {
            DataHub[AttributeActivity.COLOR_KEY] = chapterColor
        }
        DataHub[AttributeActivity.CHAPTER_KEY] = chapter
        startActivity(AttributeActivity::class.java)
    }

    fun editText(chapter: Chapter, chapterColor: Int) {
        DataHub[TextActivity.COLOR_KEY] = chapterColor
        DataHub[TextActivity.CHAPTER_KEY] = chapter
        startActivity(TextActivity::class.java)
    }

    // action is invoked after name is changed
    fun renameChapter(chapter: Chapter, action: (String) -> Unit) {
        InputBuilder(this)
                .setTint(bookColor)
                .setText(chapter.title)
                .setHint(R.string.new_chapter_hint)
                .setListener(R.string.rename) {
                    chapter.title = it
                    action(it)
                }
                .setTitle(R.string.rename_chapter_title)
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    fun deleteChapters() {
        val number = contents.selections.size
        if (number > 0) {
            val dialog = AlertDialog.Builder(this)
                    .setMessage(resources.getQuantityString(R.plurals.delete_chapter_tip, number, number))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        contents.removeSelections(true)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(bookColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(bookColor)
        }
    }

    var book: Book? = null

    fun setBook(book: Book, cover: File?) {
        this.book = book

        bookTitle.text = book.title
        val coverSize = actionBarSize
        if (cover != null) {
            Glide.with(this)
                    .load(cover)
                    .crossFade()
                    .override(coverSize, coverSize)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(Images.palette { palette ->
                        if (palette != null) {
                            setViewColors(Jem.coverSwatch(palette)?.rgb ?: primaryColor)
                        } else {
                            setViewColors(primaryColor)
                        }
                    })
                    .into(bookCover)
        } else {
            val drawable = TextDrawable.builder()
                    .beginConfig()
                    .bold()
                    .width(coverSize)
                    .height(coverSize)
                    .textColor(setViewColors(primaryColor))
                    .endConfig()
                    .buildRound(Jem.hintTextOf(book.title), primaryColor)
            bookCover.setImageDrawable(drawable)
        }
        contents.activateBook(book)
    }

    fun setViewColors(primaryColor: Int): Int {
        bookColor = primaryColor
        val actionColor = setToolbarColor(primaryColor)
        setPathBarColor(primaryColor, actionColor)
        bookCover.borderColor = actionColor
        bookTitle.setTextColor(actionColor)
        setContentsColor(generateMdColor(primaryColor, 600))
        val bottomColor = generateMdColor(primaryColor, 500)
        setControlsBarColor(ColorUtils.setAlphaComponent(bottomColor, 0xF0), actionColor)
        scroller.tintBackground(bottomColor)
        scroller.tintImage(actionColor)
        fab.tintBackground(bottomColor)
        fab.tintImage(actionColor)
        return actionColor
    }

    fun setContentsColor(itemColor: Int) {
        this.itemColor = itemColor
        contents.setItemColor(itemColor)
    }

    fun setPathBarColor(barColor: Int, itemColor: Int) {
        pathBar.setBackgroundColor(barColor)
        pathAdapter.color = itemColor
    }

    fun setControlsBarColor(barColor: Int, itemColor: Int) {
        controlsBar.setBackgroundColor(barColor)
        controlsBar.setTitleTextColor(itemColor)
        controlsBar.tintItems(itemColor)
    }

    fun printable(e: Throwable, param: EpmInParam): String = e.toString()

    inner class Contents(recycler: RecyclerView) : ContentsHelper(recycler) {
        override fun onPreLoad() {
            pathProgress.isVisible = true
        }

        override fun onPostLoad(items: List<ChapterItem>) {
            super.onPostLoad(items)
            pathProgress.isVisible = false
            pathAdapter.items = pathToRoot()
            pathList.smoothScrollToPosition(pathAdapter.itemCount - 1)
        }

        override fun onItemClicked(item: ChapterItem, position: Int, view: View): Boolean {
            if (!super.onItemClicked(item, position, view)) {
                editText(item.chapter, bookColor)
            }
            return true
        }

        override fun onItemCoverClicked(item: ChapterItem, position: Int, view: View) {
            popup = OverviewPopup(this@ImabwActivity).show(item)
        }

        override fun onSizeChanged() {
            placeholder.isVisible = size == 0
        }

        override fun activateBook(book: Book) {
            super.activateBook(book)
            appbar.setExpanded(true)
        }

        override fun newChapter(parent: ChapterItem, position: Int) {
            InputBuilder(this@ImabwActivity)
                    .setTint(bookColor)
                    .setHint(R.string.new_chapter_hint)
                    .setText(getString(R.string.untitled))
                    .setListener(R.string.add) {
                        val chapter = Chapter(it)
                        if (position < 0) {
                            contents.appendItem(ChapterItem(chapter))
                            val end = contents.size - 1
                            val last = (chapterList.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                            if (end - last < chapterList.childCount) {
                                chapterList.scrollToPosition(end)
                                appbar.setExpanded(false)
                            }
                        } else {
                            appendChapter(chapter, parent.chapter, position)
                        }
                    }
                    .setTitle(R.string.new_chapter_title)
                    .setNegativeButton(R.string.cancel, null)
                    .show()
        }

        override fun renameChapter(parent: ChapterItem, position: Int) {
            renameChapter(parent.chapter) {
                adapter.notifyItemChanged(position)
            }
        }

        override fun beginSelection(): Boolean = if (super.beginSelection()) {
            controlsBar.isVisible = true
            fab.hide()
            true
        } else false

        override fun finishSelection() {
            super.finishSelection()
            controlsBar.isVisible = false
            fab.show()
        }

        override fun onSelectionChanged(position: Int) {
            super.onSelectionChanged(position)
            val total = size
            if (total == 0) {
                finishSelection()
            } else {
                val menu = controlsBar.menu
                val number = contents.selections.size
                menu.findItem(R.id.action_cut).isVisible = number != 0
                menu.findItem(R.id.action_merge).isVisible = number > 1
                menu.findItem(R.id.action_delete).isVisible = number != 0
                menu.findItem(R.id.action_select_all).isVisible = total != 0
                controlsBar.title = resources.getQuantityString(R.plurals.selection_title, number, number)
            }
        }
    }
}

class OverviewPopup(activity: ImabwActivity) : AbstractPopup<ImabwActivity>(activity, R.layout.popup_chapter_overview) {
    private val coverSize = activity.resources.getDimensionPixelSize(R.dimen.overview_cover_size)

    private var barColor: Int = 0
    private lateinit var item: ChapterItem

    init {
        val rootView = popup.contentView

        popup.animationStyle = R.style.AppTheme_OverviewAnimation
        popup.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.popup_background))
        rootView.findViewById(R.id.titleBar).setOnClickListener {
            popup.dismiss()
            activity.editAttributes(item.chapter, barColor)
        }
        rootView.titleBar.alpha = 0.85F
        rootView.chapterCover.alpha = 0.85F
    }

    fun show(item: ChapterItem): PopupWindow {
        bindData(item)
        this.item = item
        activity.alphaWindow(0.5F)
        popup.showAtLocation(activity.window.decorView, Gravity.CENTER, 0, 0)
        return popup
    }

    private fun bindData(item: ChapterItem) {
        val chapter = item.chapter
        val rootView = popup.contentView
        rootView.chapterTitle.text = chapter.title
        setCover(chapter, rootView.chapterCover)
        setIntro(chapter, rootView.chapterIntro)
    }

    private fun setCover(chapter: Chapter, imageView: ImageView) {
        val stream = chapter.cover?.openStream()
        if (stream != null) {
            Images.glide(activity, stream)
                    .dontAnimate()
                    .override(coverSize, coverSize)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(Images.consume { bmp ->
                        if (bmp != null) {
                            imageView.visibility = View.VISIBLE
                            setColors(Jem.coverSwatch(bmp)?.rgb ?: activity.itemColor)
                        } else {
                            imageView.visibility = View.GONE
                            setColors(activity.itemColor)
                        }
                    })
                    .into(imageView)
        } else {
            imageView.visibility = View.GONE
            setColors(activity.itemColor)
        }
    }

    private fun setIntro(chapter: Chapter, textView: TextView) {
        val intro = chapter.intro?.text
        if (intro.isNullOrEmpty()) {
            textView.setText(R.string.no_intro_tip)
            textView.gravity = Gravity.CENTER
        } else {
            textView.text = intro
            textView.gravity = Gravity.START
        }
    }

    private fun setColors(color: Int) {
        barColor = color
        val rootView = popup.contentView
        val textColor = showyColorFor(color)
        rootView.titleBar.setBackgroundColor(color)
        rootView.chapterCover.borderColor = textColor
        rootView.chapterTitle.setTextColor(textColor)
        rootView.chapterArrow.tintImage(textColor)
    }
}
