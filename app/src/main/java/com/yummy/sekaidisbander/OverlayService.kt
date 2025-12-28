package com.yummy.sekaidisbander

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class OverlayService : Service() {

    companion object {
        var isRunning = false
    }

    private var cachedBitmap: android.graphics.Bitmap? = null
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var imageView: ImageView

    private var isCollapsed = false

    private var expandedSize = 150
    private var arrowAlpha = 0.9f
    private var arrowWidth = 60
    private var arrowHeight = 120

    private var screenWidth = 0
    private var screenHeight = 0

    private var justExpanded = false
    private var isImmuneToSuck = false
    private var waitingFingerInside = false

    private var smoothX = 0f
    private var smoothY = 0f

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    private val suckThreshold = 150
    private val moveSlop = 10f
    private val pullThreshold = 30

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "UPDATE_OVERLAY_CONFIG" -> {
                    val newSize = intent.getIntExtra("SIZE", -1)
                    val newAlpha = intent.getIntExtra("ALPHA", -1)
                    val newWidth = intent.getIntExtra("ARROW_WIDTH", -1)
                    val newHeight = intent.getIntExtra("ARROW_HEIGHT", -1)

                    if (newSize != -1) {
                        expandedSize = newSize
                        if (!isCollapsed) updateSize(newSize)
                    }
                    if (newAlpha != -1) {
                        arrowAlpha = newAlpha / 100f
                        if (isCollapsed) imageView.alpha = arrowAlpha
                    }

                    var needArrowUpdate = false
                    if (newWidth != -1) {
                        arrowWidth = newWidth
                        needArrowUpdate = true
                    }
                    if (newHeight != -1) {
                        arrowHeight = newHeight
                        needArrowUpdate = true
                    }

                    if (needArrowUpdate && isCollapsed) {
                        val isLeft = params.x < screenWidth / 2
                        collapseView(isLeft)
                    }
                }
                "UPDATE_OVERLAY_ICON" -> {
                    loadIconToCache()
                    if (!isCollapsed) updateIcon()
                }
                "STOP_OVERLAY_SERVICE" -> stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()

        if (::params.isInitialized) {
            var needUpdate = false

            if (params.x > screenWidth - params.width) {
                params.x = screenWidth - params.width
                needUpdate = true
            }

            if (params.y > screenHeight - params.height) {
                params.y = screenHeight - params.height
                needUpdate = true
            }

            if (isCollapsed) {
                val isLeft = params.x < screenWidth / 2
                collapseView(isLeft)
            } else if (needUpdate) {
                windowManager.updateViewLayout(imageView, params)
            }
        }
    }

    private fun updateScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val realMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            screenWidth = realMetrics.widthPixels
            screenHeight = realMetrics.heightPixels
        }
    }

    private fun loadIconToCache() {
        val customImageFile = File(filesDir, "custom_icon.png")

        cachedBitmap = if (customImageFile.exists()) {
            BitmapFactory.decodeFile(customImageFile.absolutePath)
        } else {
            null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startMyForeground()
        loadIconToCache()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenMetrics()

        val filter = IntentFilter().apply {
            addAction("UPDATE_OVERLAY_CONFIG")
            addAction("UPDATE_OVERLAY_ICON")
            addAction("STOP_OVERLAY_SERVICE")
        }
        ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val prefs = getSharedPreferences("DisbanderPrefs", Context.MODE_PRIVATE)
        expandedSize = prefs.getInt("BUTTON_SIZE", 150)
        arrowAlpha = prefs.getInt("ARROW_ALPHA", 90) / 100f
        arrowWidth = prefs.getInt("ARROW_WIDTH", 60)
        arrowHeight = prefs.getInt("ARROW_HEIGHT", 120)

        imageView = ImageView(this)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.background = null
        updateIcon()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            expandedSize,
            expandedSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } catch (_: Exception) {
            }
        }

        setupTouchListener()
        windowManager.addView(imageView, params)
    }

    private fun startMyForeground() {
        val channelId = "overlay_channel_id"
        val channelName = "Overlay Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sekai Disbander")
            .setContentText("Overlay is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1337, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        imageView.setOnTouchListener(object : View.OnTouchListener {
            private var isDrag = false
            private var startRawX = 0f
            private var startRawY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val rawX = event.rawX
                val rawY = event.rawY

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        smoothX = params.x.toFloat()
                        smoothY = params.y.toFloat()
                        isDrag = false
                        startRawX = rawX
                        startRawY = rawY
                        isImmuneToSuck = false

                        touchOffsetX = rawX - params.x
                        touchOffsetY = rawY - params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (waitingFingerInside) {
                            if (rawX <= 0f || rawX >= screenWidth.toFloat()) {
                                return true
                            }

                            waitingFingerInside = false

                            touchOffsetX = expandedSize / 2f
                            touchOffsetY = expandedSize / 2f

                            smoothX = params.x.toFloat()
                            smoothY = params.y.toFloat()
                            return true
                        }

                        if (justExpanded) {
                            touchOffsetX = expandedSize / 2f
                            touchOffsetY = expandedSize / 2f
                            justExpanded = false
                            return true
                        }

                        if (!isDrag) {
                            if (abs(rawX - startRawX) > moveSlop || abs(rawY - startRawY) > moveSlop) {
                                isDrag = true
                            }
                        }

                        if (isDrag) {
                            if (isCollapsed) {
                                val dx = (rawX - startRawX).toInt()
                                val pullingOut = if (params.x < screenWidth / 2) dx > pullThreshold else dx < -pullThreshold

                                if (pullingOut) {
                                    isCollapsed = false
                                    isImmuneToSuck = true
                                    justExpanded = true
                                    waitingFingerInside = true

                                    updateIcon()
                                    imageView.background = null
                                    imageView.alpha = 1.0f

                                    params.width = expandedSize
                                    params.height = expandedSize

                                    windowManager.updateViewLayout(imageView, params)
                                    return true
                                }
                            }

                            val newX = (rawX - touchOffsetX).toInt()
                            val rawNewY = (rawY - touchOffsetY).toInt()

                            val newY = rawNewY.coerceIn(0, screenHeight - params.height)

                            if (isImmuneToSuck) {
                                val distToRight = screenWidth - (newX + params.width)

                                if (newX > suckThreshold && distToRight > suckThreshold) {
                                    isImmuneToSuck = false
                                }
                            }

                            if (isImmuneToSuck) {
                                val smoothFactor = 0.25f
                                smoothX += (newX - smoothX) * smoothFactor
                                smoothY += (newY - smoothY) * smoothFactor
                            } else {
                                smoothX = newX.toFloat()
                                smoothY = newY.toFloat()
                            }

                            params.x = smoothX.toInt()
                            params.y = smoothY.toInt()

                            if (!isCollapsed && !isImmuneToSuck) {
                                animateSuckEffect(params.x)
                            } else {
                                imageView.alpha = 1.0f
                                imageView.scaleX = 1.0f
                                imageView.scaleY = 1.0f
                            }

                            windowManager.updateViewLayout(imageView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isImmuneToSuck = false

                        if (!isDrag) {
                            if (isCollapsed) expandToEdge() else performDisband()
                        } else {
                            if (!isCollapsed) {
                                checkForCollapse()
                            } else {
                                collapseView(params.x < screenWidth / 2)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun expandToEdge() {
        isCollapsed = false
        updateIcon()
        imageView.background = null
        imageView.alpha = 1.0f
        imageView.scaleX = 1.0f
        imageView.scaleY = 1.0f

        params.width = expandedSize
        params.height = expandedSize

        params.y = params.y.coerceIn(0, screenHeight - expandedSize)

        if (params.x < screenWidth / 2) {
            params.x = suckThreshold + 20
        } else {
            params.x = screenWidth - expandedSize - suckThreshold - 20
        }

        windowManager.updateViewLayout(imageView, params)
    }

    private fun animateSuckEffect(currentX: Int) {
        val distToRight = screenWidth - (currentX + params.width)
        val minDist = min(currentX, distToRight)

        if (minDist < suckThreshold) {
            val progress = minDist.toFloat() / suckThreshold.toFloat()
            imageView.alpha = 0.5f + (0.5f * progress)
            imageView.scaleX = 0.8f + (0.2f * progress)
            imageView.scaleY = 0.8f + (0.2f * progress)
        } else {
            imageView.alpha = 1.0f
            imageView.scaleX = 1.0f
            imageView.scaleY = 1.0f
        }
    }

    private fun checkForCollapse() {
        val distToRight = screenWidth - (params.x + params.width)

        if (params.x < suckThreshold) {
            collapseView(toLeft = true)
        } else if (distToRight < suckThreshold) {
            collapseView(toLeft = false)
        } else {
            imageView.alpha = 1.0f
            imageView.scaleX = 1.0f
            imageView.scaleY = 1.0f

            if (params.x < 0) params.x = 0
            if (params.x + params.width > screenWidth) params.x = screenWidth - params.width
            params.y = params.y.coerceIn(0, screenHeight - params.height)

            windowManager.updateViewLayout(imageView, params)
        }
    }

    private fun collapseView(toLeft: Boolean) {
        isCollapsed = true

        if (toLeft) {
            imageView.setImageResource(R.drawable.ic_arrow_right)
            imageView.setBackgroundResource(R.drawable.bg_handle_left)
        } else {
            imageView.setImageResource(R.drawable.ic_arrow_left)
            imageView.setBackgroundResource(R.drawable.bg_handle_right)
        }

        imageView.setPadding(10, 20, 10, 20)

        imageView.alpha = arrowAlpha
        imageView.scaleX = 1.0f
        imageView.scaleY = 1.0f

        params.width = arrowWidth
        params.height = arrowHeight

        params.x = if (toLeft) 0 else screenWidth - params.width
        params.y = params.y.coerceIn(0, screenHeight - params.height)

        windowManager.updateViewLayout(imageView, params)
    }

    private fun updateSize(newSize: Int) {
        if (::params.isInitialized && ::imageView.isInitialized) {
            params.width = newSize
            params.height = newSize
            params.y = params.y.coerceIn(0, screenHeight - newSize)
            windowManager.updateViewLayout(imageView, params)
        }
    }

    private fun updateIcon() {
        imageView.setPadding(0, 0, 0, 0)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
        } else {
            imageView.setImageResource(R.drawable.default_stamp)
        }
    }

    private fun performDisband() {
        val intent = Intent(this, GhostLagActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        if (::imageView.isInitialized) windowManager.removeView(imageView)
    }
}