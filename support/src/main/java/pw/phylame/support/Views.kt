package pw.phylame.support

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.IdRes
import android.support.v4.graphics.ColorUtils
import android.support.v4.view.ViewCompat
import android.support.v4.widget.CompoundButtonCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*

@Suppress("UNCHECKED_CAST")
fun <T : Any?> View.tag(): T = tag as T

operator fun View.set(key: Int, tag: Any?) = setTag(key, tag)

@Suppress("UNCHECKED_CAST")
operator fun <T : Any?> View.get(key: Int): T = getTag(key) as T

@Suppress("UNCHECKED_CAST")
fun <T : View?> View.viewFor(@IdRes id: Int): T = findViewById(id) as T

fun View.shortAnimate(): ViewPropertyAnimator = animate()
        .setDuration(context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())

fun View.mediumAnimate(): ViewPropertyAnimator = animate()
        .setDuration(context.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong())

fun View.longAnimate(): ViewPropertyAnimator = animate()
        .setDuration(context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())

var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(visible) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        if (visibility != this.visibility) {
            this.visibility = visibility
            shortAnimate()
                    .alpha(if (visible) 1F else 0F)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            this@isVisible.visibility = visibility
                        }
                    })
                    .start()
        }
    }

var ViewGroup.topMargin
    get() = (layoutParams as ViewGroup.MarginLayoutParams).topMargin
    set(value) {
        val params = layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = value
        layoutParams = params
    }

var ViewGroup.bottomMargin
    get() = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    set(value) {
        val params = layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = value
        layoutParams = params
        width
    }

val View.backgroundColor: Int
    get() = (background as ColorDrawable).color

fun Drawable.tint(@ColorInt color: Int): Drawable {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setTint(color)
    } else {
        setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
    return this
}

fun View.tintBackground(@ColorInt color: Int) {
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(color))
}

fun TextView.setTextAndColor(text: CharSequence?, color: Int) {
    setText(text)
    setTextColor(color)
}

fun TextView.setTextAndColor(textRes: Int, color: Int) {
    setText(textRes)
    setTextColor(color)
}

fun ImageView.tintImage(@ColorInt color: Int) {
    setImageDrawable(drawable.mutate().tint(color))
}

fun CheckBox.setTint(@ColorInt color: Int) {
    CompoundButtonCompat.setButtonTintMode(this, PorterDuff.Mode.SRC_IN)
    CompoundButtonCompat.setButtonTintList(this, ColorStateList.valueOf(color))
}

fun SwitchCompat.setTint(@ColorInt color: Int) {
    thumbTintList = ColorStateList.valueOf(color)
    trackTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x4D))
}

fun MenuItem.setTint(@ColorInt color: Int) {
    icon = icon?.mutate()?.tint(color) ?: return
}

fun Menu.setTint(@ColorInt color: Int) {
    for (i in 0..size() - 1) {
        getItem(i).setTint(color)
    }
}

val MenuItem.position: Int get() = (menuInfo as AdapterView.AdapterContextMenuInfo).position

fun Toolbar.tintItems(@ColorInt color: Int) {
    navigationIcon = navigationIcon?.mutate()?.tint(color)
    overflowIcon = overflowIcon?.mutate()?.tint(color)
    menu?.setTint(color)
}

data class LinearPosition(val position: Int, val offset: Int)

var ListView.position: LinearPosition
    get() {
        var index = firstVisiblePosition
        var view: View? = getChildAt(0)
        var top = if (view == null) 0 else view.top
        if (top < 0 && getChildAt(1) != null) {
            index++
            view = getChildAt(1)
            top = view!!.top
        }
        return LinearPosition(index, top)
    }
    set(value) {
        if (value.offset < 0) {
            setSelection(value.position)
        } else {
            setSelectionFromTop(value.position, value.offset)
        }
    }

var RecyclerView.position: LinearPosition?
    get() {
        val lm = layoutManager
        return if (lm is LinearLayoutManager) {
            val offset = if (lm.orientation == LinearLayoutManager.VERTICAL) {
                getChildAt(0).top
            } else {
                getChildAt(0).left
            }
            LinearPosition(lm.findFirstVisibleItemPosition(), offset)
        } else {
            null
        }
    }
    set(value) {
        val lm = layoutManager
        if (lm is LinearLayoutManager) {
            if (value != null) {
                lm.scrollToPositionWithOffset(value.position, value.offset)
            } else {
                lm.scrollToPosition(0)
            }
        }
    }

val RecyclerView.topPosition: Int
    get() = (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1

val RecyclerView.bottomPosition: Int
    get() = (layoutManager as? LinearLayoutManager)?.findLastVisibleItemPosition() ?: -1

fun RecyclerView.Adapter<*>.notifyItemsChanged(payload: Any) {
    notifyItemRangeChanged(0, itemCount, payload)
}

open class DividerItemDecoration(context: Context, orientation: Int) : RecyclerView.ItemDecoration() {
    companion object {
        val HORIZONTAL = LinearLayout.HORIZONTAL
        val VERTICAL = LinearLayout.VERTICAL

        private val ATTRS = intArrayOf(android.R.attr.listDivider)
    }

    var divider: Drawable

    var orientation: Int = 0
        set(value) {
            if (value != HORIZONTAL && value != VERTICAL) {
                throw IllegalArgumentException("Invalid orientation. It should be either HORIZONTAL or VERTICAL")
            }
            field = value
        }

    open val isLastEnable: Boolean = true

    private val bounds = Rect()

    init {
        this.orientation = orientation
        val attrs = context.obtainStyledAttributes(ATTRS)
        divider = attrs.getDrawable(0)
        attrs.recycle()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
        if (parent.layoutManager == null) {
            return
        }
        if (orientation == VERTICAL) {
            drawVertical(c, parent)
        } else {
            drawHorizontal(c, parent)
        }
    }

    @SuppressLint("NewApi")
    open protected fun drawVertical(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val left: Int
        val right: Int
        if (parent.clipToPadding) {
            left = parent.paddingLeft
            right = parent.width - parent.paddingRight
            canvas.clipRect(left, parent.paddingTop, right, parent.height - parent.paddingBottom)
        } else {
            left = 0
            right = parent.width
        }

        val childCount = parent.childCount - if (isLastEnable) 0 else 1
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val bottom = bounds.bottom + Math.round(ViewCompat.getTranslationY(child))
            val top = bottom - divider.intrinsicHeight
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
        canvas.restore()
    }

    @SuppressLint("NewApi")
    open protected fun drawHorizontal(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val top: Int
        val bottom: Int
        if (parent.clipToPadding) {
            top = parent.paddingTop
            bottom = parent.height - parent.paddingBottom
            canvas.clipRect(parent.paddingLeft, top, parent.width - parent.paddingRight, bottom)
        } else {
            top = 0
            bottom = parent.height
        }

        val childCount = parent.childCount - if (isLastEnable) 0 else 1
        for (i in 0..childCount - 1) {
            val child = parent.getChildAt(i)
            parent.layoutManager.getDecoratedBoundsWithMargins(child, bounds)
            val right = bounds.right + Math.round(ViewCompat.getTranslationX(child))
            val left = right - divider.intrinsicWidth
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
        canvas.restore()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        if (orientation == VERTICAL) {
            outRect.set(0, 0, 0, divider.intrinsicHeight)
        } else {
            outRect.set(0, 0, divider.intrinsicWidth, 0)
        }
    }
}
