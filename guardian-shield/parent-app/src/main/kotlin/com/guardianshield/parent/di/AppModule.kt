package com.guardianshield.parent.di

import android.content.Context
import com.guardianshield.parent.BuildConfig
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.data.repository.ParentRepositoryImpl
import com.guardianshield.parent.domain.repository.ParentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Realtime)
            install(Auth) {
                scheme = "guardianshield"
                host = "auth"
            }
        }
    }

    @Provides
    @Singleton
    fun provideParentDataStore(
        @ApplicationContext context: Context
    ): ParentDataStore = ParentDataStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindParentRepository(
        impl: ParentRepositoryImpl
    ): ParentRepository
}
