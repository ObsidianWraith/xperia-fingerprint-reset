package com.xperia.fpreset

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.*

class FpResetTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    override fun onStartListening() {
        super.onStartListening()
        ResetStore.load(this)
        observer?.cancel()
        observer = scope.launch {
            ResetStore.state.collect { s ->
                qsTile?.apply {
                    label = "Reset fingerprint"
                    subtitle = if (s.busy) "Working…" else s.title
                    state = if (s.busy) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }
    override fun onStopListening() { observer?.cancel(); super.onStopListening() }
    override fun onClick() {
        super.onClick()
        if (ResetStore.state.value.busy) return
        if (isLocked) unlockAndRun { ResetService.start(this) } else ResetService.start(this)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
