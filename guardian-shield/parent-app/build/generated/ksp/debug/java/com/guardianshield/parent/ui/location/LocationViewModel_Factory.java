package com.guardianshield.parent.ui.location;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class LocationViewModel_Factory implements Factory<LocationViewModel> {
  @Override
  public LocationViewModel get() {
    return newInstance();
  }

  public static LocationViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LocationViewModel newInstance() {
    return new LocationViewModel();
  }

  private static final class InstanceHolder {
    private static final LocationViewModel_Factory INSTANCE = new LocationViewModel_Factory();
  }
}
