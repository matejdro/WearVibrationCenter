package com.matejdro.wearvibrationcenter.notificationprovider;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.matejdro.wearvibrationcenter.notification.ProcessedNotification;

public class NotificationBroadcaster {
    private Context context;

    private NotificationBroadcastMediator mediator;

    public NotificationBroadcaster(Context context) {
        this.context = context;

        Intent intent = new Intent();
        intent.setComponent(NotificationProviderConstants.TARGET_COMPONENT);
        intent.setAction(NotificationProviderConstants.ACTION_NOTIFICATION_SENDER);

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void onDestroy() {
        context.unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediator = ((NotificationBroadcastMediator.MediatorBinder) service).getMediator();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediator = null;
        }
    };

    public void onNewNotification(ProcessedNotification notification) {
        if (mediator != null) {
            mediator.onNewNotification(notification);
        }
    }
}
