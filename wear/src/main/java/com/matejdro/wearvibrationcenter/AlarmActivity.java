package com.matejdro.wearvibrationcenter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.activity.WearableActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.matejdro.wearutils.messages.ParcelPacker;
import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearutils.preferences.definition.Preferences;
import com.matejdro.wearvibrationcenter.common.AlarmCommand;
import com.matejdro.wearvibrationcenter.preferences.GlobalSettings;

import timber.log.Timber;

public class AlarmActivity extends WearableActivity implements View.OnTouchListener {
    public static final String EXTRA_ALARM_COMMAND_BYTES = "AlarmCommandBytes";
    public static final String EXTRA_ALARM_TEXT = "Text";

    private int displayWidth;

    private FrameLayout rootLayout;
    private FrameLayout movableLayout;
    private FrameLayout centerMovableLayout;
    private ImageView leftMovableCircle;
    private ImageView rightMovableCircle;

    private ImageView backgroundImage;
    private ImageView iconImage;
    private TextView titleBox;

    private int leftCircleStartPosition;
    private int rightCircleStartPosition;

    private int moveStartX = 0;
    private int lastMoveX = 0;

    private boolean resumed = false;

    private AlarmCommand alarmCommand;

    private Vibrator vibrator;
    private Handler mainThreadHandler;
    private Runnable vibrationRestartRunnable = new Runnable() {
        @Override
        public void run() {
            restartVibrator();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAmbientEnabled();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        byte[] alarmCommandData = getIntent().getByteArrayExtra(EXTRA_ALARM_COMMAND_BYTES);
        if (alarmCommandData == null) {
            String alarmText = getIntent().getStringExtra(EXTRA_ALARM_TEXT);
            if (alarmText != null) {
                alarmCommand = createTextAlarmCommand(alarmText);
            } else {
                Timber.e("No alarm intent!");
                finish();
                return;
            }
        } else {
            alarmCommand = ParcelPacker.getParcelable(alarmCommandData, AlarmCommand.CREATOR);
        }

        setContentView(R.layout.activity_alarm);

        rootLayout = (FrameLayout) findViewById(R.id.root_layout);
        movableLayout = (FrameLayout) findViewById(R.id.movable_layout);
        centerMovableLayout = (FrameLayout) findViewById(R.id.center_movable_layout);
        leftMovableCircle = (ImageView) findViewById(R.id.left_movable_circle);
        rightMovableCircle = (ImageView) findViewById(R.id.right_movable_circle);

        backgroundImage = (ImageView) findViewById(R.id.background);
        iconImage = (ImageView) findViewById(R.id.icon);
        titleBox = (TextView) findViewById(R.id.title);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mainThreadHandler = new Handler();

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                initAnimationParameters();
            }
        });
        loadAlarmData();
        setupSelfDismiss();
    }

    @NonNull
    private AlarmCommand createTextAlarmCommand(String alarmText) {
        return new AlarmCommand(alarmText, new long[]{0, 250, 250, 250, 250}, null, null, 600, false, false);
    }

    private void initAnimationParameters() {
        displayWidth = rootLayout.getMeasuredWidth();
        int animationWidth = displayWidth * 5 / 3;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) movableLayout.getLayoutParams();
        layoutParams.width = animationWidth * 2 + displayWidth;
        layoutParams.leftMargin = -animationWidth;
        layoutParams.rightMargin = -animationWidth;
        movableLayout.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) centerMovableLayout.getLayoutParams();
        layoutParams.leftMargin = animationWidth;
        layoutParams.rightMargin = animationWidth;
        centerMovableLayout.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) leftMovableCircle.getLayoutParams();
        layoutParams.leftMargin = displayWidth;
        layoutParams.rightMargin = animationWidth;
        leftMovableCircle.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) rightMovableCircle.getLayoutParams();
        layoutParams.leftMargin = animationWidth;
        layoutParams.rightMargin = displayWidth;
        rightMovableCircle.setLayoutParams(layoutParams);

        int circleInset = getResources().getDimensionPixelSize(R.dimen.alarm_circle_start_inset);
        leftCircleStartPosition = -displayWidth + circleInset;
        rightCircleStartPosition = displayWidth - circleInset;

        setShiftPosition(0);
        rootLayout.setOnTouchListener(this);
    }

    private void setShiftPosition(int position) {
        centerMovableLayout.setTranslationX(position);
        leftMovableCircle.setTranslationX(leftCircleStartPosition + position);
        rightMovableCircle.setTranslationX(rightCircleStartPosition + position);
    }

    private void loadAlarmData() {
        titleBox.setText(alarmCommand.getText());
        iconImage.setImageBitmap(BitmapUtils.deserialize(alarmCommand.getIcon()));
        backgroundImage.setImageBitmap(BitmapUtils.deserialize(alarmCommand.getBackgroundBitmap()));
    }

    private void setupSelfDismiss() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int alarmTimeoutSeconds = Preferences.getInt(preferences, GlobalSettings.ALARM_TIMEOUT);
        if (alarmTimeoutSeconds > 0) {
            mainThreadHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismissAlarm();
                }
            }, alarmTimeoutSeconds * 1000);
        }
    }

    private void restartVibrator() {
        if (!resumed) {
            vibrator.cancel();
            return;
        }

        int vibrationTimeSum = 0;
        for (long vibElem : alarmCommand.getVibrationPattern()) {
            vibrationTimeSum += vibElem;
        }

        vibrator.cancel();
        vibrator.vibrate(alarmCommand.getVibrationPattern(), -1);
        mainThreadHandler.removeCallbacks(vibrationRestartRunnable);
        mainThreadHandler.postDelayed(vibrationRestartRunnable, vibrationTimeSum);
    }

    @Override
    protected void onResume() {
        restartVibrator();
        onAmbientStateChanged(isAmbient());
        resumed = true;

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
            vibrator.cancel();
            mainThreadHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        onAmbientStateChanged(true);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                initAnimationParameters();
            }
        });

        onAmbientStateChanged(false);
    }

    private void dismissAlarm() {
        finish();
    }

    private void snoozeAlarm() {
        Intent intent = new Intent(this, ConfirmationActivity.class);
        intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.SUCCESS_ANIMATION);
        intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                getString(R.string.alarm_snoozed));
        startActivity(intent);

        Intent alarmActivityIntent = new Intent(this, AlarmActivity.class);
        alarmActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alarmActivityIntent.putExtra(AlarmActivity.EXTRA_ALARM_COMMAND_BYTES, ParcelPacker.getData(alarmCommand));
        startActivity(alarmActivityIntent);

        PendingIntent alarmPendingIntent = PendingIntent.getActivity(this, 0, alarmActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + alarmCommand.getSnoozeDuration(), alarmPendingIntent);

        finish();
    }

    private void onAmbientStateChanged(boolean inAmbientNow) {
        if (rootLayout == null) {
            // This method seem to sometimes call before onCreate. Ignore it.
            return;
        }
        movableLayout.setVisibility(inAmbientNow ? View.INVISIBLE : View.VISIBLE);
        backgroundImage.setVisibility(inAmbientNow ? View.INVISIBLE : View.VISIBLE);

        // Vibration will stop when entering ambient some time after this method has been called.
        // Re-start vibration after a short delay.
        mainThreadHandler.removeCallbacks(vibrationRestartRunnable);
        mainThreadHandler.postDelayed(vibrationRestartRunnable, 500);

        if (!isAmbient()) {
            lastMoveX = 0;
            moveStartX = 0;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isAmbient()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moveStartX = (int) event.getRawX();
                break;
            case MotionEvent.ACTION_MOVE:
                int moveLength = (int) (event.getRawX() - moveStartX);
                lastMoveX = moveLength;
                setShiftPosition(moveLength * 4 / 3);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                if (lastMoveX != 0 && moveStartX != 0) {
                    setShiftPosition(0);
                    if (lastMoveX < -displayWidth / 2) {
                        snoozeAlarm();
                    } else if (lastMoveX > displayWidth / 2) {
                        dismissAlarm();
                    }
                }

                lastMoveX = 0;
                moveStartX = 0;
                break;
        }
        return true;
    }
}
