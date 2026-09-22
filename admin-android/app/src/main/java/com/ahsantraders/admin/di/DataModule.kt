package com.ahsantraders.admin.di

import android.content.Context
import com.ahsantraders.admin.BuildConfig
import com.ahsantraders.admin.data.AdminRepository
import com.ahsantraders.admin.data.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        SessionStore(context)

    @Provides
    @Singleton
    fun provideAdminRepository(store: SessionStore): AdminRepository =
        AdminRepository(store, BuildConfig.DEBUG)
}
