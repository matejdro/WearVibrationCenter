package com.matejdro.wearvibrationcenter.common;

import android.os.Parcel;
import android.os.Parcelable;

public class LiteAlarmCommand implements Parcelable, InterruptionCommand {
    private final String text;
    private final long[] vibrationPattern;
    private final int snoozeDuration;
    private final boolean doNotVibrateInTheater;
    private final boolean doNotVibrateOnCharger;

    public LiteAlarmCommand(AlarmCommand alarmCommand)
    {
        this(alarmCommand.getText(),
                alarmCommand.getVibrationPattern(),
                alarmCommand.getSnoozeDuration(),
                alarmCommand.shouldNotVibrateInTheater(),
                alarmCommand.shouldNotVibrateOnCharger());
    }

    public LiteAlarmCommand(String text, long[] vibrationPattern, int snoozeDuration, boolean doNotVibrateInTheater, boolean doNotVibrateOnCharger) {
        this.text = text;
        this.vibrationPattern = vibrationPattern;
        this.snoozeDuration = snoozeDuration;
        this.doNotVibrateInTheater = doNotVibrateInTheater;
        this.doNotVibrateOnCharger = doNotVibrateOnCharger;
    }

    public String getText() {
        return text;
    }

    public long[] getVibrationPattern() {
        return vibrationPattern;
    }

    public int getSnoozeDuration() {
        return snoozeDuration;
    }

    public boolean shouldNotVibrateInTheater() {
        return doNotVibrateInTheater;
    }

    public boolean shouldNotVibrateOnCharger() {
        return doNotVibrateOnCharger;
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.text);
        dest.writeLongArray(this.vibrationPattern);
        dest.writeInt(this.snoozeDuration);
        dest.writeByte(this.doNotVibrateInTheater ? (byte) 1 : (byte) 0);
        dest.writeByte(this.doNotVibrateOnCharger ? (byte) 1 : (byte) 0);

        // DataLayer API will only send different items, so if two same alarms are triggered,
        // only one will be triggered. Workaround is to add timestamp to always send changed packet.
        dest.writeLong(System.currentTimeMillis());
    }

    protected LiteAlarmCommand(Parcel in) {
        this.text = in.readString();
        this.vibrationPattern = in.createLongArray();
        this.snoozeDuration = in.readInt();
        this.doNotVibrateInTheater = in.readByte() != 0;
        this.doNotVibrateOnCharger = in.readByte() != 0;
    }

    public static final Creator<LiteAlarmCommand> CREATOR = new Creator<LiteAlarmCommand>() {
        @Override public LiteAlarmCommand createFromParcel(Parcel source) {
            return new LiteAlarmCommand(source);
        }

        @Override public LiteAlarmCommand[] newArray(int size) {
            return new LiteAlarmCommand[size];
        }
    };
}
