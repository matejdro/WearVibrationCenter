package com.matejdro.wearvibrationcenter.common;

import android.os.Parcel;
import android.os.Parcelable;

public class VibrationCommand implements Parcelable, InterruptionCommand {
    private final long[] pattern;
    private final boolean doNotVibrateInTheater;
    private final boolean doNotVibrateOnCharger;

    public VibrationCommand(long[] pattern, boolean doNotVibrateInTheater, boolean doNotVibrateOnCharger) {
        this.pattern = pattern;
        this.doNotVibrateInTheater = doNotVibrateInTheater;
        this.doNotVibrateOnCharger = doNotVibrateOnCharger;
    }

    public long[] getPattern() {
        return pattern;
    }

    public boolean shouldNotVibrateInTheater() {
        return doNotVibrateInTheater;
    }

    public boolean shouldNotVibrateOnCharger() {
        return doNotVibrateOnCharger;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLongArray(this.pattern);
        dest.writeByte(this.doNotVibrateInTheater ? (byte) 1 : (byte) 0);
        dest.writeByte(this.doNotVibrateOnCharger ? (byte) 1 : (byte) 0);
    }

    protected VibrationCommand(Parcel in) {
        this.pattern = in.createLongArray();
        this.doNotVibrateInTheater = in.readByte() != 0;
        this.doNotVibrateOnCharger = in.readByte() != 0;
    }

    public static final Creator<VibrationCommand> CREATOR = new Creator<VibrationCommand>() {
        @Override
        public VibrationCommand createFromParcel(Parcel source) {
            return new VibrationCommand(source);
        }

        @Override
        public VibrationCommand[] newArray(int size) {
            return new VibrationCommand[size];
        }
    };
}
