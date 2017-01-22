package com.matejdro.wearvibrationcenter.common;

import android.os.Parcel;
import android.os.Parcelable;

public class TimedMuteCommand implements Parcelable {
    private final int muteDurationMinutes;

    public TimedMuteCommand(int muteDurationMinutes) {
        this.muteDurationMinutes = muteDurationMinutes;
    }

    public int getMuteDurationMinutes() {
        return muteDurationMinutes;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.muteDurationMinutes);
    }

    protected TimedMuteCommand(Parcel in) {
        this.muteDurationMinutes = in.readInt();
    }

    public static final Parcelable.Creator<TimedMuteCommand> CREATOR = new Parcelable.Creator<TimedMuteCommand>() {
        @Override
        public TimedMuteCommand createFromParcel(Parcel source) {
            return new TimedMuteCommand(source);
        }

        @Override
        public TimedMuteCommand[] newArray(int size) {
            return new TimedMuteCommand[size];
        }
    };
}
