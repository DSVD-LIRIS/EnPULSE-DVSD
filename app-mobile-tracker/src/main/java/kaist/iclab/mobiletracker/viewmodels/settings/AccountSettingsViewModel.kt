package kaist.iclab.mobiletracker.viewmodels.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.campaign.CampaignData
import kaist.iclab.mobiletracker.helpers.SupabaseHelper
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.repository.SurveyRepository
import kaist.iclab.mobiletracker.repository.UserProfileRepository
import kaist.iclab.mobiletracker.services.CampaignService
import kaist.iclab.mobiletracker.services.ProfileService
import kaist.iclab.mobiletracker.utils.AppToast
import kaist.iclab.mobiletracker.utils.SupabaseSessionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountSettingsViewModel(
    private val campaignService: CampaignService,
    private val profileService: ProfileService,
    private val supabaseHelper: SupabaseHelper,
    private val userProfileRepository: UserProfileRepository,
    private val surveyRepository: SurveyRepository,
    private val context: Context
) : ViewModel() {

    // Campaign state
    private val _campaigns = MutableStateFlow<List<CampaignData>>(emptyList())
    val campaigns: StateFlow<List<CampaignData>> = _campaigns.asStateFlow()

    private val _isLoadingCampaigns = MutableStateFlow(false)
    val isLoadingCampaigns: StateFlow<Boolean> = _isLoadingCampaigns.asStateFlow()

    // Survey sync loading state
    private val _isSyncingSurveys = MutableStateFlow(false)
    val isSyncingSurveys: StateFlow<Boolean> = _isSyncingSurveys.asStateFlow()

    private val _campaignError = MutableStateFlow<String?>(null)
    val campaignError: StateFlow<String?> = _campaignError.asStateFlow()

    // Selected campaign ID (stored as String for UI compatibility)
    private val _selectedCampaignId = MutableStateFlow<String?>(null)
    val selectedCampaignId: StateFlow<String?> = _selectedCampaignId.asStateFlow()

    // Selected campaign name (reactively computed from selectedCampaignId and campaigns)
    val selectedCampaignName: StateFlow<String?> = combine(
        _selectedCampaignId,
        _campaigns
    ) { selectedId, campaigns ->
        if (selectedId == null) {
            null
        } else {
            val campaignIdInt = selectedId.toIntOrNull()
            campaignIdInt?.let { id ->
                campaigns.find { it.id == id }?.name
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        fetchCampaigns()

        viewModelScope.launch {
            userProfileRepository.profileFlow.collect { profile ->
                profile?.campaign_id?.let { campaignId ->
                    _selectedCampaignId.value = campaignId.toString()
                } ?: run {
                    _selectedCampaignId.value = null
                }
            }
        }
    }

    /**
     * Fetch all campaigns from Supabase
     */
    fun fetchCampaigns() {
        if (_campaigns.value.isNotEmpty() || _isLoadingCampaigns.value) {
            return
        }

        viewModelScope.launch {
            _isLoadingCampaigns.value = true
            _campaignError.value = null

            when (val result = campaignService.getAllCampaigns()) {
                is Result.Success -> {
                    _campaigns.value = result.data
                    _isLoadingCampaigns.value = false
                }

                is Result.Error -> {
                    _campaignError.value = result.message
                    _isLoadingCampaigns.value = false
                }
            }
        }
    }

    /**
     * Select a campaign by ID and save it to the user's profile
     */
    fun selectCampaign(campaignId: String) {
        _selectedCampaignId.value = campaignId

        viewModelScope.launch {
            saveCampaignToProfile(campaignId)
        }
    }

    /**
     * Save the selected campaign to the user's profile
     */
    private suspend fun saveCampaignToProfile(campaignId: String) {
        val uuid = getUuidFromSession() ?: return
        val campaignIdInt = campaignId.toIntOrNull() ?: return

        when (val result = profileService.updateCampaignId(uuid, campaignIdInt)) {
            is Result.Success -> {
                // Fetch and persist surveys via repository
                _isSyncingSurveys.value = true
                surveyRepository.fetchAndPersistSurveys(campaignIdInt)
                _isSyncingSurveys.value = false
                
                refreshUserProfile()
                AppToast.show(context, R.string.toast_experiment_group_selected)
            }

            is Result.Error -> {
                // Campaign save failed
            }
        }
    }

    private fun getUuidFromSession(): String? {
        return SupabaseSessionHelper.getUuidOrNull(supabaseHelper.supabaseClient)
    }

    private fun refreshUserProfile() {
        viewModelScope.launch {
            val uuid = getUuidFromSession() ?: return@launch

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
}
