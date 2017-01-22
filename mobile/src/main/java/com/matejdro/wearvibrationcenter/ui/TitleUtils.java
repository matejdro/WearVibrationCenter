package com.matejdro.wearvibrationcenter.ui;

import android.app.Activity;

public class TitleUtils {
    public static void updateTitle(Activity activity, CharSequence newTitle) {
        if (activity instanceof TitledActivity) {
            ((TitledActivity) activity).updateTitle(newTitle);
        }
    }

    public interface TitledActivity {
        void updateTitle(CharSequence newTitle);
    }

    public interface TitledFragment {
        String getTitle();
    }
}
