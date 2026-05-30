package com.guardianshield.parent.domain.repository

import com.guardianshield.parent.domain.models.ChildLocation
import com.guardianshield.parent.domain.models.RemoteCommand
import com.guardianshield.parent.domain.models.SosEvent
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.CallLog
import com.guardianshield.parent.domain.models.SmsPreview
import com.guardianshield.parent.domain.models.ContactProfile
import kotlinx.coroutines.flow.Flow

interface ParentRepository {
    fun observeChildren(): Flow<List<Child>>
    fun observeSosEvents(): Flow<SosEvent>
    suspend fun sendCommand(cmd: RemoteCommand): Result<Unit>
    
    suspend fun fetchAndCacheFamilyId(): Result<String>
    fun observeChildLocationHistory(childId: String): Flow<List<ChildLocation>>
    
    fun observeCallLogs(childId: String, limit: Int = 50): Flow<List<CallLog>>
    fun observeSmsPreviews(childId: String, limit: Int = 50): Flow<List<SmsPreview>>
    fun observeContacts(childId: String): Flow<List<ContactProfile>>
}
