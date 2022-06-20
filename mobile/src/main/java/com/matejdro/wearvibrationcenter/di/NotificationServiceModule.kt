package com.matejdro.wearvibrationcenter.di

import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import com.matejdro.wearvibrationcenter.notification.NotificationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn

@Module
@InstallIn(NotificationServiceComponent::class)
abstract class NotificationServiceModule {
    @Binds
    abstract fun bindNotificationListenerService(notificationService: NotificationService): NotificationListenerService

    companion object {
        @Provides
        fun provideSharedPreferences(notificationService: NotificationService): SharedPreferences {
            return notificationService.globalSettings
        }
    }
}
