package com.androidlearning.sliide_challenge.userfeed.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One [Mutex] per key: callers with the same key run one at a time, callers with different keys never wait for each
 * other. A key's mutex is dropped once nobody holds or waits for it, so the map only holds keys in use.
 */
internal class KeyedMutex<K> {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val guard = Mutex()
    private val entries = mutableMapOf<K, Entry>()

    suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val entry = guard.withLock { entries.getOrPut(key, ::Entry).also { it.users++ } }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            // Must run even if the caller was cancelled, or the entry would never be dropped.
            withContext(NonCancellable) {
                guard.withLock {
                    if (--entry.users == 0) entries.remove(key)
                }
            }
        }
    }
}
