package com.matejdro.wearvibrationcenter.notification;

import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.StatusBarNotification;

public class ProcessedNotification {
    private final StatusBarNotification contentNotification;
    private StatusBarNotification metadataNotification;
    private final SharedPreferences appPreferences;
    private boolean updateNotification;
    private boolean subsequentNotification;
    private String title;
    private String text;

    public ProcessedNotification(StatusBarNotification contentNotification, SharedPreferences appPreferences) {
        this.contentNotification = contentNotification;
        this.metadataNotification = contentNotification;
        this.appPreferences = appPreferences;
        updateNotification = false;
        subsequentNotification = false;
        text = "";
        title = "";
    }

    /**
     * @return  notification that contains content of the message
     */
    public StatusBarNotification getContentNotification() {
        return contentNotification;
    }

    public SharedPreferences getAppPreferences() {
        return appPreferences;
    }

    public boolean isUpdateNotification() {
        return updateNotification;
    }

    public void setUpdateNotification(boolean updateNotification) {
        this.updateNotification = updateNotification;
    }

    public boolean isSubsequentNotification() {
        return subsequentNotification;
    }

    public void setSubsequentNotification(boolean subsequentNotification) {
        this.subsequentNotification = subsequentNotification;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /**
     * @return  notification that contains the metadata of the message (vibration pattern etc.)
     */
    public StatusBarNotification getMetadataNotification() {
        return metadataNotification;
    }

    public void setMetadataNotification(StatusBarNotification metadataNotification) {
        this.metadataNotification = metadataNotification;
    }

    public boolean containsSameNotification(StatusBarNotification other) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return contentNotification.getKey().equals(other.getKey());
        } else {
            return (contentNotification.getPackageName().equals(other.getPackageName()) &&
                    (contentNotification.getId() == other.getId()) &&
                    ((contentNotification.getTag() != null && contentNotification.getTag().equals(other.getTag())) || (contentNotification.getTag() == null && other.getTag() == null)));
        }
    }
}
