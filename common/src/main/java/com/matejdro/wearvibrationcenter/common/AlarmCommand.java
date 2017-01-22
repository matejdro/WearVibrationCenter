package com.matejdro.wearvibrationcenter.common;

import android.os.Parcel;
import android.os.Parcelable;

public class AlarmCommand implements Parcelable, InterruptionCommand {
    private final String text;
    private final long[] vibrationPattern;
    private final byte[] backgroundBitmap;
    private final byte[] icon;
    private final int snoozeDuration;
    private final boolean doNotVibrateInTheater;
    private final boolean doNotVibrateOnCharger;

    public AlarmCommand(LiteAlarmCommand liteAlarmCommand, byte[] backgroundBitmap, byte[] icon) {
        this(liteAlarmCommand.getText(),
                liteAlarmCommand.getVibrationPattern(),
                backgroundBitmap,
                icon,
                liteAlarmCommand.getSnoozeDuration(),
                liteAlarmCommand.shouldNotVibrateInTheater(),
                liteAlarmCommand.shouldNotVibrateOnCharger());
    }

    public AlarmCommand(String text, long[] vibrationPattern, byte[] backgroundBitmap, byte[] icon, int snoozeDuration, boolean doNotVibrateInTheater, boolean doNotVibrateOnCharger) {
        this.text = text;
        this.vibrationPattern = vibrationPattern;
        this.backgroundBitmap = backgroundBitmap;
        this.icon = icon;
        this.snoozeDuration = snoozeDuration;
        this.doNotVibrateInTheater = doNotVibrateInTheater;
        this.doNotVibrateOnCharger = doNotVibrateOnCharger;
    }

    public String getText() {
        return text;
    }

    public byte[] getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public boolean shouldNotVibrateInTheater() {
        return doNotVibrateInTheater;
    }

    public boolean shouldNotVibrateOnCharger() {
        return doNotVibrateOnCharger;
    }

    public byte[] getIcon() {
        return icon;
    }

    public long[] getVibrationPattern() {
        return vibrationPattern;
    }

    public int getSnoozeDuration() {
        return snoozeDuration;
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.text);
        dest.writeLongArray(this.vibrationPattern);
        dest.writeByteArray(this.backgroundBitmap);
        dest.writeByteArray(this.icon);
        dest.writeInt(this.snoozeDuration);
        dest.writeByte(this.doNotVibrateInTheater ? (byte) 1 : (byte) 0);
        dest.writeByte(this.doNotVibrateOnCharger ? (byte) 1 : (byte) 0);
    }

    protected AlarmCommand(Parcel in) {
        this.text = in.readString();
        this.vibrationPattern = in.createLongArray();
        this.backgroundBitmap = in.createByteArray();
        this.icon = in.createByteArray();
        this.snoozeDuration = in.readInt();
        this.doNotVibrateInTheater = in.readByte() != 0;
        this.doNotVibrateOnCharger = in.readByte() != 0;
    }

    public static final Creator<AlarmCommand> CREATOR = new Creator<AlarmCommand>() {
        @Override public AlarmCommand createFromParcel(Parcel source) {
            return new AlarmCommand(source);
        }

        @Override public AlarmCommand[] newArray(int size) {
            return new AlarmCommand[size];
        }
    };
}
