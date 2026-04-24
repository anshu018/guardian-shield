package com.guardianshield.parent.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ParentDataStore_Factory implements Factory<ParentDataStore> {
  private final Provider<Context> contextProvider;

  public ParentDataStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ParentDataStore get() {
    return newInstance(contextProvider.get());
  }

  public static ParentDataStore_Factory create(Provider<Context> contextProvider) {
    return new ParentDataStore_Factory(contextProvider);
  }

  public static ParentDataStore newInstance(Context context) {
    return new ParentDataStore(context);
  }
}
