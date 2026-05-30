package com.guardianshield.parent.ui.location

import androidx.lifecycle.ViewModel
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val parentRepository: ParentRepository,
    private val parentDataStore: ParentDataStore
) : ViewModel() {
}
