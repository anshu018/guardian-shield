package com.guardianshield.child;

import androidx.hilt.work.HiltWorkerFactory;
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
public final class GuardianChildApp_MembersInjector implements MembersInjector<GuardianChildApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public GuardianChildApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<GuardianChildApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new GuardianChildApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(GuardianChildApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.guardianshield.child.GuardianChildApp.workerFactory")
  public static void injectWorkerFactory(GuardianChildApp instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
