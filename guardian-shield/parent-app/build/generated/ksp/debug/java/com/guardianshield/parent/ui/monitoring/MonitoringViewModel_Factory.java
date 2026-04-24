package com.guardianshield.parent.ui.monitoring;

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
public final class MonitoringViewModel_Factory implements Factory<MonitoringViewModel> {
  @Override
  public MonitoringViewModel get() {
    return newInstance();
  }

  public static MonitoringViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MonitoringViewModel newInstance() {
    return new MonitoringViewModel();
  }

  private static final class InstanceHolder {
    private static final MonitoringViewModel_Factory INSTANCE = new MonitoringViewModel_Factory();
  }
}
