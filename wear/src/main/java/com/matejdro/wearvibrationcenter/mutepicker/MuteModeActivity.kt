package com.matejdro.wearvibrationcenter.mutepicker

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.wearable.CapabilityApi.GetCapabilityResult
import com.matejdro.wearutils.companionnotice.WearCompanionWatchActivity
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.mutepicker.AppMuteActivity

class MuteModeActivity : WearCompanionWatchActivity(), GoogleApiClient.ConnectionCallbacks,
    ResultCallback<GetCapabilityResult?> {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mute_mode)
    }

    fun openTimedMute(view: View?) {
        startActivity(Intent(this, TimedMuteActivity::class.java))
        finish()
    }

    fun openAppMute(view: View?) {
        startActivity(Intent(this, AppMuteActivity::class.java))
        finish()
    }

    override fun getPhoneAppPresenceCapability(): String {
        return CommPaths.PHONE_APP_CAPABILITY
    }
}
