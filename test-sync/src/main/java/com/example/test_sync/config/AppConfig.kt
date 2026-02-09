package com.example.test_sync.config

import com.example.test_sync.BuildConfig

/**
 * Configuration file for test-sync app.
 * Contains all implemented configuration variables.
 *
 * Credentials are injected via BuildConfig from local.properties or environment variables.
 */
object AppConfig {

    // ==================== SUPABASE CONFIGURATION ====================

    /**
     * Supabase project URL
     * Injected from local.properties (SUPABASE_URL) or environment variable
     */
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL

    /**
     * Supabase anonymous/public key
     * Injected from local.properties (SUPABASE_ANON_KEY) or environment variable
     * This is safe to use in client applications
     */
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    /**
     * Supabase table name for test data
     * Make sure this table exists in your Supabase database
     * Required columns: id (serial), message (text), value (integer), created_at (timestamp)
     */
    const val SUPABASE_TABLE_NAME = "test_data"

    /**
     * Polling configuration for database monitoring
     * Controls how often the app checks for new data in the database
     */
    object Polling {
        /**
         * Polling interval in milliseconds
         * How often to check the database for new data (5 seconds = 5000ms)
         */
        const val INTERVAL_MS = 5000L

        /**
         * Retry delay in milliseconds when polling fails
         * How long to wait before retrying after an error (5 seconds = 5000ms)
         */
        const val RETRY_DELAY_MS = 5000L
    }

    // ==================== INTERNET/HTTP CONFIGURATION ====================

    /**
     * HTTPBin URL for testing HTTP requests
     * HTTPBin is a simple HTTP request & response service for testing
     */
    const val HTTPBIN_URL = "https://httpbin.org"

    // ==================== BLE CONFIGURATION ====================

    /**
     * BLE message keys for different data types
     * These keys are used to identify different types of messages in BLE communication
     */
    object BLEKeys {
        const val MESSAGE = "message"
        const val STRUCTURED_DATA = "structured_data"
        const val SENSOR_DATA_CSV = "sensor_data_csv"
    }

    // ==================== LOGGING CONFIGURATION ====================

    /**
     * Log tags for different components
     * Use these tags to filter logs: adb logcat | grep "TAG_NAME"
     */
    object LogTags {
        const val PHONE_BLE = "PHONE_BLE"
        const val PHONE_INTERNET = "PHONE_INTERNET"
        const val PHONE_SUPABASE = "PHONE_SUPABASE"
    }

}
