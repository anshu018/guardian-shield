package com.guardianshield.child.domain.usecases;

import com.guardianshield.child.domain.repository.LocationRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class UploadLocationUseCase_Factory implements Factory<UploadLocationUseCase> {
  private final Provider<LocationRepository> locationRepositoryProvider;

  public UploadLocationUseCase_Factory(Provider<LocationRepository> locationRepositoryProvider) {
    this.locationRepositoryProvider = locationRepositoryProvider;
  }

  @Override
  public UploadLocationUseCase get() {
    return newInstance(locationRepositoryProvider.get());
  }

  public static UploadLocationUseCase_Factory create(
      Provider<LocationRepository> locationRepositoryProvider) {
    return new UploadLocationUseCase_Factory(locationRepositoryProvider);
  }

  public static UploadLocationUseCase newInstance(LocationRepository locationRepository) {
    return new UploadLocationUseCase(locationRepository);
  }
}
