package com.gothwad.computer.data

/**
  * Lightweight, in-memory LRU tracker of packages launched by the user.
  */
object AppLaunchTracker {
    // Timestamp-ordered list of launched packages. The most recent is at index 0.
    private val lock = Any()
    private val lruList = mutableListOf<String>()

    /**
     * Records an app launch. Moves [pkg] to the top (index 0) of the LRU list.
     */
    fun onAppLaunched(pkg: String) {
        if (pkg.isBlank()) return
        synchronized(lock) {
            lruList.remove(pkg)
            lruList.add(0, pkg)
            // Cap history size to prevent unnecessary memory footprint
            if (lruList.size > 30) {
                lruList.removeAt(lruList.lastIndex)
            }
        }
    }
}
