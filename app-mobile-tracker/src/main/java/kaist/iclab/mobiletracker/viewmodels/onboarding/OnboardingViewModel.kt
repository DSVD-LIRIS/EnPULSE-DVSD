package kaist.iclab.mobiletracker.viewmodels.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kaist.iclab.mobiletracker.data.campaign.CampaignData
import kaist.iclab.mobiletracker.data.survey.SurveyConfig
import kaist.iclab.mobiletracker.helpers.SupabaseHelper
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.repository.SurveyRepository
import kaist.iclab.mobiletracker.repository.UserProfileRepository
import kaist.iclab.mobiletracker.services.CampaignService
import kaist.iclab.mobiletracker.services.ProfileService
import kaist.iclab.mobiletracker.utils.SupabaseSessionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for OnboardingScreen
 */
data class OnboardingUiState(
    val campaigns: List<CampaignData> = emptyList(),
    val selectedCampaign: CampaignData? = null,
    val surveyConfigs: List<SurveyConfig> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false
)

/**
 * ViewModel for handling campaign onboarding flow
 */
class OnboardingViewModel(
    private val campaignService: CampaignService,
    private val profileService: ProfileService,
    private val supabaseHelper: SupabaseHelper,
    private val userProfileRepository: UserProfileRepository,
    private val surveyRepository: SurveyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Load available campaigns from Supabase
     */
    fun loadCampaigns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = campaignService.getAllCampaigns()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            campaigns = result.data,
                            isLoading = false
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Select a campaign
     */
    fun selectCampaign(campaign: CampaignData) {
        _uiState.update { it.copy(selectedCampaign = campaign) }
    }

    /**
     * Confirm selection and save to profile
     */
    fun confirmSelection() {
        val selectedCampaign = _uiState.value.selectedCampaign ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val uuid = getUuidFromSession()
            if (uuid == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Unable to get user session")
                }
                return@launch
            }

            when (val result = profileService.updateCampaignId(uuid, selectedCampaign.id)) {
                is Result.Success -> {
                    // Fetch and persist surveys via repository
                    val surveyResult = surveyRepository.fetchAndPersistSurveys(selectedCampaign.id)
                    
                    // Refresh user profile to trigger navigation
                    refreshUserProfile(uuid)
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isComplete = true,
                            error = if (surveyResult.isFailure) "Campaign saved, but failed to load surveys" else null
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun getUuidFromSession(): String? {
        return SupabaseSessionHelper.getUuidOrNull(supabaseHelper.supabaseClient)
    }

    private suspend fun refreshUserProfile(uuid: String) {
        when (val result = profileService.getProfileByUuid(uuid)) {
            is Result.Success -> {
                userProfileRepository.saveProfile(result.data)
            }

            is Result.Error -> {
                // Error refreshing profile
            }
        }
    }
}
