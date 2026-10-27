package com.cordobot.scanvault.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ScanRepository {
    fun observeHistory(): Flow<List<Scan>>
    fun observeScan(id: Long): Flow<Scan?>
    suspend fun latestWithValue(rawValue: String): Scan?
    suspend fun insert(rawValue: String, format: String, scannedAtMillis: Long): Long
    suspend fun toggleFavorite(id: Long)
    suspend fun delete(id: Long)
}

/**
 * Stores a scan, ignoring the same code read again within [DUPLICATE_WINDOW_MS]: the camera
 * sees a code many times per second and the user expects a single history entry.
 */
class SaveScanUseCase @Inject constructor(private val repository: ScanRepository) {

    suspend operator fun invoke(rawValue: String, format: String, now: Long = System.currentTimeMillis()): Long {
        val previous = repository.latestWithValue(rawValue)
        if (previous != null && now - previous.scannedAt.toEpochMilli() < DUPLICATE_WINDOW_MS) return previous.id
        return repository.insert(rawValue, format, now)
    }

    companion object {
        const val DUPLICATE_WINDOW_MS = 10_000L
    }
}

/** History narrowed by text (raw value or parsed title) and, optionally, to favorites. */
class FilterHistoryUseCase @Inject constructor() {
    operator fun invoke(scans: List<Scan>, query: String, favoritesOnly: Boolean): List<Scan> {
        val text = query.trim()
        return scans.filter { scan ->
            (!favoritesOnly || scan.isFavorite) &&
                (text.isEmpty() || scan.rawValue.contains(text, ignoreCase = true) || scan.content.title.contains(text, ignoreCase = true))
        }
    }
}
