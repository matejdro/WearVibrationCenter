package com.matejdro.wearvibrationcenter.notification;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;

import com.matejdro.wearutils.miscutils.BitmapUtils;

public class NotificationUtils {
    public
    @Nullable
    static Bitmap getNotificationIcon(Context context, StatusBarNotification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon icon = notification.getNotification().getSmallIcon();
            if (icon == null) {
                return null;
            }

            return BitmapUtils.getBitmap(context, icon);
        } else {
            @SuppressWarnings("deprecation")
            int iconId = notification.getNotification().icon;
            try {
                Resources sourceAppResources = context.getPackageManager().getResourcesForApplication(notification.getPackageName());
                return BitmapUtils.getBitmap(ResourcesCompat.getDrawable(sourceAppResources, iconId, null));
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static Bitmap getNotificationBackgroundImage(Context context, StatusBarNotification notification) {
        Bundle extras = NotificationCompat.getExtras(notification.getNotification());
        if (extras != null) {
            //Extract image from BigPictureStyle notification style
            Bitmap bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_PICTURE));
            if (bitmap != null) {
                return bitmap;
            }

            //Extract image from Wearable extender background
            if (extras.containsKey("android.wearable.EXTENSIONS")) {
                Bundle wearableExtension = extras.getBundle("android.wearable.EXTENSIONS");
                bitmap = BitmapUtils.getBitmap(context, wearableExtension.getParcelable("background"));
                if (bitmap != null) {
                    return bitmap;
                }
            }

            //Extract image from Car extender large icon
            if (extras.containsKey("android.car.EXTENSIONS")) {
                Bundle carExtensions = extras.getBundle("android.car.EXTENSIONS");
                bitmap = BitmapUtils.getBitmap(context, carExtensions.getParcelable("large_icon"));
                if (bitmap != null) {
                    return bitmap;
                }
            }

            //Extract image from large icon on android notification
            bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON_BIG));
            if (bitmap != null) {
                return bitmap;
            }

            bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON));
            if (bitmap != null) {
                return bitmap;
            }
        }

        return null;
    }

    @SuppressWarnings("ConstantConditions")
    public static Bitmap getNotificationLargeIcon(Context context, StatusBarNotification notification) {
        Bundle extras = NotificationCompat.getExtras(notification.getNotification());
        if (extras != null) {
            //Extract image from BigPictureStyle notification style
            Bitmap bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_PICTURE));
            if (bitmap != null) {
                return bitmap;
            }

            //Extract image from large icon on android notification
            bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON_BIG));
            if (bitmap != null) {
                return bitmap;
            }

            bitmap = BitmapUtils.getBitmap(context, extras.getParcelable(NotificationCompat.EXTRA_LARGE_ICON));
            if (bitmap != null) {
                return bitmap;
            }
        }

        return null;
    }

}
