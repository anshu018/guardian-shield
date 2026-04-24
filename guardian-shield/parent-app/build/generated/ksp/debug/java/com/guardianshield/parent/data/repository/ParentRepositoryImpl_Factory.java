package com.guardianshield.parent.data.repository;

import com.guardianshield.parent.data.local.ParentDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.github.jan.supabase.SupabaseClient;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class ParentRepositoryImpl_Factory implements Factory<ParentRepositoryImpl> {
  private final Provider<SupabaseClient> supabaseClientProvider;

  private final Provider<ParentDataStore> dataStoreProvider;

  public ParentRepositoryImpl_Factory(Provider<SupabaseClient> supabaseClientProvider,
      Provider<ParentDataStore> dataStoreProvider) {
    this.supabaseClientProvider = supabaseClientProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public ParentRepositoryImpl get() {
    return newInstance(supabaseClientProvider.get(), dataStoreProvider.get());
  }

  public static ParentRepositoryImpl_Factory create(Provider<SupabaseClient> supabaseClientProvider,
      Provider<ParentDataStore> dataStoreProvider) {
    return new ParentRepositoryImpl_Factory(supabaseClientProvider, dataStoreProvider);
  }

  public static ParentRepositoryImpl newInstance(SupabaseClient supabaseClient,
      ParentDataStore dataStore) {
    return new ParentRepositoryImpl(supabaseClient, dataStore);
  }
}
