package com.ehazir.companion.ui.navigation

import androidx.lifecycle.ViewModel
import com.ehazir.companion.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppPreferencesViewModel @Inject constructor(
    val prefs: AppPreferences
) : ViewModel()
