package org.milliytechnology.spiko.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.milliytechnology.spiko.features.billing.BillingClientWrapper
import org.milliytechnology.spiko.features.billing.FakeBillingClientWrapper
import javax.inject.Singleton

// THIS MODULE IS FOR DEBUG BUILDS ONLY.
// It replaces the real BillingModule because it has the same name and is in the 'debug' source set.
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    // This tells Hilt: "For debug builds, when someone needs a BillingClientWrapper,
    // provide them with an instance of the FAKE one."
    @Binds
    @Singleton
    abstract fun bindBillingClientWrapper(
        impl: FakeBillingClientWrapper
    ): BillingClientWrapper
}