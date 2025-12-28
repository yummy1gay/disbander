package com.yummy.sekaidisbander

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class DisbanderTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (Build.VERSION.SDK_INT >= 34) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }

            Toast.makeText(this, getString(R.string.perm_required), Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java)

        if (OverlayService.isRunning) {
            stopService(serviceIntent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        qsTile?.state = if (OverlayService.isRunning) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        qsTile?.updateTile()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTileState()
        }, 300)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        if (OverlayService.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_active)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_inactive)
        }

        tile.updateTile()
    }
}