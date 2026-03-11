package com.sujood.app.service

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Invisible activity whose sole job is to sit above the lock screen so that
 * the TYPE_APPLICATION_OVERLAY window (PrayerLockOverlayService) is visible.
 *
 * On Android 8.1+ TYPE_APPLICATION_OVERLAY cannot show over the keyguard on
 * its own — but an Activity with showWhenLocked/turnScreenOn CAN, and any
 * overlay added while that Activity is in the foreground inherits that privilege.
 *
 * This Activity is fully transparent and has no UI of its own.
 * It finishes itself after a short delay so the overlay remains but the
 * activity stack is clean.
 */
class LockScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tell the window manager this activity can show above the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // No layout — fully transparent, just here to unlock the keyguard
        // Finish immediately so we don't sit in the back stack
        finish()
    }
}
