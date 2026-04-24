package com.guardianshield.child.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class LocationDataStore_Factory implements Factory<LocationDataStore> {
  private final Provider<Context> contextProvider;

  public LocationDataStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LocationDataStore get() {
    return newInstance(contextProvider.get());
  }

  public static LocationDataStore_Factory create(Provider<Context> contextProvider) {
    return new LocationDataStore_Factory(contextProvider);
  }

  public static LocationDataStore newInstance(Context context) {
    return new LocationDataStore(context);
  }
}
