package com.seki.multilangduo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import com.seki.multilangduo.ui.AppScreen

class MainActivity : ComponentActivity() {
    private val model: TtsViewModel by viewModels()
    private var exportAfterPermission = false
    private var microphoneRequired = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (microphoneRequired && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            model.message("麦克风权限未授权。请在系统应用设置中允许麦克风后重试。")
        } else if (exportAfterPermission) model.export() else model.read()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportAfterPermission = savedInstanceState?.getBoolean("permission_export") ?: false
        microphoneRequired = savedInstanceState?.getBoolean("permission_microphone") ?: false
        // The flag applies while this Activity is visible; no wake lock or extra permission is needed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                AppScreen(model, onRead = { requestTask(false) }, onExport = { requestTask(true) }, onTtsSettings = {
                    try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                    catch (_: Exception) {
                        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        catch (_: Exception) { model.message("无法打开系统设置") }
                    }
                })
            }
        }
    }
    private fun requestTask(export: Boolean) {
        exportAfterPermission = export
        microphoneRequired = !export && model.needsMicrophone()
        val requested = mutableListOf<String>()
        if (microphoneRequired && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requested += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requested += Manifest.permission.POST_NOTIFICATIONS
        if (requested.isNotEmpty()) permissions.launch(requested.toTypedArray())
        else if (export) model.export() else model.read()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permission_export", exportAfterPermission)
        outState.putBoolean("permission_microphone", microphoneRequired)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); model.reloadVoices() }
}
