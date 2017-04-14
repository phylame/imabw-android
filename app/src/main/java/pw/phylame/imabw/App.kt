package pw.phylame.imabw

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import pw.phylame.support.Worker

private typealias CleanupAction = () -> Unit

class ImabwApp : Application() {
    companion object {
        lateinit var sharedApp: ImabwApp
    }

    private val cleanups = LinkedHashSet<CleanupAction>()

    init {
        sharedApp = this
        registerCleanup(Worker::cleanup)
    }

    val uiSettings: SharedPreferences by lazy {
        getSharedPreferences("ui", Context.MODE_PRIVATE)
    }

    val generalSettings: SharedPreferences by lazy {
        getSharedPreferences("general", Context.MODE_PRIVATE)
    }

    fun registerCleanup(action: CleanupAction) {
        cleanups.add(action)
    }

    fun cleanup() {
        for (action in cleanups) {
            action()
        }
    }
}
