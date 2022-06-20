package com.matejdro.wearremotelist.parcelables;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Parcelable wrapper for Bitmap that also compresses Bitmap to PNG for faster transmission.
 */
public class CompressedParcelableBitmap implements Parcelable
{
    private Bitmap bitmap;

    public CompressedParcelableBitmap(Bitmap bitmap)
    {
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap()
    {
        return bitmap;
    }


    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        if (bitmap == null)
        {
            dest.writeByte((byte) 0);
            return;
        }
        else
        {
            dest.writeByte((byte) 1);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream(5000);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bytes = stream.toByteArray();
        dest.writeByteArray(bytes);

        try
        {
            stream.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    protected CompressedParcelableBitmap(Parcel in)
    {
        byte isNotNull = in.readByte();
        if (isNotNull == 0)
        {
            this.bitmap = null;
            return;
        }

        byte[] byteArray = in.createByteArray();
        this.bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
    }

    public static final Parcelable.Creator<CompressedParcelableBitmap> CREATOR = new Parcelable.Creator<CompressedParcelableBitmap>()
    {
        public CompressedParcelableBitmap createFromParcel(Parcel source)
        {
            return new CompressedParcelableBitmap(source);
        }

        public CompressedParcelableBitmap[] newArray(int size)
        {
            return new CompressedParcelableBitmap[size];
        }
    };
}
