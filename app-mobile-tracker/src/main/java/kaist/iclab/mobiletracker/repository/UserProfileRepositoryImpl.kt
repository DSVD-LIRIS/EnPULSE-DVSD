package kaist.iclab.mobiletracker.repository

import kaist.iclab.mobiletracker.data.sensors.phone.ProfileData
import kaist.iclab.mobiletracker.helpers.SupabaseHelper
import kaist.iclab.mobiletracker.services.ProfileService
import kaist.iclab.mobiletracker.utils.SupabaseSessionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of UserProfileRepository.
 * Manages user profile caching and remote operations via ProfileService.
 */
class UserProfileRepositoryImpl(
    private val profileService: ProfileService,
    private val supabaseHelper: SupabaseHelper
) : UserProfileRepository {

    companion object {
        private const val TAG = "UserProfileRepo"
    }

    private val _profile = MutableStateFlow<ProfileData?>(null)
    override val profileFlow: StateFlow<ProfileData?> = _profile.asStateFlow()

    override fun getCurrentUuid(): String? {
        return SupabaseSessionHelper.getUuidOrNull(supabaseHelper.supabaseClient)
    }

    override fun saveProfile(profile: ProfileData) {
        _profile.value = profile
    }

    override fun clearProfile() {
        _profile.value = null
    }

    override suspend fun updateCampaignId(campaignId: Int): Result<Unit> {
        val uuid = getCurrentUuid()
            ?: return Result.Error(AppError.Unknown("User not logged in"))
        return profileService.updateCampaignId(uuid, campaignId)
    }

    override suspend fun refreshProfile(): Result<ProfileData?> {
        val uuid = getCurrentUuid()
            ?: return Result.Error(AppError.Unknown("User not logged in"))

        return when (val result = profileService.getProfileByUuid(uuid)) {
            is Result.Success -> {
                _profile.value = result.data
                result
            }
            is Result.Error -> result
        }
    }

    override suspend fun createProfileIfNotExists(
        email: String,
        campaignId: Int?
    ): Result<Unit> {
        val uuid = getCurrentUuid()
            ?: return Result.Error(AppError.Unknown("User not logged in"))
        return profileService.createProfileIfNotExists(uuid, email, campaignId)
    }
}

