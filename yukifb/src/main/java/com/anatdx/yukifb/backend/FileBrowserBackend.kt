package com.anatdx.yukifb.backend

import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry

/**
 * Minimal read-only backend required by YukiFB.
 *
 * Implementations should move blocking I/O to an appropriate dispatcher. Methods
 * may throw backend-specific exceptions; the controller exposes them as UI errors.
 */
interface FileBrowserBackend {
    /** Returns current information for [entryId]. */
    suspend fun stat(entryId: EntryId): FileEntry

    /** Returns the direct children of [directoryId]. */
    suspend fun list(directoryId: EntryId): List<FileEntry>
}
