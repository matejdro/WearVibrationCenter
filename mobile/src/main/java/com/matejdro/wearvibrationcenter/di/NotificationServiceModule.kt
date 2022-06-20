package com.matejdro.wearvibrationcenter.di

import android.service.notification.NotificationListenerService
import com.matejdro.wearvibrationcenter.notification.NotificationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn

@Module
@InstallIn(NotificationServiceComponent::class)
abstract class NotificationServiceModule {
    @Binds
    abstract fun bindNotificationListenerService(notificationService: NotificationService): NotificationListenerService
}
