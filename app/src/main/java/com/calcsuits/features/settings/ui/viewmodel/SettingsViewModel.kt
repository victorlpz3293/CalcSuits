package com.calcsuits.features.settings.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuits.data.local.datastore.AppSettings
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CalculatorRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setUseRadians(useRadians: Boolean) {
        viewModelScope.launch { repository.setUseRadians(useRadians) }
    }

    fun setDecimalPrecision(precision: Int) {
        viewModelScope.launch { repository.setDecimalPrecision(precision) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHapticEnabled(enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
