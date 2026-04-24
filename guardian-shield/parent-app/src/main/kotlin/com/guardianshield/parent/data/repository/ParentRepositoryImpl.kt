package com.guardianshield.parent.data.repository

import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.RemoteCommand
import com.guardianshield.parent.domain.models.SosEvent
import com.guardianshield.parent.domain.repository.ParentRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ParentRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val dataStore: ParentDataStore
) : ParentRepository {

    override fun observeChildren(): Flow<List<Child>> = flow {
        // TODO: Supabase realtime
    }

    override fun observeSosEvents(): Flow<SosEvent> = flow {
        // TODO: Supabase realtime
    }

    override suspend fun sendCommand(cmd: RemoteCommand): Result<Unit> {
        return Result.success(Unit)
    }
}
