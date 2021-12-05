package com.matejdro.wearvibrationcenter.notificationprovider;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;

import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearvibrationcenter.notification.NotificationUtils;
import com.matejdro.wearvibrationcenter.notification.ProcessedNotification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import timber.log.Timber;

public class NotificationBroadcastMediator extends Service {
    private MediatorBinder senderBinder;
    private List<INotificationListener> listeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        senderBinder = new MediatorBinder();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (NotificationProviderConstants.ACTION_NOTIFICATION_PROVIDER.equals(intent.getAction())) {
            return notificationProvider;
        } else if (NotificationProviderConstants.ACTION_NOTIFICATION_SENDER.equals(intent.getAction())) {
            return senderBinder;
        }

        return null;
    }

    public void onNewNotification(ProcessedNotification notification) {
        if (listeners.isEmpty()) {
            return;
        }

        Bitmap background = NotificationUtils.getNotificationLargeIcon(this, notification.getContentNotification());
        if (background == null) {
            background = NotificationUtils.getNotificationIcon(this, notification.getMetadataNotification());
        }
        background = BitmapUtils.shrinkPreservingRatio(background, 400, 400);

        ReceivedNotification receivedNotification = new ReceivedNotification(notification.getTitle(),
                notification.getText(),
                BitmapUtils.serialize(background));

        for (Iterator<INotificationListener> i = listeners.iterator(); i.hasNext(); ) {
            INotificationListener listener = i.next();

            try {
                listener.onNotificationReceived(receivedNotification);
            } catch (DeadObjectException e) {
                // Connection has died. Remove listener from the list
                i.remove();
            } catch (RemoteException e) {
                Timber.e(e, "Notification sending error");
            }
        }
    }

    private INotificationProvider.Stub notificationProvider = new INotificationProvider.Stub() {
        @Override
        public void startSendingNotifications(INotificationListener listener) throws RemoteException {
            listeners.add(listener);
        }
    };

    public class MediatorBinder extends Binder {
        public NotificationBroadcastMediator getMediator() {
            return NotificationBroadcastMediator.this;
        }
    }
}
