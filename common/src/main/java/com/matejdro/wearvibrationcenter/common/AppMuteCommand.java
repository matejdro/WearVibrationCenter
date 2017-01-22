package com.matejdro.wearvibrationcenter.common;

import android.os.Parcel;
import android.os.Parcelable;

public class AppMuteCommand implements Parcelable {
    private final int appIndex;

    public AppMuteCommand(int appIndex) {
        this.appIndex = appIndex;
    }

    public int getAppIndex() {
        return appIndex;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.appIndex);
    }

    protected AppMuteCommand(Parcel in) {
        this.appIndex = in.readInt();
    }

    public static final Parcelable.Creator<AppMuteCommand> CREATOR = new Parcelable.Creator<AppMuteCommand>() {
        @Override
        public AppMuteCommand createFromParcel(Parcel source) {
            return new AppMuteCommand(source);
        }

        @Override
        public AppMuteCommand[] newArray(int size) {
            return new AppMuteCommand[size];
        }
    };
}
