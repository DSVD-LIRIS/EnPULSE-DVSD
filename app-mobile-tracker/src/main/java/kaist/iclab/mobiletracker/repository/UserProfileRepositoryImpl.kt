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

    override suspend fun updateCampaignId(campaignId: Int): kotlin.Result<Unit> {
        val uuid = getCurrentUuid()
            ?: return kotlin.Result.failure(Exception("User not logged in"))

        return when (val result = profileService.updateCampaignId(uuid, campaignId)) {
            is Result.Success -> kotlin.Result.success(Unit)
            is Result.Error -> kotlin.Result.failure(result.exception ?: Exception(result.message))
        }
    }

    override suspend fun refreshProfile(): kotlin.Result<ProfileData> {
        val uuid = getCurrentUuid()
            ?: return kotlin.Result.failure(Exception("User not logged in"))

        return when (val result = profileService.getProfileByUuid(uuid)) {
            is Result.Success -> {
                _profile.value = result.data
                kotlin.Result.success(result.data)
            }

            is Result.Error -> kotlin.Result.failure(result.exception ?: Exception(result.message))
        }
    }

    override suspend fun createProfileIfNotExists(
        email: String,
        campaignId: Int?
    ): kotlin.Result<Unit> {
        val uuid = getCurrentUuid()
            ?: return kotlin.Result.failure(Exception("User not logged in"))

        return when (val result =
            profileService.createProfileIfNotExists(uuid, email, campaignId)) {
            is Result.Success -> kotlin.Result.success(Unit)
            is Result.Error -> kotlin.Result.failure(result.exception ?: Exception(result.message))
        }
    }
}

