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
 *
 * TYPE_APPLICATION_OVERLAY windows cannot appear above the keyguard on their own —
 * but an Activity with showWhenLocked=true CAN, and the overlay inherits that privilege
 * while this activity is in the foreground.
 *
 * This activity is fully transparent and has no UI. It stays alive (but invisible)
 * until it receives the FINISH_LOCK_ACTIVITY broadcast, which PrayerLockOverlayService
 * sends when the overlay is dismissed. This ensures the overlay stays visible on the
 * lock screen for the full duration.
 *
 * Crucially: we do NOT call requestDismissKeyguard — the phone stays locked.
 */
class LockScreenActivity : Activity() {

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show above the lock screen without dismissing it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // NOTE: intentionally NOT calling requestDismissKeyguard — phone stays locked
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on while the overlay is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // No layout — fully transparent background
        // Stay alive until the overlay service tells us to finish
        registerReceiver(finishReceiver, IntentFilter(ACTION_FINISH),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                RECEIVER_NOT_EXPORTED else 0
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(finishReceiver) } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_FINISH = "com.sujood.app.FINISH_LOCK_ACTIVITY"

        fun start(context: Context) {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }

        fun finish(context: Context) {
            context.sendBroadcast(Intent(ACTION_FINISH))
        }
    }
}
