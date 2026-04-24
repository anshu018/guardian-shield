package com.guardianshield.parent.ui.livescreen;

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
public final class LiveScreenViewModel_Factory implements Factory<LiveScreenViewModel> {
  @Override
  public LiveScreenViewModel get() {
    return newInstance();
  }

  public static LiveScreenViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LiveScreenViewModel newInstance() {
    return new LiveScreenViewModel();
  }

  private static final class InstanceHolder {
    private static final LiveScreenViewModel_Factory INSTANCE = new LiveScreenViewModel_Factory();
  }
}
