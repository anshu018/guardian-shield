package com.guardianshield.parent.domain.repository

import com.guardianshield.parent.domain.models.ChildLocation
import com.guardianshield.parent.domain.models.RemoteCommand
import com.guardianshield.parent.domain.models.SosEvent
import com.guardianshield.parent.domain.models.Child
import kotlinx.coroutines.flow.Flow

interface ParentRepository {
    fun observeChildren(): Flow<List<Child>>
    fun observeSosEvents(): Flow<SosEvent>
    suspend fun sendCommand(cmd: RemoteCommand): Result<Unit>
}
