package com.guardianshield.parent.di

import android.content.Context
import com.guardianshield.parent.BuildConfig
import com.guardianshield.parent.data.local.ParentDataStore
import com.guardianshield.parent.data.remote.AuthRepository
import com.guardianshield.parent.data.remote.AuthRepositoryImpl
import com.guardianshield.parent.data.repository.ParentRepositoryImpl
import com.guardianshield.parent.domain.repository.ParentRepository
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
                host = "parent"
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

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}
