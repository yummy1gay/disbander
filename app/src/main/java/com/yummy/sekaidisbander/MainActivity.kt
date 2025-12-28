package com.yummy.sekaidisbander

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvSizeValue: TextView
    private lateinit var tvAlphaValue: TextView
    private lateinit var tvArrowWidthValue: TextView
    private lateinit var tvArrowHeightValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        val btnPick = findViewById<MaterialButton>(R.id.btnPickImage)
        val btnReset = findViewById<MaterialButton>(R.id.btnResetImage)
        val btnStart = findViewById<MaterialButton>(R.id.btnStart)
        val btnStop = findViewById<MaterialButton>(R.id.btnStop)

        val seekSize = findViewById<SeekBar>(R.id.seekSize)
        val seekAlpha = findViewById<SeekBar>(R.id.seekAlpha)
        val seekArrowWidth = findViewById<SeekBar>(R.id.seekArrowWidth)
        val seekArrowHeight = findViewById<SeekBar>(R.id.seekArrowHeight)

        tvSizeValue = findViewById(R.id.tvSizeValue)
        tvAlphaValue = findViewById(R.id.tvAlphaValue)
        tvArrowWidthValue = findViewById(R.id.tvArrowWidthValue)
        tvArrowHeightValue = findViewById(R.id.tvArrowHeightValue)

        val prefs = getSharedPreferences("DisbanderPrefs", Context.MODE_PRIVATE)

        val currentSize = prefs.getInt("BUTTON_SIZE", 150)
        seekSize.progress = currentSize
        tvSizeValue.text = getString(R.string.size_format, currentSize)

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 50) 50 else progress
                tvSizeValue.text = getString(R.string.size_format, size)
                prefs.edit { putInt("BUTTON_SIZE", size) }
                sendConfigUpdate(size = size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val currentAlpha = prefs.getInt("ARROW_ALPHA", 90)
        seekAlpha.progress = currentAlpha
        tvAlphaValue.text = getString(R.string.alpha_format, currentAlpha)

        seekAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAlphaValue.text = getString(R.string.alpha_format, progress)
                prefs.edit { putInt("ARROW_ALPHA", progress) }
                sendConfigUpdate(alpha = progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val currentWidth = prefs.getInt("ARROW_WIDTH", 60)
        seekArrowWidth.progress = currentWidth
        tvArrowWidthValue.text = getString(R.string.size_format, currentWidth)

        seekArrowWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 20) 20 else progress
                tvArrowWidthValue.text = getString(R.string.size_format, size)
                prefs.edit { putInt("ARROW_WIDTH", size) }
                sendConfigUpdate(width = size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val currentHeight = prefs.getInt("ARROW_HEIGHT", 120)
        seekArrowHeight.progress = currentHeight
        tvArrowHeightValue.text = getString(R.string.size_format, currentHeight)

        seekArrowHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 20) 20 else progress
                tvArrowHeightValue.text = getString(R.string.size_format, size)
                prefs.edit { putInt("ARROW_HEIGHT", size) }
                sendConfigUpdate(height = size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                saveImageToInternalStorage(it)
                val intent = Intent("UPDATE_OVERLAY_ICON")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }

        btnPick.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnReset.setOnClickListener {
            val file = File(filesDir, "custom_icon.png")
            if (file.exists()) file.delete()
            Toast.makeText(this, getString(R.string.toast_reset), Toast.LENGTH_SHORT).show()

            val intent = Intent("UPDATE_OVERLAY_ICON")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        }

        btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, OverlayService::class.java)
            stopService(intent)
        }
    }

    private fun sendConfigUpdate(size: Int = -1, alpha: Int = -1, width: Int = -1, height: Int = -1) {
        val intent = Intent("UPDATE_OVERLAY_CONFIG")
        if (size != -1) intent.putExtra("SIZE", size)
        if (alpha != -1) intent.putExtra("ALPHA", alpha)
        if (width != -1) intent.putExtra("ARROW_WIDTH", width)
        if (height != -1) intent.putExtra("ARROW_HEIGHT", height)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.perm_required), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        } else {
            val intent = Intent(this, OverlayService::class.java)
            startService(intent)
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "custom_icon.png")
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_error), Toast.LENGTH_SHORT).show()
        }
    }
}