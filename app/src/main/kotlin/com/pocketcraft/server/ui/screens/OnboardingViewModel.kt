package com.pocketcraft.server.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.server.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted
    
    init {
        checkOnboardingStatus()
    }
    
    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val prefs = AppPreferences(context)
            _onboardingCompleted.value = prefs.onboardingCompleted
        }
    }
    
    fun completeOnboarding() {
        viewModelScope.launch {
            val prefs = AppPreferences(context)
            prefs.onboardingCompleted = true
            _onboardingCompleted.value = true
        }
    }
}
