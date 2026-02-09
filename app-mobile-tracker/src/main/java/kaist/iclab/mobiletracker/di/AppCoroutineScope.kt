package kaist.iclab.mobiletracker.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Application-wide coroutine scope container.
 * Provides centralized scope management for background operations.
 *
 * Usage:
 * - Inject via Koin: `private val appScope by inject<AppCoroutineScope>()`
 * - Use `appScope.io` for IO-bound operations (network, database)
 * - Use `appScope.main` for main-thread operations (UI updates)
 */
class AppCoroutineScope(
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val main: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    /**
     * Cancels all coroutines in both scopes.
     * Should only be called when the application is terminating.
     */
    fun cancel() {
        io.cancel()
        main.cancel()
    }
}
