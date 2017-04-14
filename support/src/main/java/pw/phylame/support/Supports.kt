package pw.phylame.support

import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.support.annotation.ColorInt
import android.support.annotation.IntRange
import android.support.v4.graphics.ColorUtils
import android.util.SparseArray
import rx.Observable
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


fun isLightColor(@ColorInt color: Int) = ColorUtils.calculateLuminance(color) > 0.5

@ColorInt
fun darkenColor(@ColorInt base: Int, @IntRange(from = 0, to = 100) amount: Int, alpha: Int = 0xFF): Int {


    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base, hsl)
    hsl[2] -= amount / 100f
    if (hsl[2] < 0) {
        hsl[2] = 0f
    }
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
}

@ColorInt
fun lightenColor(@ColorInt base: Int, @IntRange(from = 0, to = 100) amount: Int, alpha: Int = 0xFF): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base, hsl)
    hsl[2] += amount / 100f
    if (hsl[2] > 1) {
        hsl[2] = 1f
    }
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
}

@ColorInt
fun generateMdColor(@ColorInt base: Int, num: Int, alpha: Int = 0xFF): Int = when (num) {
    50 -> lightenColor(base, 52, alpha)
    100 -> lightenColor(base, 37, alpha)
    200 -> lightenColor(base, 26, alpha)
    300 -> lightenColor(base, 12, alpha)
    400 -> lightenColor(base, 6, alpha)
    500 -> lightenColor(base, 0, alpha)
    600 -> darkenColor(base, 6, alpha)
    700 -> darkenColor(base, 12, alpha)
    800 -> darkenColor(base, 18, alpha)
    900 -> darkenColor(base, 24, alpha)
    else -> ColorUtils.setAlphaComponent(base, alpha)
}

@ColorInt
fun transformColor(@ColorInt color: Int, saturation: Float = 1F, lightness: Float = 1F, alpha: Int = 0xFF): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] *= saturation
    hsl[2] *= lightness
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
}

@ColorInt
fun showyColorFor(@ColorInt color: Int, saturation: Float = 1F, lightness: Float = 1F) = if (isLightColor(color)) {
    transformColor(Color.BLACK, saturation, lightness)
} else {
    transformColor(Color.WHITE, saturation, lightness)
}

operator fun <E> SparseArray<E>.set(key: Int, value: E) = put(key, value)

operator fun <E> SparseArray<E>.contains(key: Int): Boolean = contains(key)

fun <E> SparseArray<E>.getOrPut(key: Int, defaultValue: () -> E): E {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        put(key, answer)
        answer
    } else {
        value
    }
}

fun <E> MutableList<E>.swap(from: Int, to: Int) {
    Collections.swap(this, from, to)
}

interface Titled {
    val titleId: Int
}

object PlatformCompat {
    val buildProps: Properties by lazy {
        val prop = Properties()
        File(Environment.getRootDirectory(), "build.prop")
                .inputStream()
                .use(prop::load)
        prop
    }

    val isMIUI: Boolean
        get() = buildProps.containsKey("ro.miui.ui.version.name")

    val isEMUI: Boolean
        get() = buildProps.containsKey("ro.build.version.emui")

    val isFlyme: Boolean
        get() = try {
            Build::class.java.getMethod("hasSmartBar") != null
        } catch (ignored: NoSuchMethodException) {
            false
        }
}

class TimedAction(val limit: Long) {
    private var last: Long = 0

    val isEnable: Boolean
        get() {
            val now = System.currentTimeMillis()
            return if (now - last < limit) {
                last = 0
                true
            } else {
                last = now
                false
            }
        }
}

object DataHub {
    private val data = SparseArray<Any>()

    operator fun set(key: Int, o: Any) {
        data.put(key, o)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Int): T {
        return data.get(key) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> take(key: Int): T {
        val obj = data.get(key) as T
        data.remove(key)
        return obj
    }

    fun remove(key: Int) {
        data.remove(key)
    }

    fun clear() {
        data.clear()
    }
}

object RxBus {
    private val bus = SerializedSubject(PublishSubject.create<Any>())
    private val stickyEvents = ConcurrentHashMap<Class<*>, Any>()

    fun hasObservers() = bus.hasObservers()

    fun post(event: Any) = bus.onNext(event)

    fun <T> toObservable(eventType: Class<T>): Observable<T> = bus.ofType(eventType)

    fun <T> subscribe(eventType: Class<T>, action: Action1<T>) = toObservable(eventType).subscribe(action)

    fun postSticky(event: Any) = synchronized(stickyEvents) {
        stickyEvents.put(event.javaClass, event)
        post(event)
    }

    fun <T> subscribeSticky(eventType: Class<T>, action: Action1<T>) = toObservableSticky(eventType).subscribe(action)

    fun <T> toObservableSticky(eventType: Class<T>): Observable<T> = synchronized(stickyEvents) {
        val observable = bus.ofType(eventType)
        val event = stickyEvents[eventType]
        if (event != null) {
            observable.mergeWith(Observable.create {
                it.onNext(eventType.cast(event))
            })
        } else {
            observable
        }
    }

    fun <T> getStickyEvent(eventType: Class<T>): T = synchronized(stickyEvents) {
        return eventType.cast(stickyEvents[eventType])
    }

    fun <T> removeStickyEvent(eventType: Class<T>): T = synchronized(stickyEvents) {
        return eventType.cast(stickyEvents.remove(eventType))
    }

    fun removeAllStickyEvents() = synchronized(stickyEvents) {
        stickyEvents.clear()
    }
}

object Worker {
    val executor: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    fun cleanup() {
        executor.shutdown()
    }

    fun <R> schedule(task: () -> R) = Observable.create<R> { sub ->
        sub.onNext(task())
        sub.onCompleted()
    }.subscribeOn(Schedulers.from(executor))
}