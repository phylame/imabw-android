package pw.phylame.imabw.activity

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import jem.Chapter
import jem.kotlin.title
import kotlinx.android.synthetic.main.activity_text.*
import pw.phylame.imabw.R
import pw.phylame.support.DataHub

class TextActivity : BaseActivity() {
    companion object {
        private val TAG: String = TextActivity::class.java.simpleName

        const val COLOR_KEY = 201
        const val CHAPTER_KEY = 200
    }

    lateinit var chapter: Chapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text)
        setupToolbar(toolbar, showHomeAsUp = true)
        if (isImageBackground) {
            container.setBackgroundResource(R.drawable.light_mask)
        }

        val chapter: Chapter? = DataHub.take(CHAPTER_KEY)
        if (chapter == null) {
            Log.e(TAG, "no chapter found in Hub")
            finish()
        } else {
            initChapter(chapter)
            this.chapter = chapter
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> {
                toggleEditable()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleEditable() {
    }

    private fun initChapter(chapter: Chapter) {
        title = chapter.title
        text.setText(chapter.text?.text)
        val toolbarColor = DataHub.take(COLOR_KEY) ?: primaryColor
        setToolbarColor(toolbarColor)
    }
}
