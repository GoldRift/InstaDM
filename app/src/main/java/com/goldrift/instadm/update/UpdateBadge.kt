package com.goldrift.instadm.update

/** Reads the pending update only; a transient install state cannot hide the badge or row. */
fun showUpdateBadge(pending: UpdateState.Available?, seenVersionCode: Long): Boolean =
    pending != null && pending.update.versionCode != seenVersionCode

fun versionToMarkSeen(pending: UpdateState.Available?): Long? = pending?.update?.versionCode
