package com.matejdro.wearvibrationcenter.ui;

import android.content.Context;
import android.util.AttributeSet;

import com.matejdro.wearutils.preferences.legacy.StringListPreference;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexListPreference extends StringListPreference {
    public RegexListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public RegexListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RegexListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RegexListPreference(Context context) {
        super(context);
    }

    @Override
    protected String validateNewEntry(String newEntry) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Pattern.compile(newEntry);
        } catch (PatternSyntaxException e) {
            return e.getMessage();
        }

        return null;
    }
}
