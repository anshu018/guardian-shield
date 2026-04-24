package com.guardianshield.child.services;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
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
public final class ServiceWatchdogWorker_Factory {
  public ServiceWatchdogWorker_Factory() {
  }

  public ServiceWatchdogWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params);
  }

  public static ServiceWatchdogWorker_Factory create() {
    return new ServiceWatchdogWorker_Factory();
  }

  public static ServiceWatchdogWorker newInstance(Context context, WorkerParameters params) {
    return new ServiceWatchdogWorker(context, params);
  }
}
