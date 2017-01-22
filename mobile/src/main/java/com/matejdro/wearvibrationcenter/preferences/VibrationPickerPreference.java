package com.matejdro.wearvibrationcenter.preferences;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.DialogPreference;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.matejdro.wearvibrationcenter.common.VibrationCommand;
import com.matejdro.wearutils.miscutils.ArrayUtils;
import com.matejdro.wearutils.miscutils.HtmlCompat;
import com.matejdro.wearvibrationcenter.R;
import com.matejdro.wearvibrationcenter.watch.WatchCommander;


public class VibrationPickerPreference extends DialogPreference {
    private Vibrator vibrator;

    private EditText vibrationPatternBox;
    private Button tapActivationButton;
    private View vibrationTapperBox;

    private boolean tapMode = false;
    private long lastChangeTime = 0;
    private String currentTapPattern;

    private int addedPause;

    private String summaryFormat;
    private String savedTapPattern;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VibrationPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public VibrationPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public VibrationPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VibrationPickerPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        summaryFormat = getSummary().toString();

        // Add 1 second pause at the end for alarm mode.
        addedPause = 1000;
    }

    @Override
    protected View onCreateDialogView() {
        @SuppressLint("InflateParams")
        ViewGroup root = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.vibration_pattern_picker, null);

        vibrationPatternBox = (EditText) root.findViewById(R.id.pattern_box);
        tapActivationButton = (Button) root.findViewById(R.id.tap_button);
        vibrationTapperBox = root.findViewById(R.id.tap_box);

        vibrationPatternBox.setText(savedTapPattern);

        tapActivationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTapMode();
            }
        });

        root.findViewById(R.id.phone_test_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testVibrationOnPhone();
            }
        });

        root.findViewById(R.id.watch_test_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testVibrationOnWatch();
            }
        });

        root.findViewById(R.id.help_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp();
            }
        });

        vibrationTapperBox.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onTapEvent(event);
                return false;
            }
        });

        return root;
    }

    public void setSavedTapPattern(String savedTapPattern) {
        boolean noChange = TextUtils.equals(savedTapPattern, this.savedTapPattern);
        if (noChange) {
            return;
        }

        this.savedTapPattern = savedTapPattern;

        String summary = String.format(summaryFormat, savedTapPattern);
        setSummary(HtmlCompat.fromHtml(summary));

        notifyDependencyChange(shouldDisableDependents());
        notifyChanged();
    }

    private void onOkClicked() {
        if (parseAndValidateVibration() == null) {
            return;
        }

        setSavedTapPattern(vibrationPatternBox.getText().toString());
        persistString(savedTapPattern);

        getDialog().dismiss();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // Remap OK button to not close dialog if pattern is not valid.
        android.app.AlertDialog dialog = (android.app.AlertDialog) getDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOkClicked();
            }
        });
    }

    /**
     * Overrides hidden method in DialogPreference
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean needInputMethod() {
        return true;
    }


    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setSavedTapPattern(restorePersistedValue ? getPersistedString((String) defaultValue) : (String) defaultValue);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    private void toggleTapMode() {
        tapMode = !tapMode;

        if (tapMode) {
            vibrationTapperBox.setVisibility(View.VISIBLE);
            tapActivationButton.setText(R.string.finish_tapping);
            lastChangeTime = 0;
            currentTapPattern = "0";
        } else {
            vibrationTapperBox.setVisibility(View.GONE);
            tapActivationButton.setText(R.string.tap_vibration_pattern);

            if (addedPause > 0) {
                currentTapPattern += ", " + addedPause;
            }

            vibrationPatternBox.setText(currentTapPattern);
            vibrator.cancel();
        }
    }

    public
    @Nullable
    long[] parseAndValidateVibration() {
        long[] pattern = ArrayUtils.parseLongArray(vibrationPatternBox.getText().toString());

        if (pattern == null || pattern.length < 2) {
            vibrationPatternBox.setError(getContext().getString(R.string.invalid_vibration_pattern));

            return null;
        }

        return pattern;
    }

    private void testVibrationOnPhone() {
        long[] pattern = parseAndValidateVibration();
        if (pattern == null) {
            return;
        }

        vibrator.cancel();
        vibrator.vibrate(pattern, -1);
    }

    private void testVibrationOnWatch() {
        long[] pattern = parseAndValidateVibration();
        if (pattern == null) {
            return;
        }

        WatchCommander.sendVibrationCommand(getContext(), new VibrationCommand(pattern, false, false));
    }


    private void onTapEvent(MotionEvent event) {
        int eventType = event.getActionMasked();
        if (eventType == MotionEvent.ACTION_DOWN) {
            tapModeFingerDown();
        } else if (eventType == MotionEvent.ACTION_UP || eventType == MotionEvent.ACTION_OUTSIDE || eventType == MotionEvent.ACTION_CANCEL) {
            tapModeFingerUp();
        }
    }

    private void addChange() {
        int diffMs = (int) (System.currentTimeMillis() - lastChangeTime);
        lastChangeTime = System.currentTimeMillis();

        if (currentTapPattern.isEmpty()) {
            currentTapPattern += Integer.toString(diffMs);
        } else {
            currentTapPattern += ", " + Integer.toString(diffMs);
        }
    }

    private void tapModeFingerDown() {
        vibrator.vibrate(10000);

        if (lastChangeTime == 0) {
            lastChangeTime = System.currentTimeMillis();
        } else {
            addChange();
        }
    }

    private void tapModeFingerUp() {
        vibrator.cancel();
        addChange();
    }

    private void showHelp() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.setting_vibration_pattern)
                .setMessage(R.string.vibration_pattern_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
