package pw.phylame.support

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.IdRes
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.reflect.KProperty

abstract class ManagedActivity : AppCompatActivity() {
    private val views = SparseArray<View>(16)

    @Suppress("UNCHECKED_CAST")
    operator fun <T : View> Int.getValue(ref: ManagedActivity, prop: KProperty<*>): T = views.getOrPut(this) {
        findViewById(this)
    } as T
}

@Suppress("UNCHECKED_CAST")
fun <T : View?> Activity.viewFor(@IdRes id: Int): T = findViewById(id) as T

fun Activity.alphaWindow(alpha: Float) {
    val params = window.attributes
    val animator = ValueAnimator.ofFloat(params.alpha, alpha)
    animator.duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
    animator.addUpdateListener {
        params.alpha = it.animatedValue as Float
        window.attributes = params
    }
    animator.start()
}

fun Activity.scaledWidth(rate: Double): Int = (window.decorView.width * rate).toInt()

fun Activity.scaledHeight(rate: Double): Int = (window.decorView.height * rate).toInt()

val Activity.isPortrait get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

fun Activity.startActivity(target: Class<out Activity>) {
    startActivity(Intent(this, target))
}

val Activity.statusHeight
    get() = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))

val Activity.navigationHeight
    get() = resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height", "dimen", "android"))

fun Activity.setStatusTransparent() {
    val window = window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    } else {
        return
    }
}

fun Activity.setStatusColor(@ColorInt color: Int, light: Boolean? = null) {
    val window = window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        val statusViewTag = "coloredStatusBar"
        val decorView = window.decorView as ViewGroup
        var statusView = decorView.findViewWithTag(statusViewTag)
        if (statusView == null) { // add colored view
            statusView = View(this)
            statusView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusHeight)
            statusView.tag = statusViewTag
            decorView.addView(statusView)

            val rootView = (findViewById(android.R.id.content) as ViewGroup).getChildAt(0)
            if (rootView is DrawerLayout) {
                rootView.getChildAt(0).fitsSystemWindows = true
            } else {
                rootView.fitsSystemWindows = true
            }
        }
        statusView.setBackgroundColor(color)
    } else {
        return
    }
    setStatusLight(light ?: isLightColor(color))
}

fun Activity.setStatusLight(light: Boolean) = when {
    PlatformCompat.isMIUI -> setMiuiStatusLight(light)
    PlatformCompat.isFlyme -> setFlymeStatusLight(light)
    else -> setAospStatusLight(light)
}

fun Activity.setMiuiStatusLight(light: Boolean) = try {
    val paramsClass = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
    val field = paramsClass.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
    field.isAccessible = true
    val bits = field.getInt(null)
    val method = window.javaClass.getMethod("setExtraFlags", Int::class.java, Int::class.java)
    method.isAccessible = true
    method.invoke(window, if (light) bits else 0, bits)
    true
} catch (e: Exception) {
    false
}

fun Activity.setFlymeStatusLight(light: Boolean) = try {
    val attrs = window.attributes
    var field = WindowManager.LayoutParams::class.java.getField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
    field.isAccessible = true
    val bits = field.getInt(null)
    field = WindowManager.LayoutParams::class.java.getDeclaredField("meizuFlags")
    field.isAccessible = true
    var flags = field.getInt(attrs)
    if (light) {
        flags = flags or bits
    } else {
        flags = flags and bits.inv()
    }
    field.setInt(attrs, flags)
    window.attributes = attrs
    true
} catch (e: Exception) {
    false
}

fun Activity.setAospStatusLight(light: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    val decorView = window.decorView
    var flags = decorView.systemUiVisibility
    if (light) {
        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    } else {
        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }
    decorView.systemUiVisibility = flags
    true
} else {
    false
}