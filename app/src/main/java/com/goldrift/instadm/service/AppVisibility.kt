package com.goldrift.instadm.service

/**
 * Tracks whether the single activity is visible so the service can avoid
 * posting a notification for a message the user is already looking at.
 */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
