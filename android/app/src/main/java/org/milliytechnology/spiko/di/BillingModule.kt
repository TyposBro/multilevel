package org.milliytechnology.spiko.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.milliytechnology.spiko.features.billing.BillingClientWrapper
import org.milliytechnology.spiko.features.billing.BillingClientWrapperImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindBillingClientWrapper(
        impl: BillingClientWrapperImpl
    ): BillingClientWrapper
}