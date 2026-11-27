package com.assem.copyapp

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.assem.copyapp.data.SessionState
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val state = (applicationContext as App).container.repository.sessionFlow().first()
            withContext(Dispatchers.Main) { updateTile(state) }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val container = (applicationContext as App).container
            val state = container.repository.sessionFlow().first()
            if (state.active) {
                container.sessionController.end("TILE", fromUser = true)
            } else {
                val settings = container.repository.settingsFlow().first()
                container.sessionController.start(
                    settings.defaultDurationMinutes,
                    emptySet(),
                    settings.defaultMode,
                    settings.strictByDefault
                )
                startForegroundService(Intent(this@FocusTileService, TimerService::class.java))
            }
            val updated = container.repository.sessionFlow().first()
            withContext(Dispatchers.Main) { updateTile(updated) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(state: SessionState) {
        val tile = qsTile ?: return
        tile.state = if (state.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(if (state.active) R.string.tile_active else R.string.tile_start)
        tile.contentDescription = getString(R.string.tile_description)
        tile.updateTile()
    }
}
