package com.matejdro.wearremotelist.parcelables;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable wrapper for String, provided for convenience
 */
public class StringParcelableWraper implements Parcelable
{
    private String string;

    public StringParcelableWraper(String string)
    {
        this.string = string;
    }

    public String getString()
    {
        return string;
    }

    protected StringParcelableWraper(Parcel in)
    {
        this.string = in.readString();
    }

    public static final Creator<StringParcelableWraper> CREATOR = new Creator<StringParcelableWraper>()
    {
        @Override
        public StringParcelableWraper createFromParcel(Parcel in)
        {
            return new StringParcelableWraper(in);
        }

        @Override
        public StringParcelableWraper[] newArray(int size)
        {
            return new StringParcelableWraper[size];
        }
    };

    @Override
    public int hashCode()
    {
        return getString().hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        //noinspection SimplifiableIfStatement
        if (o instanceof StringParcelableWraper)
            return getString().equals(((StringParcelableWraper) o).getString());

        return false;
    }

    @Override
    public String toString()
    {
        return getString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(string);
    }
}
