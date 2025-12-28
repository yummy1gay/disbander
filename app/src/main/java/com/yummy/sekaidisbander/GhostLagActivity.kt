package com.yummy.sekaidisbander

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity

class GhostLagActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(0, 0)

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, 1)
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}