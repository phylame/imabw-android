package pw.phylame.imabw

import android.graphics.Bitmap
import android.support.v7.graphics.Palette
import android.util.Log
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import jem.Book
import jem.epm.EpmInParam
import jem.epm.EpmManager
import jem.util.flob.Flob
import pw.phylame.support.Titled
import pw.phylame.support.showyColorFor
import rx.Observable
import java.io.File
import java.io.IOException

object Jem {
    val TAG: String = Jem.javaClass.simpleName

    enum class PaletteMode(val id: Int, override val titleId: Int) : Titled {
        Normal(0, R.string.palette_mode_normal),
        Light(1, R.string.palette_mode_light),
        Dark(2, R.string.palette_mode_dark),
        Fallback(3, R.string.palette_mode_fallback)
    }

    var isCoverPalette = false

    var coverPaletteMode = PaletteMode.Normal

    init {
        EpmManager.loadImplementors()
        val prefs = ImabwApp.sharedApp.uiSettings
        prefs.registerOnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "coverPalette" -> isCoverPalette = prefs.getBoolean(key, true)
                "paletteMode" -> coverPaletteMode = PaletteMode.valueOf(prefs.getString(key, ""))
            }
        }
        isCoverPalette = prefs.getBoolean("coverPalette", true)
        coverPaletteMode = PaletteMode.valueOf(prefs.getString("paletteMode", PaletteMode.Normal.name))
    }

    fun openBook(param: EpmInParam): Observable<Book> = Observable.create<Book> {
        try {
            it.onNext(EpmManager.readBook(param.file, param.format, param.arguments))
            it.onCompleted()
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    fun cache(flob: Flob, dir: File): File? {
        val name = Integer.toHexString(flob.toString().hashCode())
        val file = File(dir, name)
        if (file.exists()) {
            return file
        }
        try {
            file.outputStream().use {
                flob.writeTo(it)
            }
        } catch (e: IOException) {
            if (!file.delete()) {
                Log.e(TAG, "cannot delete cache file: $file")
            }
        }
        return file
    }

    fun hintTextOf(title: String): String {
        var last: Char? = null
        title.reversed().forEach { ch ->
            if (ch.isWhitespace() || ch in separator && last != null) {
                return last.toString()
            }
            last = ch
        }
        return title.firstOrNull()?.toString() ?: ""
    }

    fun coverSwatch(drawable: GlideDrawable, forcing: Boolean = false, mode: PaletteMode? = null): Palette.Swatch? {
        return coverSwatch((drawable as GlideBitmapDrawable).bitmap, forcing, mode)
    }

    fun coverSwatch(bmp: Bitmap?, forcing: Boolean = false, mode: PaletteMode? = null): Palette.Swatch? {
        if (bmp == null || (!isCoverPalette && !forcing)) {
            return null
        }
        return coverSwatch(Palette.from(bmp).generate(), mode)
    }

    fun coverSwatch(palette: Palette, mode: PaletteMode? = null): Palette.Swatch? = when (mode ?: coverPaletteMode) {
        PaletteMode.Normal -> palette.vibrantSwatch ?: palette.mutedSwatch
        PaletteMode.Light -> palette.lightVibrantSwatch ?: palette.lightMutedSwatch
        PaletteMode.Dark -> palette.darkVibrantSwatch ?: palette.darkMutedSwatch
        PaletteMode.Fallback -> palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
    }

    fun coverTextColor(color: Int) = showyColorFor(color, 0.6F, 0.95F)

    val separator = charArrayOf('\u3000', '\uff1a', '\u201C', '\u300a', '\uFF0C', ':')
}
