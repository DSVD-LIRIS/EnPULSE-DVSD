package kaist.iclab.mobiletracker.viewmodels.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kaist.iclab.mobiletracker.repository.*
import kaist.iclab.tracker.auth.Authentication
import kaist.iclab.tracker.auth.UserState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication and user profile management.
 *
 * Handles Google Sign-In authentication flow, token management, and user profile
 * synchronization with Supabase. Automatically:
 * - Requests token after successful login
 * - Saves token to repository for persistence
 * - Creates user profile in Supabase if not exists
 * - Loads and caches user profile data
 * - Clears profile data on logout
 *
 * @param authentication Authentication wrapper for login/logout operations
 * @param authRepository Repository for token persistence
 * @param userProfileRepository Repository for user profile data
 */
class AuthViewModel(
    private val authentication: Authentication,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val TAG = "AuthViewModel"

    val userState: StateFlow<UserState> = authentication.userStateFlow

    // Expose cached profile from repository
    val userProfile: StateFlow<kaist.iclab.mobiletracker.data.sensors.phone.ProfileData?> =
        userProfileRepository.profileFlow

    private var previousLoginState = false
    private var lastSavedToken: String? = null

    init {
        // Load profile if user is already logged in (e.g., app restart)
        viewModelScope.launch {
            if (userState.value.isLoggedIn) {
                loadUserProfile()
            }
        }

        // Observe userState changes to get token after login and save it automatically
        viewModelScope.launch {
            userState.collect { state ->
                val currentToken = state.token

                // When user successfully logs in, automatically get token
                if (state.isLoggedIn && !previousLoginState && currentToken == null) {
                    previousLoginState = true
                    authentication.getToken()
                }

                // When token becomes available, save it to repository and log it (only once per token)
                if (state.isLoggedIn && currentToken != null && currentToken != lastSavedToken) {
                    authRepository.saveToken(currentToken)
                    lastSavedToken = currentToken

                    // Save profile to profiles table if not exists, then load and cache it
                    saveProfileIfNotExists(state)
                    loadUserProfile()
                }

                // Clear profile when user logs out
                if (!state.isLoggedIn) {
                    userProfileRepository.clearProfile()
                    previousLoginState = false
                    lastSavedToken = null
                }
            }
        }
    }

    /**
     * Save user profile to profiles table if it doesn't exist
     */
    private suspend fun saveProfileIfNotExists(state: UserState) {
        val user = state.user
        if (user == null || user.email.isEmpty()) {
            return
        }

        userProfileRepository.createProfileIfNotExists(user.email, null)
            .onFailure { e ->
                Log.e(TAG, "Error saving profile: ${e.message}", e)
            }
    }

    /**
     * Load user profile from Supabase and cache it
     */
    private fun loadUserProfile() {
        viewModelScope.launch {
            userProfileRepository.refreshProfile()
                .onFailure { e ->
                    // Profile might not exist yet, which is okay
                    if (e !is NoSuchElementException) {
                        Log.e(TAG, "Error loading user profile: ${e.message}", e)
                    }
                }
        }
    }

    fun login(activity: Activity) {
        viewModelScope.launch {
            try {
                authentication.login(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.message}", e)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authentication.logout()
            authRepository.clearToken()
            userProfileRepository.clearProfile()
        }
    }

    /**
     * Refresh user profile from Supabase
     */
    fun refreshUserProfile() {
        loadUserProfile()
    }

    fun getToken() {
        viewModelScope.launch {
            authentication.getToken()
        }
    }
}
