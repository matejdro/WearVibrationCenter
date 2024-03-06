package com.matejdro.wearvibrationcenter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import com.matejdro.wearutils.logging.LogRetrievalTask
import com.matejdro.wearutils.preferences.legacy.CustomStoragePreferenceFragment
import com.matejdro.wearutils.preferencesync.PreferencePusher.pushPreferences
import com.matejdro.wearvibrationcenter.R
import com.matejdro.wearvibrationcenter.common.CommPaths
import com.matejdro.wearvibrationcenter.ui.TitleUtils.TitledFragment
import de.psdev.licensesdialog.LicensesDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

open class GlobalSettingsFragment : CustomStoragePreferenceFragment(), TitledFragment {

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.global_settings)
        try {
            findPreference("version").summary =
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        findPreference("supportButton").onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                sendLogs()
                true
            }
        findPreference("licenses").onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                LicensesDialog.Builder(activity)
                    .setNotices(R.raw.notices)
                    .setIncludeOwnLicense(true)
                    .build()
                    .show()
                true
            }
        findPreference("donateButton").onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=THRX5EMUNBZE6"))
                startActivity(intent)
                true
            }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Deprecated("Deprecated in Java")
    override fun onStop() {
        super.onStop()
        if (canTransmitSettingsAutomatically()) {
            GlobalScope.launch(Dispatchers.Main) {
                pushPreferences(
                    requireNotNull(context).applicationContext,
                    preferenceManager.getSharedPreferences(),
                    CommPaths.PREFERENCES_PREFIX,
                    false
                )
            }

        }
    }

    override fun getTitle(): String {
        return getString(R.string.global_settings)
    }

    protected open fun canTransmitSettingsAutomatically(): Boolean {
        return true
    }

    private fun sendLogs() {
        LogRetrievalTask(
            activity,
            CommPaths.COMMAND_SEND_LOGS,
            "matejdro+support@gmail.com",
            "com.matejdro.wearvibrationcenter.logs"
        ).execute(null as Void?)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissions.size > 0 && permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendLogs()
        }
    }
}
