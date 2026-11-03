package com.xperia.fpreset

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var signal: CancellationSignal? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val report = ResetStore.state.value.log
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = contentResolver.openOutputStream(uri) ?: error("No output stream")
                        stream.bufferedWriter().use { it.write(report) }
                    }
                }
                ResetStore.append(this@MainActivity, if (result.isSuccess) "Report exported." else "Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResetStore.load(this)
        setContent {
            val s by ResetStore.state.collectAsState()
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            val view = LocalView.current
            var lastCompletion by remember { mutableLongStateOf(s.completedReset) }
            LaunchedEffect(s.completedReset) {
                if (s.completedReset != 0L && s.completedReset != lastCompletion) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                lastCompletion = s.completedReset
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text("XPERIA 1 III · REV 002", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Fingerprint\nReset", style = MaterialTheme.typography.headlineLarge)
                        Text("${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text("Root: ${s.root}\nHAL: ${s.hal}\nPID: ${s.pid}")
                                Text("Status is the last observed snapshot.", style = MaterialTheme.typography.bodySmall)
                                Text("Restart the fingerprint service without rebooting. Root access is required. A service restart may help; a hardware fault may persist.")
                                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        Button(onClick = { signal?.cancel(); ResetService.start(this@MainActivity) },
                            enabled = !s.busy && FingerprintResetManager.supported(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Reset fingerprint sensor")
                        }
                        if (!FingerprintResetManager.supported()) Text("Reset is restricted to Xperia 1 III on Android 11–13. You can still collect diagnostics.")
                        OutlinedButton(onClick = { testBiometric() }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Test fingerprint") }
                        OutlinedButton(onClick = { signal?.cancel(); ResetService.start(this@MainActivity, true) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Collect diagnostics (read-only)") }
                        OutlinedButton(onClick = { signal?.cancel(); ResetService.start(this@MainActivity, refresh = true) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh status / kernel logs") }
                        Text("First use", style = MaterialTheme.typography.titleMedium)
                        Text("Unlock with your PIN, tap Reset, and grant root in Magisk / KernelSU / APatch. Keep this app open for the first request. Then test your fingerprint and test the lock screen separately.")
                        Text("Quick access", style = MaterialTheme.typography.titleMedium)
                        Text("Home screen: long-press an empty area → Widgets → Fingerprint Reset. Quick Settings: edit tiles and drag Reset fingerprint into your active tiles. The tile asks you to unlock if needed.")
                        if (Build.VERSION.SDK_INT >= 33) {
                            TextButton(onClick = { addTile() }) { Text("Add Quick Settings tile") }
                            TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable result notifications") }
                        }
                        TextButton(onClick = {
                            runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                                .onFailure { ResetStore.append(this@MainActivity, "Security settings unavailable: ${it.message}") }
                        }) { Text("Open security settings") }
                        Text("Hardware reset", style = MaterialTheme.typography.titleMedium)
                        Text("Not enabled: the sysfs driver and its remove/probe behavior must be verified for your firmware first. This revision restarts only the fingerprint HAL. It does not reset biometric lockout or repair a damaged sensor.")
                        Text("Latest report", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { export.launch("XperiaFingerprintReset_rev002_report.txt") }, enabled = !s.busy && s.log.isNotBlank()) { Text("Save report…") }
                        Text("Reports stay on this phone unless you save or share them. Review device and enrollment metadata before sharing.", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text(s.log.ifBlank { "No operation yet." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    private fun testBiometric() {
        signal?.cancel()
        val status = getSystemService(BiometricManager::class.java).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        ResetStore.append(this, "Biometric availability code: $status (0 = available; this is not proof of a working sensor).")
        signal = CancellationSignal()
        BiometricPrompt.Builder(this)
            .setTitle("Test fingerprint")
            .setSubtitle("Touch your enrolled finger to the sensor")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Cancel", mainExecutor) { _, _ -> signal?.cancel() }
            .build().authenticate(signal!!, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    ResetStore.append(this@MainActivity, "Biometric authentication succeeded. Now test fingerprint unlock on the lock screen.")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    ResetStore.append(this@MainActivity, "Biometric test: $errorCode — $errString")
                }
                override fun onAuthenticationFailed() {
                    ResetStore.append(this@MainActivity, "Fingerprint did not match. Try the enrolled finger again.")
                }
            })
    }
    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, FpResetTileService::class.java), "Reset fingerprint",
                Icon.createWithResource(this, R.drawable.ic_fingerprint), mainExecutor
            ) { result -> ResetStore.append(this, "Tile request result: $result") }
        }
    }
    override fun onStop() { signal?.cancel(); super.onStop() }
}
