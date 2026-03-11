package com.sujood.app.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Invisible activity that holds the "show above lock screen" privilege for the overlay.
 * Stays alive until the overlay is dismissed, then finishes via broadcast.
 */
class LockScreenActivity : Activity() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_FINISH           -> finish()
                ACTION_ALLOW_SCREEN_OFF -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val filter = IntentFilter().apply {
            addAction(ACTION_FINISH)
            addAction(ACTION_ALLOW_SCREEN_OFF)
        }
        registerReceiver(receiver, filter,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                RECEIVER_NOT_EXPORTED else 0
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_FINISH           = "com.sujood.app.FINISH_LOCK_ACTIVITY"
        const val ACTION_ALLOW_SCREEN_OFF = "com.sujood.app.ALLOW_SCREEN_OFF"

        fun start(context: Context) {
            context.startActivity(
                Intent(context, LockScreenActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }

        fun finish(context: Context)         = context.sendBroadcast(Intent(ACTION_FINISH))
        fun allowScreenOff(context: Context) = context.sendBroadcast(Intent(ACTION_ALLOW_SCREEN_OFF))
    }
}
