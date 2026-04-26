package com.guardianshield.child.di

import android.content.Context
import com.guardianshield.child.BuildConfig
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.repository.LocationRepositoryImpl
import com.guardianshield.child.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
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
            install(Auth) {
                scheme = "guardianshield"
                host = "child"
                // Auto-saves session to DataStore â€” child services need this
                // to authenticate Supabase calls without re-login
            }
            install(Postgrest)
            install(Realtime)
        }
    }

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest =
        client.postgrest

    @Provides
    @Singleton
    fun provideRealtime(client: SupabaseClient): Realtime =
        client.realtime

    @Provides
    @Singleton
    fun provideLocationDataStore(
        @ApplicationContext context: Context
    ): LocationDataStore = LocationDataStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository
}
