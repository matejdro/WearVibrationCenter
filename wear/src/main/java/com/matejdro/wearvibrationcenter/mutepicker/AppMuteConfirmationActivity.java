package com.matejdro.wearvibrationcenter.mutepicker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.matejdro.wearvibrationcenter.R;

public class AppMuteConfirmationActivity extends Activity {
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_DO_NOT_ASK_AGAIN = "DoNotAskAgain";

    private CheckBox doNotAskAgainBox;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_mute_confirmation);

        String appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        TextView descriptionText = (TextView) findViewById(R.id.description);
        descriptionText.setText(getString(R.string.app_mute_confirmation_explanation, appName));

        doNotAskAgainBox = (CheckBox) findViewById(R.id.do_not_ask_again_checkbox);
    }

    public void cancelButton(View view) {
        setResult(RESULT_CANCELED);
        finish();
    }

    public void okButton(View view) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_DO_NOT_ASK_AGAIN, doNotAskAgainBox.isChecked());
        setResult(RESULT_OK, resultIntent);

        finish();
    }
}
