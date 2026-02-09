package kaist.iclab.mobiletracker.viewmodels.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DataUiState] data class.
 * Verifies default values and state transition patterns.
 */
class DataUiStateTest {

    @Test
    fun `default state is loading`() {
        val state = DataUiState()
        assertTrue(state.isLoading)
    }

    @Test
    fun `default state has empty sensor list`() {
        val state = DataUiState()
        assertTrue(state.sensors.isEmpty())
        assertEquals(0, state.totalRecords)
    }

    @Test
    fun `default state has no error`() {
        val state = DataUiState()
        assertNull(state.error)
    }

    @Test
    fun `default state has no async operations in progress`() {
        val state = DataUiState()
        assertFalse(state.isUploading)
        assertFalse(state.isDeleting)
        assertFalse(state.isExporting)
    }

    @Test
    fun `default timestamps show placeholder`() {
        val state = DataUiState()
        assertEquals("--", state.currentTime)
        assertNull(state.lastWatchData)
        assertNull(state.lastSuccessfulUpload)
    }

    @Test
    fun `transition to loaded state clears loading flag`() {
        val loading = DataUiState()
        val loaded = loading.copy(isLoading = false, totalRecords = 150)
        assertFalse(loaded.isLoading)
        assertEquals(150, loaded.totalRecords)
    }

    @Test
    fun `transition to error state`() {
        val state = DataUiState().copy(
            isLoading = false,
            error = "Failed to load sensor data"
        )
        assertFalse(state.isLoading)
        assertEquals("Failed to load sensor data", state.error)
    }

    @Test
    fun `uploading flag can be toggled independently`() {
        val base = DataUiState(isLoading = false)
        val uploading = base.copy(isUploading = true)
        assertTrue(uploading.isUploading)
        assertFalse(uploading.isDeleting)
        assertFalse(uploading.isExporting)

        val done = uploading.copy(isUploading = false)
        assertFalse(done.isUploading)
    }

    @Test
    fun `deleting flag can be toggled independently`() {
        val base = DataUiState(isLoading = false)
        val deleting = base.copy(isDeleting = true)
        assertTrue(deleting.isDeleting)
        assertFalse(deleting.isUploading)
    }

    @Test
    fun `exporting flag can be toggled independently`() {
        val base = DataUiState(isLoading = false)
        val exporting = base.copy(isExporting = true)
        assertTrue(exporting.isExporting)
        assertFalse(exporting.isUploading)
    }

    @Test
    fun `equality works for identical states`() {
        val a = DataUiState(isLoading = false, totalRecords = 42)
        val b = DataUiState(isLoading = false, totalRecords = 42)
        assertEquals(a, b)
    }
}
