package com.meteocompare.app.di

import com.meteocompare.app.ui.home.DataStoreSelectedCityStore
import com.meteocompare.app.ui.home.SelectedCityStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindSelectedCityStore(impl: DataStoreSelectedCityStore): SelectedCityStore
}
