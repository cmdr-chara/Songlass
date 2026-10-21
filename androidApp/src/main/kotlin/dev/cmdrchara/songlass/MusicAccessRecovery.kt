package dev.cmdrchara.songlass

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import dev.cmdrchara.songlass.core.permissions.PermissionRecovery
import dev.cmdrchara.songlass.core.permissions.PermissionRecoveryAction
import dev.cmdrchara.songlass.core.storage.KeyValueStore

/** Platform permission recovery, shown only after an explicit tap on the existing access action. */
internal class MusicAccessRecovery(
    private val activity: ComponentActivity,
    private val launchRequest: () -> Unit,
    private val refreshAccess: () -> Unit,
) {
    // Separate from Songlass app data/export. Cloud and device-transfer rules exclude all prefs.
    private val preferences = activity.getSharedPreferences("songlass.permissions", Context.MODE_PRIVATE)
    private val recovery = PermissionRecovery(object : KeyValueStore {
        override fun getString(key: String): String? = preferences.getString(key, null)
        override fun putString(key: String, value: String?) {
            preferences.edit { putString(key, value) }
        }
    })
    private var dialog: AlertDialog? = null

    fun requestAccess() {
        if (dialog?.isShowing == true) return
        val granted = activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        when (recovery.action(granted, activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO))) {
            PermissionRecoveryAction.AlreadyGranted -> refreshAccess()
            PermissionRecoveryAction.RequestPermission -> requestPermission()
            PermissionRecoveryAction.OfferRecoveryChoice -> {
                dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.music_access_recovery_title)
                    .setMessage(R.string.music_access_recovery_message)
                    .setPositiveButton(R.string.music_access_try_request) { _, _ -> requestPermission() }
                    .setNeutralButton(R.string.music_access_open_settings) { _, _ -> openAppSettings() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener { dialog = null }
                    .show()
            }
        }
    }

    private fun requestPermission() {
        try {
            launchRequest()
            recovery.requestLaunched()
        } catch (failure: Exception) {
            android.util.Log.w("MusicAccess", "Permission request unavailable", failure)
            Toast.makeText(activity, R.string.music_access_request_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)))
        } catch (failure: Exception) {
            android.util.Log.w("MusicAccess", "App settings unavailable", failure)
            Toast.makeText(activity, R.string.music_access_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun close() { dialog?.dismiss(); dialog = null }
}
