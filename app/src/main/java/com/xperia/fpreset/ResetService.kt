package com.xperia.fpreset

import android.app.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** One service instance serializes every entry point. Never restarts automatically after death. */
class ResetService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var latestStart = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        ResetStore.load(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Fingerprint reset", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification("Working…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        else startForeground(1, notification("Working…"))
        if (job?.isActive == true) return START_NOT_STICKY
        val diagnose = intent?.action == DIAGNOSE
        val refresh = intent?.action == REFRESH
        job = scope.launch {
            var title = "Operation failed"
            try {
                ResetStore.begin(this@ResetService, if (diagnose) "Diagnostic report" else if (refresh) "Status / kernel logs" else "Fingerprint HAL restart")
                FpResetWidget.refresh(this@ResetService)
                check(!getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Unlock the phone using your PIN or password first." }
                wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:reset")
                    .apply { acquire(170000) }
                val emit: (String) -> Unit = { ResetStore.append(this@ResetService, it) }
                title = if (refresh) {
                    FingerprintResetManager.refresh(emit)
                    FingerprintResetManager.kernelLogs(emit)
                    "Status refreshed"
                } else if (diagnose) {
                    val ok = FingerprintResetManager.diagnose(emit)
                    if (ok) { FingerprintResetManager.refresh(emit); FingerprintResetManager.kernelLogs(emit) }
                    if (ok) "Diagnostics ready" else "Diagnostics incomplete"
                } else {
                    val result = FingerprintResetManager.resetSensor(emit)
                    FingerprintResetManager.kernelLogs(emit)
                    ResetStore.resetCompleted()
                    "HAL reset: ${result.oldPid} → ${result.newPid}"
                }
            } catch (e: Exception) {
                ResetStore.append(this@ResetService, e.message ?: e.javaClass.simpleName)
                ResetStore.status(ResetStore.state.value.root, "Unknown — operation failed", "—")
            } finally {
                if (wake?.isHeld == true) wake?.release()
                wake = null
                ResetStore.finish(this@ResetService, title)
                Toast.makeText(this@ResetService, title, Toast.LENGTH_LONG).show()
                FpResetWidget.refresh(this@ResetService)
                stopForeground(STOP_FOREGROUND_DETACH)
                // The FGS itself can run without notification permission on API 33.
                val nm = getSystemService(NotificationManager::class.java)
                if (nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(this@ResetService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                    nm.notify(1, notification(title))
                }
                stopSelfResult(latestStart)
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_fingerprint)
            .setContentTitle("Fingerprint Reset").setContentText(text).setContentIntent(open)
            .setOnlyAlertOnce(true).setAutoCancel(true).build()
    }
    override fun onTimeout(startId: Int) {
        ResetStore.append(this, "Android short-service timeout; outcome may be unknown.")
        ResetStore.finish(this, "Timed out")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        if (wake?.isHeld == true) wake?.release()
        super.onDestroy()
    }
    companion object {
        const val RESET = "com.xperia.fpreset.RESET"
        const val REFRESH = "com.xperia.fpreset.REFRESH"
        const val DIAGNOSE = "com.xperia.fpreset.DIAGNOSE"
        private const val CHANNEL = "reset"
        fun start(context: Context, diagnose: Boolean = false, refresh: Boolean = false) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ResetService::class.java).setAction(if (diagnose) DIAGNOSE else if (refresh) REFRESH else RESET))
            } catch (e: Exception) {
                ResetStore.load(context)
                ResetStore.append(context, "Could not start: ${e.message}")
                ResetStore.finish(context, "Open the app to run reset")
                FpResetWidget.refresh(context)
            }
        }
    }
}
