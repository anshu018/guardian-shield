package com.guardianshield.child.ui.splash;

import com.guardianshield.child.data.local.LocationDataStore;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SplashActivity_MembersInjector implements MembersInjector<SplashActivity> {
  private final Provider<LocationDataStore> dataStoreProvider;

  public SplashActivity_MembersInjector(Provider<LocationDataStore> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  public static MembersInjector<SplashActivity> create(
      Provider<LocationDataStore> dataStoreProvider) {
    return new SplashActivity_MembersInjector(dataStoreProvider);
  }

  @Override
  public void injectMembers(SplashActivity instance) {
    injectDataStore(instance, dataStoreProvider.get());
  }

  @InjectedFieldSignature("com.guardianshield.child.ui.splash.SplashActivity.dataStore")
  public static void injectDataStore(SplashActivity instance, LocationDataStore dataStore) {
    instance.dataStore = dataStore;
  }
}
