package kaist.iclab.wearabletracker.ema

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Repository that loads and caches the microEMA survey configuration.
 *
 * Phase 1: Loads from a bundled JSON asset in the watch app.
 * Future: Will fetch from the phone via MessageClient.
 */
class MicroEmaRepository(
    private val context: Context
) {
    companion object {
        private const val TAG = "MicroEmaRepo"
        private const val ASSET_FILE = "micro_ema_config.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // In-memory cache
    private var cachedConfig: WatchSurveyConfig? = null

    /**
     * Load survey config from the bundled JSON asset.
     * Caches the result in memory so subsequent calls are instant.
     *
     * @return [WatchSurveyConfig] or null if loading/parsing fails.
     */
    fun loadSurveyConfig(): WatchSurveyConfig? {
        cachedConfig?.let { return it }

        return try {
            val jsonString = context.assets.open(ASSET_FILE)
                .bufferedReader()
                .use { it.readText() }
            val config = parseSurveyConfig(jsonString)
            cachedConfig = config
            Log.d(TAG, "Loaded survey config: ${config.title} with ${config.questions.size} questions")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load survey config from assets", e)
            null
        }
    }

    /**
     * Parse a JSON string into a [WatchSurveyConfig].
     * This method is also useful when receiving config from the phone via MessageClient.
     */
    fun parseSurveyConfig(jsonString: String): WatchSurveyConfig {
        return json.decodeFromString<WatchSurveyConfig>(jsonString)
    }

    /**
     * Clear the cached config (e.g., when a new config is received from the phone).
     */
    fun clearCache() {
        cachedConfig = null
    }
}
