package com.guardianshield.child.services;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ServiceWatchdogWorker_AssistedFactory_Impl implements ServiceWatchdogWorker_AssistedFactory {
  private final ServiceWatchdogWorker_Factory delegateFactory;

  ServiceWatchdogWorker_AssistedFactory_Impl(ServiceWatchdogWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public ServiceWatchdogWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<ServiceWatchdogWorker_AssistedFactory> create(
      ServiceWatchdogWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ServiceWatchdogWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<ServiceWatchdogWorker_AssistedFactory> createFactoryProvider(
      ServiceWatchdogWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ServiceWatchdogWorker_AssistedFactory_Impl(delegateFactory));
  }
}
