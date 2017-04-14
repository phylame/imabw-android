package pw.phylame.imabw.activity

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.widget.Toast
import com.tbruyelle.rxpermissions.RxPermissions
import pw.phylame.imabw.ImabwApp
import pw.phylame.imabw.R
import pw.phylame.support.*

abstract class BaseActivity : ManagedActivity() {
    companion object {
        private val TAG: String = BaseActivity::class.java.simpleName
    }

    private var isMenuCreated = false
    private var delayedActionColor: Int? = null
    private var toolbar: Toolbar? = null

    var defaultPrimaryColor = 0
    var defaultBackgroundColor = 0
    var defaultMdStatusMode = false

    var isMdStatusMode = false
        protected set

    var primaryColor: Int = 0
        protected set

    var actionBarSize: Int = 0
        protected set

    var isImageBackground = false
        protected set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBarSize = getStyledPixel(R.attr.actionBarSize)
        defaultPrimaryColor = ContextCompat.getColor(this, R.color.colorPrimary)
        defaultBackgroundColor = ContextCompat.getColor(this, R.color.colorBackground)

        val prefs = ImabwApp.sharedApp.uiSettings
        primaryColor = prefs.getInt("colorPrimary", defaultPrimaryColor)
        isMdStatusMode = prefs.getBoolean("mdStatusColor", defaultMdStatusMode)

        prefs.registerOnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "colorPrimary" -> primaryColor = prefs.getInt(key, defaultPrimaryColor)
                "mdStatusMode" -> isMdStatusMode = prefs.getBoolean(key, defaultMdStatusMode)
                "backgroundColor" -> setBackgroundColor(prefs.getInt("backgroundColor", defaultBackgroundColor))
                "backgroundImage" -> setBackgroundImage(prefs.getString("backgroundImage", ""))
            }
        }

        setBackgroundImage(prefs.getString("backgroundImage", ""))
    }

    // sub override must call lastly super method
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        isMenuCreated = true
        if (delayedActionColor != null) {
            menu.setTint(delayedActionColor!!)
        }
        return super.onCreateOptionsMenu(menu)
    }

    fun setBackgroundImage(path: String) {
        if (path.isNotEmpty()) {
            isImageBackground = true
            window.setBackgroundDrawable(BitmapDrawable.createFromPath(path))
        } else {
            setBackgroundColor(ImabwApp.sharedApp.uiSettings.getInt("backgroundColor", defaultBackgroundColor))
        }
    }

    fun setBackgroundColor(color: Int) {
        isImageBackground = false
        window.setBackgroundDrawable(ColorDrawable(color))
    }

    fun setupToolbar(toolbar: Toolbar, showHomeAsUp: Boolean = false, showTitle: Boolean = true) {
        this.toolbar = toolbar
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayShowTitleEnabled(showTitle)
        actionBar.setDisplayHomeAsUpEnabled(showHomeAsUp)
    }

    // return color for title & menu
    @ColorInt
    fun setToolbarColor(@ColorInt color: Int, @ColorInt actionColor: Int? = null): Int {
        val tintColor = actionColor ?: showyColorFor(color)
        val toolbar = toolbar
        if (toolbar != null) {
            toolbar.setBackgroundColor(color)
            toolbar.setTitleTextColor(tintColor)
            toolbar.tintItems(tintColor)
            if (!isMenuCreated) { // options menu is not create, do it when creating
                delayedActionColor = tintColor
            }
            setStatusColor(if (isMdStatusMode) generateMdColor(color, 700) else color)
        } else {
            Log.d(TAG, "no toolbar found, use setupToolbar for initialization")
        }
        return tintColor
    }

    fun requestPermissions(vararg permissions: String) {
        RxPermissions(this)
                .request(*permissions)
                .subscribe { granted ->
                    if (!granted) {
                        Toast.makeText(this, R.string.deny_permission_tip, Toast.LENGTH_SHORT).show()
                    }
                }
    }
}
