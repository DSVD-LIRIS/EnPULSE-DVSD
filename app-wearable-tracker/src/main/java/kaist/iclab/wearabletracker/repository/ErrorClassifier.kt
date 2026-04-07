package kaist.iclab.wearabletracker.repository

import android.util.Log
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Utility for classifying raw exceptions into typed [AppError] subtypes
 * and executing operations with automatic classification and logging.
 */
object ErrorClassifier {

    /**
     * Classify a raw [Throwable] into the most appropriate [AppError] subtype.
     *
     * @param e        The original exception.
     * @param context  A human-readable label describing where the error occurred.
     */
    fun classify(e: Throwable, context: String = ""): AppError {
        // Already classified — pass through.
        if (e is AppError) return e

        val prefix = if (context.isNotBlank()) "$context: " else ""
        val msg = "$prefix${e.message ?: "Unknown error"}"

        return when (e) {
            // Network errors
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException ->
                AppError.Network(msg, e)

            // "Not found" semantics
            is NoSuchElementException ->
                AppError.NotFound(msg, e)

            // Invalid arguments / validation
            is IllegalArgumentException ->
                AppError.Validation(msg, e)

            // IllegalStateException used for "no data" in specific contexts
            is IllegalStateException ->
                AppError.Validation(msg, e)

            else -> {
                // SQLite errors or others
                val className = e.javaClass.name
                when {
                    className.contains("SQLite", ignoreCase = true) ->
                        AppError.Database(msg, e)

                    else ->
                        AppError.Unknown(msg, e)
                }
            }
        }
    }

    /**
     * Execute [block] and wrap the outcome in a [Result], automatically
     * classifying any thrown exception into an [AppError] and logging it.
     */
    suspend inline fun <T> runClassified(
        tag: String,
        context: String,
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.Success(block())
        } catch (e: Throwable) {
            val classified = classify(e, context)
            Log.e(tag, classified.message, classified)
            Result.Error(classified)
        }
    }
}
