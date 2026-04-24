package com.guardianshield.parent.ui.controls;

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
public final class ControlsViewModel_Factory implements Factory<ControlsViewModel> {
  @Override
  public ControlsViewModel get() {
    return newInstance();
  }

  public static ControlsViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ControlsViewModel newInstance() {
    return new ControlsViewModel();
  }

  private static final class InstanceHolder {
    private static final ControlsViewModel_Factory INSTANCE = new ControlsViewModel_Factory();
  }
}
