package com.guardianshield.child.ui.setup;

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
public final class SetupViewModel_Factory implements Factory<SetupViewModel> {
  @Override
  public SetupViewModel get() {
    return newInstance();
  }

  public static SetupViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SetupViewModel newInstance() {
    return new SetupViewModel();
  }

  private static final class InstanceHolder {
    private static final SetupViewModel_Factory INSTANCE = new SetupViewModel_Factory();
  }
}
