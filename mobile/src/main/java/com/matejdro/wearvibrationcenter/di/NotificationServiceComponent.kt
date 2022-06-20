package com.matejdro.wearvibrationcenter.di

import com.matejdro.wearvibrationcenter.mute.AppMuteManager
import com.matejdro.wearvibrationcenter.mute.TimedMuteManager
import com.matejdro.wearvibrationcenter.notification.NotificationProcessor
import com.matejdro.wearvibrationcenter.notification.NotificationService
import com.matejdro.wearvibrationcenter.notificationprovider.NotificationBroadcaster
import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent

@DefineComponent(parent = ServiceComponent::class)
interface NotificationServiceComponent {
    @DefineComponent.Builder
    interface Builder {
        fun service(@BindsInstance notificationService: NotificationService): Builder
        fun build(): NotificationServiceComponent
    }
}

@EntryPoint
@InstallIn(NotificationServiceComponent::class)
interface NotificationServiceEntryPoint {
    fun createNotificationProcessor(): NotificationProcessor
    fun createTimedMuteManager(): TimedMuteManager
    fun createAppMuteManager(): AppMuteManager
    fun createNotificationBroadcaster(): NotificationBroadcaster
}
