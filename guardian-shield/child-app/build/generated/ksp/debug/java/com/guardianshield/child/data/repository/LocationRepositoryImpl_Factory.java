package com.guardianshield.child.data.repository;

import com.guardianshield.child.data.local.LocationDataStore;
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
public final class LocationRepositoryImpl_Factory implements Factory<LocationRepositoryImpl> {
  private final Provider<SupabaseClient> supabaseClientProvider;

  private final Provider<LocationDataStore> locationDataStoreProvider;

  public LocationRepositoryImpl_Factory(Provider<SupabaseClient> supabaseClientProvider,
      Provider<LocationDataStore> locationDataStoreProvider) {
    this.supabaseClientProvider = supabaseClientProvider;
    this.locationDataStoreProvider = locationDataStoreProvider;
  }

  @Override
  public LocationRepositoryImpl get() {
    return newInstance(supabaseClientProvider.get(), locationDataStoreProvider.get());
  }

  public static LocationRepositoryImpl_Factory create(
      Provider<SupabaseClient> supabaseClientProvider,
      Provider<LocationDataStore> locationDataStoreProvider) {
    return new LocationRepositoryImpl_Factory(supabaseClientProvider, locationDataStoreProvider);
  }

  public static LocationRepositoryImpl newInstance(SupabaseClient supabaseClient,
      LocationDataStore locationDataStore) {
    return new LocationRepositoryImpl(supabaseClient, locationDataStore);
  }
}
