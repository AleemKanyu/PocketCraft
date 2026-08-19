package com.pockethost.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pockethost.app.PocketHostApp
import com.pockethost.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        PocketHostApp.applicationScope.launch {
            val prefs = AppPreferences(context)
            val completed = prefs.onboardingCompleted
            withContext(Dispatchers.Main.immediate) {
                _onboardingCompleted.value = completed
            }
        }
    }
    
    fun completeOnboarding() {
        PocketHostApp.applicationScope.launch {
            val prefs = AppPreferences(context)
            prefs.onboardingCompleted = true
            withContext(Dispatchers.Main.immediate) {
                _onboardingCompleted.value = true
            }
        }
    }
}
