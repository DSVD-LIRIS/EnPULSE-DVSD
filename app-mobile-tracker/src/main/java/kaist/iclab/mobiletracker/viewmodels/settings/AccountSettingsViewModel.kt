package kaist.iclab.mobiletracker.viewmodels.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kaist.iclab.mobiletracker.R
import kaist.iclab.mobiletracker.data.campaign.CampaignData
import kaist.iclab.mobiletracker.repository.CampaignRepository
import kaist.iclab.mobiletracker.repository.Result
import kaist.iclab.mobiletracker.repository.SurveyRepository
import kaist.iclab.mobiletracker.repository.UserProfileRepository
import kaist.iclab.mobiletracker.repository.onFailure
import kaist.iclab.mobiletracker.repository.onSuccess
import kaist.iclab.mobiletracker.utils.AppToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountSettingsViewModel(
    private val campaignRepository: CampaignRepository,
    private val userProfileRepository: UserProfileRepository,
    private val surveyRepository: SurveyRepository,
    private val context: Context
) : ViewModel() {

    // Campaign state from repository
    val campaigns: StateFlow<List<CampaignData>> = campaignRepository.campaignsFlow

    private val _isLoadingCampaigns = MutableStateFlow(false)
    val isLoadingCampaigns: StateFlow<Boolean> = _isLoadingCampaigns.asStateFlow()

    private val _isSyncingSurveys = MutableStateFlow(false)
    val isSyncingSurveys: StateFlow<Boolean> = _isSyncingSurveys.asStateFlow()

    private val _campaignError = MutableStateFlow<String?>(null)
    val campaignError: StateFlow<String?> = _campaignError.asStateFlow()

    private val _selectedCampaignId = MutableStateFlow<String?>(null)
    val selectedCampaignId: StateFlow<String?> = _selectedCampaignId.asStateFlow()

    val selectedCampaignName: StateFlow<String?> = combine(
        _selectedCampaignId,
        campaigns
    ) { selectedId, campaignList ->
        selectedId?.toIntOrNull()?.let { id ->
            campaignList.find { it.id == id }?.name
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
                _selectedCampaignId.value = profile?.campaign_id?.toString()
            }
        }
    }

    fun fetchCampaigns() {
        if (campaigns.value.isNotEmpty() || _isLoadingCampaigns.value) return

        viewModelScope.launch {
            _isLoadingCampaigns.value = true
            _campaignError.value = null

            campaignRepository.fetchCampaigns()
                .onSuccess { _isLoadingCampaigns.value = false }
                .onFailure {
                    _campaignError.value = it.message
                    _isLoadingCampaigns.value = false
                }
        }
    }

    fun selectCampaign(campaignId: String) {
        _selectedCampaignId.value = campaignId
        viewModelScope.launch { saveCampaignToProfile(campaignId) }
    }

    private suspend fun saveCampaignToProfile(campaignId: String) {
        val campaignIdInt = campaignId.toIntOrNull() ?: return

        when (val result = userProfileRepository.updateCampaignId(campaignIdInt)) {
            is Result.Success -> {
                val surveyResult = surveyRepository.fetchAndPersistSurveys(campaignIdInt)
                _isSyncingSurveys.value = false

                userProfileRepository.refreshProfile()

                if (surveyResult.isSuccess) {
                    AppToast.show(context, R.string.toast_experiment_group_selected)
                } else {
                    AppToast.show(context, R.string.toast_experiment_group_selected_partial_error)
                }
            }

            is Result.Error -> {
                // Error handling if needed, or just let error classifier log it
            }
        }
    }

    suspend fun verifyPassword(campaignId: String, password: String): Boolean {
        return when (val result = campaignRepository.verifyPassword(campaignId, password)) {
            is Result.Success -> result.data
            is Result.Error -> false
        }
    }
}
