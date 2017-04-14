package pw.phylame.support

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupWindow

abstract class AbstractPopup<out T : Activity>(val activity: T, layoutId: Int) : PopupWindow.OnDismissListener {
    val popup: PopupWindow

    init {
        val root = LayoutInflater.from(activity).inflate(layoutId, null)
        popup = PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.isFocusable = true
        popup.isTouchable = true
        popup.isOutsideTouchable = true
        popup.setOnDismissListener(this)
        popup.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        popup.width = activity.scaledWidth(if (activity.isPortrait) 0.9 else 0.7)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.elevation = activity.dip(4F)
        }
    }

    override fun onDismiss() {
        activity.alphaWindow(1.0F)
    }
}